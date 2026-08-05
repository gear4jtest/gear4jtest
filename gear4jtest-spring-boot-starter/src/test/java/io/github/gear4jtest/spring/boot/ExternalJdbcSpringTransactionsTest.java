package io.github.gear4jtest.spring.boot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import io.github.gear4jtest.external.jdbc.repository.ExternalJdbcSchemaMigrator;
import io.github.gear4jtest.external.jdbc.repository.OperationChainConfigRepositoryJdbc;
import io.github.gear4jtest.external.jdbc.repository.OperationChainTagRepositoryJdbc;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalJdbcSpringTransactionsTest {

    @Test
    void explicitSpringBoundary_shouldCommitExternalWritesIndependentlyOfAmbientRollback() throws Exception {
        // Given
        JdbcDataSource targetDataSource = dataSource("managed");
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(targetDataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(targetDataSource);
        jdbcTemplate.execute("CREATE TABLE transaction_probe(id INT PRIMARY KEY)");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(targetDataSource);
        TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(targetDataSource);
        SpringJdbcTransactionOperations transactions = new SpringJdbcTransactionOperations(proxy,
                transactionManager);
        OperationChainConfigRepositoryJdbc configurations = configurations(proxy, transactions);
        OperationChainTagRepositoryJdbc tags = tags(proxy, transactions);
        DatabaseArtifactStore artifacts = artifacts(proxy, transactions);
        AtomicReference<String> artifactHash = new AtomicReference<>();

        // When
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO transaction_probe(id) VALUES (1)");
            configurations.upsert(new OperationChainConfig("spring-managed", false, StoreType.MEMORY, Map.of()));
            configurations.setAllowRunPublicationWithoutTest("spring-managed", true);
            configurations.updateStore("spring-managed", StoreType.DATABASE, Map.of("dialect", "h2"));
            tags.addTag("spring-managed", "kept");
            tags.addTag("spring-managed", "removed");
            tags.removeTag("spring-managed", "removed");
            try {
                artifactHash.set(artifacts.put("spring artifact".getBytes(StandardCharsets.UTF_8)));
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
            status.setRollbackOnly();
        });

        // Then
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction_probe", Integer.class)).isZero();
        OperationChainConfig saved = configurations.findByAssemblyLineId("spring-managed").orElseThrow();
        assertThat(saved.allowRunPublicationWithoutTest()).isTrue();
        assertThat(saved.storeType()).isEqualTo(StoreType.DATABASE);
        assertThat(saved.storeProps()).isEqualTo(Map.of("dialect", "h2"));
        assertThat(tags.listTags("spring-managed")).containsExactly("kept");
        assertThat(artifacts.exists(artifactHash.get())).isTrue();
    }

    @Test
    void defaultBoundary_shouldRejectTransactionAwareAmbientConnectionsBeforeExternalWrites() {
        // Given
        JdbcDataSource targetDataSource = dataSource("default");
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(targetDataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(targetDataSource);
        TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(targetDataSource);
        OperationChainConfigRepositoryJdbc configurations = configurations(proxy, null);
        OperationChainTagRepositoryJdbc tags = tags(proxy, null);
        DatabaseArtifactStore artifacts = artifacts(proxy, null);
        OperationChainConfig rejected = new OperationChainConfig("rejected", false, StoreType.MEMORY, Map.of());

        // When / Then
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThatThrownBy(() -> configurations.upsert(rejected))
                    .isInstanceOf(OperationChainRepositoryException.class)
                    .hasRootCauseMessage(ambientTransactionMessage());
            assertThatThrownBy(() -> tags.addTag("rejected", "tag"))
                    .isInstanceOf(OperationChainRepositoryException.class)
                    .hasRootCauseMessage(ambientTransactionMessage());
            assertThatThrownBy(() -> artifacts.put("rejected".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IOException.class)
                    .hasRootCauseMessage(ambientTransactionMessage());
            status.setRollbackOnly();
        });
        JdbcTemplate jdbcTemplate = new JdbcTemplate(targetDataSource);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_chain_config", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_chain_tag", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artifact_store", Integer.class)).isZero();
    }

    private static String ambientTransactionMessage() {
        return "Autonomous JDBC transactions require a fresh connection with autoCommit=true; the acquired "
                + "connection is already transaction-bound. Configure JdbcTransactionOperations explicitly instead "
                + "of allowing Gear4J to own this transaction.";
    }

    private static OperationChainConfigRepositoryJdbc configurations(TransactionAwareDataSourceProxy dataSource,
                                                                     SpringJdbcTransactionOperations transactions) {
        OperationChainConfigRepositoryJdbc.Builder builder = OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2);
        if (transactions != null) {
            builder.transactionOperations(transactions);
        }
        return builder.build();
    }

    private static OperationChainTagRepositoryJdbc tags(TransactionAwareDataSourceProxy dataSource,
                                                        SpringJdbcTransactionOperations transactions) {
        OperationChainTagRepositoryJdbc.Builder builder = OperationChainTagRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2);
        if (transactions != null) {
            builder.transactionOperations(transactions);
        }
        return builder.build();
    }

    private static DatabaseArtifactStore artifacts(TransactionAwareDataSourceProxy dataSource,
                                                   SpringJdbcTransactionOperations transactions) {
        DatabaseArtifactStore.Builder builder = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2);
        if (transactions != null) {
            builder.transactionOperations(transactions);
        }
        return builder.build();
    }

    private static JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:external-spring-" + name + "-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}
