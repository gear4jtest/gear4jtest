package io.github.gear4jtest.external.jdbc.repository;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class ExternalJdbcTransactionBoundariesIT {

    @Test
    void defaultBoundaries_shouldCommitEveryExternalMutation() throws Exception {
        // Given
        DataSource dataSource = dataSource();
        migrate(dataSource);
        OperationChainConfigRepositoryJdbc configurations = configurations(dataSource, null);
        OperationChainTagRepositoryJdbc tags = tags(dataSource, null);
        DatabaseArtifactStore artifacts = artifacts(dataSource, null);
        OperationChainConfig initial = config("committed", false, StoreType.MEMORY, Map.of());

        // When
        configurations.upsert(initial);
        configurations.setAllowRunPublicationWithoutTest("committed", true);
        configurations.updateStore("committed", StoreType.DATABASE, Map.of("dialect", "h2"));
        tags.addTag("committed", "kept");
        tags.addTag("committed", "removed");
        tags.removeTag("committed", "removed");
        String hash = artifacts.put("committed artifact".getBytes(StandardCharsets.UTF_8));

        // Then
        OperationChainConfig saved = configurations.findByAssemblyLineId("committed").orElseThrow();
        assertThat(saved.allowRunPublicationWithoutTest()).isTrue();
        assertThat(saved.storeType()).isEqualTo(StoreType.DATABASE);
        assertThat(saved.storeProps()).isEqualTo(Map.of("dialect", "h2"));
        assertThat(tags.listTags("committed")).containsExactly("kept");
        assertThat(artifacts.exists(hash)).isTrue();
    }

    @Test
    void defaultBoundaries_shouldRejectAutoCommitFalseBeforeEveryMutation() throws Exception {
        // Given
        DataSource targetDataSource = dataSource();
        migrate(targetDataSource);
        DataSource transactionBoundDataSource = new AutoCommitFalseDataSource(targetDataSource);
        OperationChainConfigRepositoryJdbc configurations = configurations(transactionBoundDataSource, null);
        OperationChainTagRepositoryJdbc tags = tags(transactionBoundDataSource, null);
        DatabaseArtifactStore artifacts = artifacts(transactionBoundDataSource, null);
        OperationChainConfig rejected = config("rejected", false, StoreType.MEMORY, Map.of());

        // When / Then
        assertRepositoryRejectsAmbientTransaction(() -> configurations.upsert(rejected));
        assertRepositoryRejectsAmbientTransaction(
                                                  () -> configurations
                                                          .setAllowRunPublicationWithoutTest("rejected", true));
        assertRepositoryRejectsAmbientTransaction(() -> configurations.updateStore(
                                                                                   "rejected", StoreType.DATABASE,
                                                                                   Map.of("dialect", "h2")));
        assertRepositoryRejectsAmbientTransaction(() -> tags.addTag("rejected", "new"));
        assertRepositoryRejectsAmbientTransaction(() -> tags.removeTag("rejected", "new"));
        assertThatThrownBy(() -> artifacts.put("rejected artifact".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class)
                .hasRootCauseMessage(ambientTransactionMessage());
        assertThat(count(targetDataSource, "operation_chain_config")).isZero();
        assertThat(count(targetDataSource, "operation_chain_tag")).isZero();
        assertThat(count(targetDataSource, "artifact_store")).isZero();
    }

    @Test
    void explicitManagedBoundary_shouldDelegateCommitAndRollbackToItsOwner() throws Exception {
        // Given
        DataSource dataSource = dataSource();
        migrate(dataSource);
        OperationChainConfigRepositoryJdbc autonomousConfigurations = configurations(dataSource, null);
        OperationChainTagRepositoryJdbc autonomousTags = tags(dataSource, null);
        autonomousConfigurations.upsert(config("baseline", false, StoreType.MEMORY, Map.of()));
        autonomousTags.addTag("baseline", "must-remain");
        byte[] content = "rolled back artifact".getBytes(StandardCharsets.UTF_8);
        String expectedHash = ArtifactHashes.sha256Hex(content);

        // When
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            JdbcTransactionOperations managedTransactions = work -> work.execute(connection);
            OperationChainConfigRepositoryJdbc configurations = configurations(dataSource, managedTransactions);
            OperationChainTagRepositoryJdbc tags = tags(dataSource, managedTransactions);
            DatabaseArtifactStore artifacts = artifacts(dataSource, managedTransactions);

            configurations.upsert(config("rolled-back", false, StoreType.MEMORY, Map.of()));
            configurations.setAllowRunPublicationWithoutTest("baseline", true);
            configurations.updateStore("baseline", StoreType.DATABASE, Map.of("ambient", "true"));
            tags.addTag("rolled-back", "new-tag");
            tags.removeTag("baseline", "must-remain");
            assertThat(artifacts.put(content)).isEqualTo(expectedHash);
            assertThat(count(connection, "operation_chain_config")).isEqualTo(2);
            assertThat(count(connection, "operation_chain_tag")).isEqualTo(1);
            assertThat(count(connection, "artifact_store")).isEqualTo(1);

            connection.rollback();
        }

        // Then
        OperationChainConfig baseline = autonomousConfigurations.findByAssemblyLineId("baseline").orElseThrow();
        assertThat(baseline.allowRunPublicationWithoutTest()).isFalse();
        assertThat(baseline.storeType()).isEqualTo(StoreType.MEMORY);
        assertThat(baseline.storeProps()).isEmpty();
        assertThat(autonomousConfigurations.findByAssemblyLineId("rolled-back")).isEmpty();
        assertThat(autonomousTags.listTags("baseline")).containsExactly("must-remain");
        assertThat(autonomousTags.listTags("rolled-back")).isEmpty();
        assertThat(artifacts(dataSource, null).exists(expectedHash)).isFalse();
    }

    private static void assertRepositoryRejectsAmbientTransaction(RepositoryMutation mutation) {
        assertThatThrownBy(mutation::execute)
                .isInstanceOf(OperationChainRepositoryException.class)
                .hasRootCauseMessage(ambientTransactionMessage());
    }

    private static String ambientTransactionMessage() {
        return "Autonomous JDBC transactions require a fresh connection with autoCommit=true; the acquired "
                + "connection is already transaction-bound. Configure JdbcTransactionOperations explicitly instead "
                + "of allowing Gear4J to own this transaction.";
    }

    private static OperationChainConfig config(String assemblyLineId,
                                               boolean allowRunWithoutTest,
                                               StoreType storeType,
                                               Map<String, String> storeProperties) {
        return new OperationChainConfig(assemblyLineId, allowRunWithoutTest, storeType, storeProperties);
    }

    private static OperationChainConfigRepositoryJdbc configurations(DataSource dataSource,
                                                                     JdbcTransactionOperations transactions) {
        OperationChainConfigRepositoryJdbc.Builder builder = OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2);
        if (transactions != null) {
            builder.transactionOperations(transactions);
        }
        return builder.build();
    }

    private static OperationChainTagRepositoryJdbc tags(DataSource dataSource,
                                                        JdbcTransactionOperations transactions) {
        OperationChainTagRepositoryJdbc.Builder builder = OperationChainTagRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2);
        if (transactions != null) {
            builder.transactionOperations(transactions);
        }
        return builder.build();
    }

    private static DatabaseArtifactStore artifacts(DataSource dataSource, JdbcTransactionOperations transactions) {
        DatabaseArtifactStore.Builder builder = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2);
        if (transactions != null) {
            builder.transactionOperations(transactions);
        }
        return builder.build();
    }

    private static void migrate(DataSource dataSource) {
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
    }

    private static int count(DataSource dataSource, String table) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return count(connection, table);
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:external-transactions-"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }

    @FunctionalInterface
    private interface RepositoryMutation {
        void execute();
    }

    private record AutoCommitFalseDataSource(DataSource delegate) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = delegate.getConnection();
            connection.setAutoCommit(false);
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection connection = delegate.getConnection(username, password);
            connection.setAutoCommit(false);
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
