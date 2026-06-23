package io.github.gear4jtest.external.api.repository.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalJdbcSchemaMigratorIT {
    @Test
    void migrate_withDataSource_shouldCreateExternalSchemaAndHistory() throws Exception {
        DataSource dataSource = dataSource("ds");

        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "ARTIFACT_STORE")).isTrue();
            assertThat(tableExists(connection, "OPERATION_CHAIN_CONFIG")).isTrue();
            assertThat(tableExists(connection, "OPERATION_CHAIN_OBJECT")).isTrue();
            assertThat(tableExists(connection, "OPERATION_CHAIN_TAG")).isTrue();
            assertThat(tableExists(connection, "GEAR4J_SCHEMA_HISTORY")).isTrue();
        }
    }

    @Test
    void migrate_withConnection_shouldCreateExternalSchema() throws Exception {
        DataSource dataSource = dataSource("connection");

        try (Connection connection = dataSource.getConnection()) {
            ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(connection);

            assertThat(tableExists(connection, "OPERATION_CHAIN_CONFIG")).isTrue();
        }
    }

    private static DataSource dataSource(String name) {
        return new DriverManagerDataSource("jdbc:h2:mem:external_migrator_" + name + ";DB_CLOSE_DELAY=-1");
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private static final class DriverManagerDataSource implements DataSource {
        private final String url;
        private PrintWriter logWriter;
        private int loginTimeout;

        private DriverManagerDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter logWriter) {
            this.logWriter = logWriter;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Unsupported unwrap type: " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
