package io.github.gear4jtest.spring.boot;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring-managed autonomous transaction boundary for Gear4J JDBC repository
 * writes.
 * <p>
 * Every invocation uses {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}.
 * An ambient application transaction is suspended and cannot be committed or
 * rolled back by Gear4J.
 */
public final class SpringJdbcTransactionOperations implements JdbcTransactionOperations {
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    public SpringJdbcTransactionOperations(DataSourceTransactionManager transactionManager) {
        this(requireTransactionManagerDataSource(transactionManager), transactionManager);
    }

    /**
     * Creates the Spring boundary and verifies that the repository and transaction
     * manager use the same target datasource.
     */
    public SpringJdbcTransactionOperations(DataSource repositoryDataSource,
                                           DataSourceTransactionManager transactionManager) {
        DataSourceTransactionManager requiredTransactionManager = Objects.requireNonNull(
                                                                                         transactionManager,
                                                                                         "transactionManager must not be null");
        DataSource requiredRepositoryDataSource = unwrapTransactionAware(Objects.requireNonNull(
                                                                                                repositoryDataSource,
                                                                                                "repositoryDataSource must not be null"));
        DataSource transactionManagerDataSource = unwrapTransactionAware(Objects.requireNonNull(
                                                                                                requiredTransactionManager
                                                                                                        .getDataSource(),
                                                                                                "transactionManager dataSource must not be null"));
        if (requiredRepositoryDataSource != transactionManagerDataSource) {
            throw new IllegalArgumentException("repositoryDataSource must match the DataSourceTransactionManager "
                    + "target dataSource");
        }
        this.dataSource = transactionManagerDataSource;
        this.transactionTemplate = new TransactionTemplate(requiredTransactionManager);
        this.transactionTemplate.setName("Gear4J JDBC persistence");
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static DataSource requireTransactionManagerDataSource(DataSourceTransactionManager transactionManager) {
        DataSourceTransactionManager requiredTransactionManager = Objects.requireNonNull(
                                                                                         transactionManager,
                                                                                         "transactionManager must not be null");
        return Objects.requireNonNull(requiredTransactionManager.getDataSource(),
                                      "transactionManager dataSource must not be null");
    }

    private static DataSource unwrapTransactionAware(DataSource dataSource) {
        DataSource current = dataSource;
        while (current instanceof TransactionAwareDataSourceProxy transactionAware) {
            current = Objects.requireNonNull(transactionAware.getTargetDataSource(),
                                             "TransactionAwareDataSourceProxy target dataSource must not be null");
        }
        return current;
    }

    @Override
    public void execute(Work work) throws SQLException {
        Objects.requireNonNull(work, "work must not be null");
        try {
            transactionTemplate.execute(status -> {
                executeWork(work);
                return null;
            });
        } catch (SqlCallbackException exception) {
            throw exception.sqlException();
        }
    }

    private void executeWork(Work work) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            work.execute(connection);
        } catch (SQLException exception) {
            throw new SqlCallbackException(exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static final class SqlCallbackException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private SqlCallbackException(SQLException cause) {
            super(cause);
        }

        private SQLException sqlException() {
            return (SQLException) getCause();
        }
    }
}
