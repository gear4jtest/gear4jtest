package io.github.gear4jtest.external.jdbc.repository;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
            assertThat(indexColumns(connection, "OPERATION_CHAIN_OBJECT", "IDX_OP_CHAIN_LATEST_RUN"))
                    .containsExactly("AL_ID", "PUBLICATION_MODE", "PUBLISHED_AT", "ID");
            assertThat(appliedMigrationVersions(connection))
                    .containsExactly("1");
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

    private static List<String> indexColumns(Connection connection, String tableName, String indexName)
            throws Exception {
        Map<Short, String> columnsByPosition = new TreeMap<>();
        try (ResultSet rs = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))
                        && rs.getString("COLUMN_NAME") != null) {
                    columnsByPosition.put(rs.getShort("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"));
                }
            }
        }
        return List.copyOf(columnsByPosition.values());
    }

    private static List<String> appliedMigrationVersions(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement(
                                                         "SELECT version FROM gear4j_schema_history "
                                                                 + "WHERE module_id=? ORDER BY installed_at, version")) {
            statement.setString(1, "gear4j-external-api");
            try (ResultSet rs = statement.executeQuery()) {
                var versions = new java.util.ArrayList<String>();
                while (rs.next()) {
                    versions.add(rs.getString(1));
                }
                return List.copyOf(versions);
            }
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
