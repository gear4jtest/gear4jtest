package io.github.gear4jtest.spring.boot;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringJdbcTransactionOperationsTest {

    @Test
    void shouldCommitWhenNoAmbientTransactionExists() throws Exception {
        // Given
        JdbcDataSource dataSource = dataSource("without-ambient");
        JdbcTemplate jdbcTemplate = initialize(dataSource);
        SpringJdbcTransactionOperations transactions = new SpringJdbcTransactionOperations(
                new DataSourceTransactionManager(dataSource));

        // When
        transactions.execute(connection -> {
            insert(connection, 1, "independent");
        });

        // Then
        assertThat(messages(jdbcTemplate)).containsExactly("independent");
    }

    @Test
    void shouldSuspendAmbientTransactionAndCommitIndependentWork() {
        // Given
        JdbcDataSource targetDataSource = dataSource("with-ambient");
        JdbcTemplate jdbcTemplate = initialize(targetDataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(targetDataSource);
        TransactionTemplate ambientTransaction = new TransactionTemplate(transactionManager);
        TransactionAwareDataSourceProxy transactionAwareDataSource = new TransactionAwareDataSourceProxy(
                targetDataSource);
        SpringJdbcTransactionOperations transactions = new SpringJdbcTransactionOperations(
                transactionAwareDataSource, transactionManager);

        // When
        ambientTransaction.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO transaction_probe(id, message) VALUES (?, ?)", 1, "ambient");
            try {
                transactions.execute(connection -> {
                    insert(connection, 2, "gear4j");
                });
            } catch (SQLException exception) {
                throw new AssertionError(exception);
            }
            status.setRollbackOnly();
        });

        // Then
        assertThat(messages(jdbcTemplate)).containsExactly("gear4j");
    }

    @Test
    void defaultAutonomousModeShouldRejectTransactionAwareConnectionInsideAmbientTransaction() {
        // Given
        JdbcDataSource targetDataSource = dataSource("transaction-aware");
        JdbcTemplate jdbcTemplate = initialize(targetDataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(targetDataSource);
        TransactionTemplate ambientTransaction = new TransactionTemplate(transactionManager);
        TransactionAwareDataSourceProxy transactionAwareDataSource = new TransactionAwareDataSourceProxy(
                targetDataSource);
        JdbcTransactionOperations transactions = JdbcTransactionOperations.autonomous(transactionAwareDataSource);

        // When / Then
        ambientTransaction.executeWithoutResult(status -> {
            assertThatThrownBy(() -> transactions.execute(connection -> {
            }))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("already transaction-bound");
            status.setRollbackOnly();
        });
        assertThat(messages(jdbcTemplate)).isEmpty();
    }

    @Test
    void shouldRollbackFailedIndependentWorkAndPreserveSqlException() {
        // Given
        JdbcDataSource dataSource = dataSource("rollback");
        JdbcTemplate jdbcTemplate = initialize(dataSource);
        SpringJdbcTransactionOperations transactions = new SpringJdbcTransactionOperations(
                new DataSourceTransactionManager(dataSource));
        SQLException failure = new SQLException("write failed");

        // When / Then
        assertThatThrownBy(() -> transactions.execute(connection -> {
            insert(connection, 1, "must-roll-back");
            throw failure;
        })).isSameAs(failure);
        assertThat(messages(jdbcTemplate)).isEmpty();
    }

    @Test
    void shouldRejectTransactionManagerForDifferentDataSource() {
        // Given
        JdbcDataSource repositoryDataSource = dataSource("repository");
        JdbcDataSource transactionDataSource = dataSource("transaction-manager");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(transactionDataSource);

        // When / Then
        assertThatThrownBy(() -> new SpringJdbcTransactionOperations(repositoryDataSource, transactionManager))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
    }

    private static JdbcDataSource dataSource(String databaseName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:gear4j-" + databaseName + "-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }

    private static JdbcTemplate initialize(JdbcDataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE transaction_probe(id INT PRIMARY KEY, message VARCHAR(64) NOT NULL)");
        return jdbcTemplate;
    }

    private static void insert(java.sql.Connection connection, int id, String message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                                                                       "INSERT INTO transaction_probe(id, message) VALUES (?, ?)")) {
            statement.setInt(1, id);
            statement.setString(2, message);
            statement.executeUpdate();
        }
    }

    private static List<String> messages(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList("SELECT message FROM transaction_probe ORDER BY id", String.class);
    }
}
