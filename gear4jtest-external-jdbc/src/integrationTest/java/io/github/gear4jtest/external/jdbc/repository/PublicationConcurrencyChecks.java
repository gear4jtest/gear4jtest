package io.github.gear4jtest.external.jdbc.repository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the same real-connection interleavings on H2 and the release dialects.
 */
final class PublicationConcurrencyChecks {
    private PublicationConcurrencyChecks() {
    }

    static void verify(DataSource dataSource, Gear4jDatabaseDialect dialect) throws Exception {
        commitAfterRenewal(dataSource, dialect);
        for (Contender contender : Contender.values()) {
            contenderAfterCommit(dataSource, dialect, contender);
        }
    }

    private static void commitAfterRenewal(DataSource dataSource, Gear4jDatabaseDialect dialect) throws Exception {
        // Given: renewal has modified the stage but its transaction has not committed.
        OperationChainObject object = publication(dataSource, dialect);
        OperationChainObjectRepositoryJdbc repository = repository(dataSource, dialect,
                                                                   JdbcTransactionOperations.autonomous(dataSource));
        var initial = repository.stage(object, List.of("initial"));
        CountDownLatch commitReadsStage = new CountDownLatch(1);
        JdbcTransactionOperations observed = observedTransactions(dataSource, sql -> {
            if (sql.startsWith("SELECT stage_id,")) {
                commitReadsStage.countDown();
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection renewal = dataSource.getConnection()) {
            renewal.setAutoCommit(false);
            try {
                repository(dataSource, dialect, work -> work.execute(renewal)).stage(object, List.of("renewed"));
                Future<?> commit = executor.submit(() -> repository(dataSource, dialect, observed)
                        .commit(initial.stageId()));
                assertThat(commitReadsStage.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> commit.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                // When
                renewal.commit();
                commit.get(5, TimeUnit.SECONDS);

                // Then: commit must read the renewed tags, not an earlier snapshot.
                assertThat(committedTags(dataSource, object.alId())).containsExactly("initial", "renewed");
            } finally {
                renewal.rollback();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void contenderAfterCommit(DataSource dataSource,
                                             Gear4jDatabaseDialect dialect,
                                             Contender contender)
            throws Exception {
        // Given: commit has read the stage and is paused before publishing the object.
        OperationChainObject object = publication(dataSource, dialect);
        OperationChainObjectRepositoryJdbc repository = repository(dataSource, dialect,
                                                                   JdbcTransactionOperations.autonomous(dataSource));
        var initial = repository.stage(object, List.of("initial"));
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch finishCommit = new CountDownLatch(1);
        CountDownLatch contenderExecuting = new CountDownLatch(1);
        JdbcTransactionOperations pausedCommit = observedTransactions(dataSource, sql -> {
            if (sql.startsWith("INSERT INTO operation_chain_object(")) {
                publishing.countDown();
                await(finishCommit);
            }
        });
        JdbcTransactionOperations observedContender = observedTransactions(dataSource, sql -> {
            if (sql.startsWith("INSERT INTO operation_chain_publication_stage(")
                    || sql.startsWith("DELETE FROM operation_chain_publication_stage ")
                    || sql.startsWith("SELECT stage_id,")) {
                contenderExecuting.countDown();
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> commit = executor.submit(() -> repository(dataSource, dialect, pausedCommit)
                    .commit(initial.stageId()));
            assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> competing = executor.submit(() -> {
                OperationChainObjectRepositoryJdbc other = repository(dataSource, dialect, observedContender);
                switch (contender) {
                    case RENEW -> other.stage(object, List.of("renewed"));
                    case ABORT -> other.abort(initial.stageId());
                    case COMMIT -> other.commit(initial.stageId());
                }
            });
            assertThat(contenderExecuting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> competing.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            // When
            finishCommit.countDown();
            commit.get(5, TimeUnit.SECONDS);
            competing.get(5, TimeUnit.SECONDS);
            // A renewal ordered after commit creates a new stage; its accepted tags
            // survive.
            repository.commit(initial.stageId());

            // Then
            assertThat(repository.find(object.alId(), object.version(), object.mode())).isPresent();
            assertThat(committedTags(dataSource, object.alId()))
                    .containsExactlyElementsOf(contender == Contender.RENEW
                            ? List.of("initial", "renewed") : List.of("initial"));
            repository.commit(initial.stageId());
            repository.abort(initial.stageId());
            assertThat(repository.abortIfUnchanged(initial)).isFalse();
        } finally {
            finishCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static OperationChainObject publication(DataSource dataSource, Gear4jDatabaseDialect dialect) {
        String alId = "concurrent-" + UUID.randomUUID();
        OperationChainConfigRepositoryJdbc.builder().dataSource(dataSource).databaseDialect(dialect).build()
                .upsert(new OperationChainConfig(alId, false, StoreType.MEMORY, Map.of()));
        Instant now = Instant.now();
        return new OperationChainObject(null, alId, "1.0.0", ExecutionMode.TEST, "a".repeat(64), 10,
                "application/xml", now, "test", now);
    }

    private static OperationChainObjectRepositoryJdbc repository(DataSource dataSource,
                                                                 Gear4jDatabaseDialect dialect,
                                                                 JdbcTransactionOperations transactions) {
        return OperationChainObjectRepositoryJdbc.builder().dataSource(dataSource).databaseDialect(dialect)
                .jdbcStatementTimeout(Duration.ofSeconds(5)).transactionOperations(transactions).build();
    }

    private static List<String> committedTags(DataSource dataSource, String alId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                                                                          "SELECT tag FROM operation_chain_tag WHERE al_id=? ORDER BY tag")) {
            statement.setString(1, alId);
            try (var rows = statement.executeQuery()) {
                List<String> tags = new ArrayList<>();
                while (rows.next()) {
                    tags.add(rows.getString(1));
                }
                return tags;
            }
        }
    }

    private static JdbcTransactionOperations observedTransactions(DataSource dataSource, SqlHook hook) {
        return work -> JdbcTransactionOperations.autonomous(dataSource).execute(connection -> {
            Connection observed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                                                      new Class<?>[] { Connection.class },
                                                                      (proxy, method, arguments) -> {
                                                                          Object result = invoke(connection, method,
                                                                                                 arguments);
                                                                          if (method.getName()
                                                                                  .equals("prepareStatement")
                                                                                  && result instanceof PreparedStatement statement) {
                                                                              String sql = (String) arguments[0];
                                                                              return Proxy
                                                                                      .newProxyInstance(PreparedStatement.class
                                                                                              .getClassLoader(),
                                                                                                        new Class<?>[] {
                                                                                                                PreparedStatement.class },
                                                                                                        (ignored,
                                                                                                         operation,
                                                                                                         parameters) -> {
                                                                                                            if (operation
                                                                                                                    .getName()
                                                                                                                    .startsWith("execute")) {
                                                                                                                hook.beforeExecute(sql);
                                                                                                            }
                                                                                                            return invoke(statement,
                                                                                                                          operation,
                                                                                                                          parameters);
                                                                                                        });
                                                                          }
                                                                          return result;
                                                                      });
            work.execute(observed);
        });
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static void await(CountDownLatch latch) throws SQLException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new SQLException("Timed out coordinating publication transactions");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted coordinating publication transactions", exception);
        }
    }

    private enum Contender {
        RENEW, ABORT, COMMIT
    }

    @FunctionalInterface
    private interface SqlHook {
        void beforeExecute(String sql) throws SQLException;
    }
}
