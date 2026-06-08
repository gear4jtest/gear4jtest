package io.github.gear4jtest.core.persistence.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small, dependency-free schema migrator used by Gear4J JDBC components.
 *
 * <p>
 * It is intentionally not a Flyway replacement. Its purpose is to let Gear4J
 * own and version its internal schema without relying on table-existence checks
 * or ambiguous JDBC dialect detection. Applications that already use Flyway can
 * still disable auto-creation and run the same SQL resources from their own
 * migration process.
 * </p>
 */
public final class JdbcSchemaMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcSchemaMigrator.class);
    private static final String HISTORY_TABLE = "gear4j_schema_history";

    private final String moduleId;
    private final Gear4jDatabaseDialect dialect;
    private final String migrationListResource;
    private final String baselineTableName;
    private final ClassLoader classLoader;

    public JdbcSchemaMigrator(String moduleId,
                              Gear4jDatabaseDialect dialect,
                              String migrationListResource,
                              String baselineTableName) {
        this(moduleId, dialect, migrationListResource, baselineTableName,
                Thread.currentThread().getContextClassLoader());
    }

    public JdbcSchemaMigrator(String moduleId,
                              Gear4jDatabaseDialect dialect,
                              String migrationListResource,
                              String baselineTableName,
                              ClassLoader classLoader) {
        this.moduleId = requireNonBlank(moduleId, "moduleId");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.migrationListResource = normalizeResource(requireNonBlank(migrationListResource,
                                                                       "migrationListResource"));
        this.baselineTableName = requireNonBlank(baselineTableName, "baselineTableName");
        this.classLoader = classLoader != null ? classLoader : JdbcSchemaMigrator.class.getClassLoader();
    }

    public static JdbcSchemaMigrator core(Gear4jDatabaseDialect dialect) {
        return new JdbcSchemaMigrator("gear4j-core", dialect,
                "io/github/gear4j/db/" + dialect.resourceDirectory() + "/migrations/migrations.list",
                "assembly_run");
    }

    public void migrate(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        try (Connection connection = dataSource.getConnection()) {
            migrate(connection);
        } catch (SQLException e) {
            throw new SchemaMigrationException("Failed to migrate Gear4J schema for module " + moduleId, e);
        }
    }

    public void migrate(Connection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        try {
            ensureHistoryTable(connection);
            List<SchemaMigration> migrations = loadMigrations();
            if (migrations.isEmpty()) {
                return;
            }
            if (hasNoHistory(connection) && tableExists(connection, baselineTableName)) {
                baselineExistingSchema(connection, migrations.get(0));
                migrations = migrations.subList(1, migrations.size());
            }
            for (SchemaMigration migration : migrations) {
                applyIfNeeded(connection, migration);
            }
        } catch (SQLException | IOException e) {
            throw new SchemaMigrationException("Failed to migrate Gear4J schema for module " + moduleId, e);
        }
    }

    private void ensureHistoryTable(Connection connection) throws SQLException {
        if (tableExists(connection, HISTORY_TABLE)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(historyTableSql());
        }
    }

    private String historyTableSql() {
        return switch (dialect) {
            case POSTGRESQL -> "CREATE TABLE gear4j_schema_history ("
                    + "module_id VARCHAR(100) NOT NULL, "
                    + "version VARCHAR(40) NOT NULL, "
                    + "description VARCHAR(300) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "installed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), "
                    + "PRIMARY KEY (module_id, version))";
            case MYSQL, MARIADB -> "CREATE TABLE gear4j_schema_history ("
                    + "module_id VARCHAR(100) NOT NULL, "
                    + "version VARCHAR(40) NOT NULL, "
                    + "description VARCHAR(300) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (module_id, version))";
            case ORACLE -> "CREATE TABLE gear4j_schema_history ("
                    + "module_id VARCHAR2(100) NOT NULL, "
                    + "version VARCHAR2(40) NOT NULL, "
                    + "description VARCHAR2(300) NOT NULL, "
                    + "checksum VARCHAR2(64) NOT NULL, "
                    + "installed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                    + "CONSTRAINT pk_gear4j_schema_history PRIMARY KEY (module_id, version))";
            case H2 -> "CREATE TABLE IF NOT EXISTS gear4j_schema_history ("
                    + "module_id VARCHAR(100) NOT NULL, "
                    + "version VARCHAR(40) NOT NULL, "
                    + "description VARCHAR(300) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (module_id, version))";
        };
    }

    private boolean hasNoHistory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                                                                       "SELECT 1 FROM gear4j_schema_history WHERE module_id=?")) {
            statement.setString(1, moduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return !resultSet.next();
            }
        }
    }

    private void baselineExistingSchema(Connection connection, SchemaMigration baseline)
            throws SQLException, IOException {
        String content = readResource(baseline.resourcePath());
        String checksum = sha256(content);
        LOGGER.info("[Gear4J] Baselining existing schema for module {} at migration {}", moduleId,
                    baseline.version());
        insertHistory(connection, baseline, checksum);
    }

    private void applyIfNeeded(Connection connection, SchemaMigration migration) throws SQLException, IOException {
        String content = readResource(migration.resourcePath());
        String checksum = sha256(content);
        MigrationState state = findMigrationState(connection, migration.version());
        if (state != null) {
            if (!checksum.equals(state.checksum())) {
                throw new SchemaMigrationException("Checksum mismatch for Gear4J migration " + moduleId + ":"
                        + migration.version());
            }
            return;
        }
        LOGGER.info("[Gear4J] Applying schema migration {}:{} ({})", moduleId, migration.version(),
                    migration.description());
        executeScript(connection, content);
        insertHistory(connection, migration, checksum);
    }

    private MigrationState findMigrationState(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                                                                       "SELECT checksum FROM gear4j_schema_history WHERE module_id=? AND version=?")) {
            statement.setString(1, moduleId);
            statement.setString(2, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? new MigrationState(resultSet.getString(1)) : null;
            }
        }
    }

    private void insertHistory(Connection connection, SchemaMigration migration, String checksum) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                                                                       "INSERT INTO gear4j_schema_history(module_id, version, description, checksum, installed_at) "
                                                                               + "VALUES (?,?,?,?,?)")) {
            statement.setString(1, moduleId);
            statement.setString(2, migration.version());
            statement.setString(3, migration.description());
            statement.setString(4, checksum);
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private List<SchemaMigration> loadMigrations() throws IOException {
        String listContent = readResource(migrationListResource);
        List<SchemaMigration> migrations = new ArrayList<>();
        String basePath = migrationListResource.substring(0, migrationListResource.lastIndexOf('/') + 1);
        for (String raw : listContent.split("\\R")) {
            String entry = raw.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String fileName = entry.substring(entry.lastIndexOf('/') + 1);
            int separator = fileName.indexOf("__");
            int extension = fileName.lastIndexOf('.');
            if (!fileName.startsWith("V") || separator < 0 || extension <= separator) {
                throw new IOException("Invalid Gear4J migration file name: " + entry);
            }
            String version = fileName.substring(1, separator);
            String description = fileName.substring(separator + 2, extension).replace('_', ' ');
            migrations.add(new SchemaMigration(version, description, basePath + entry));
        }
        return migrations;
    }

    private String readResource(String resourcePath) throws IOException {
        String normalized = normalizeResource(resourcePath);
        InputStream stream = classLoader.getResourceAsStream(normalized);
        if (stream == null) {
            stream = JdbcSchemaMigrator.class.getClassLoader().getResourceAsStream(normalized);
        }
        if (stream == null) {
            throw new IOException("Migration resource not found: " + normalized);
        }
        try (InputStream input = stream;
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    private void executeScript(Connection connection, String scriptContent) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : splitSqlStatements(scriptContent)) {
                statement.execute(sql);
            }
        }
    }

    static List<String> splitSqlStatements(String scriptContent) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        String dollarQuote = null;

        for (int i = 0; i < scriptContent.length(); i++) {
            char ch = scriptContent.charAt(i);
            char next = i + 1 < scriptContent.length() ? scriptContent.charAt(i + 1) : '\0';

            if (lineComment) {
                if (ch == '\n' || ch == '\r') {
                    lineComment = false;
                    current.append(ch);
                }
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    blockComment = false;
                    current.append(' ');
                    i++;
                }
                continue;
            }
            if (dollarQuote != null) {
                if (scriptContent.startsWith(dollarQuote, i)) {
                    current.append(dollarQuote);
                    i += dollarQuote.length() - 1;
                    dollarQuote = null;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (singleQuoted) {
                current.append(ch);
                if (ch == '\'' && next == '\'') {
                    current.append(next);
                    i++;
                } else if (ch == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                current.append(ch);
                if (ch == '"' && next == '"') {
                    current.append(next);
                    i++;
                } else if (ch == '"') {
                    doubleQuoted = false;
                }
                continue;
            }

            if (ch == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (ch == '\'') {
                singleQuoted = true;
                current.append(ch);
                continue;
            }
            if (ch == '"') {
                doubleQuoted = true;
                current.append(ch);
                continue;
            }
            if (ch == '$') {
                String tag = readDollarQuoteTag(scriptContent, i);
                if (tag != null) {
                    dollarQuote = tag;
                    current.append(tag);
                    i += tag.length() - 1;
                    continue;
                }
            }
            if (ch == ';') {
                addStatement(statements, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
    }

    private static String readDollarQuoteTag(String scriptContent, int start) {
        int end = scriptContent.indexOf('$', start + 1);
        if (end < 0) {
            return null;
        }
        for (int i = start + 1; i < end; i++) {
            char ch = scriptContent.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return null;
            }
        }
        return scriptContent.substring(start, end + 1);
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return tableExists(metadata, tableName) || tableExists(metadata, tableName.toLowerCase())
                || tableExists(metadata, tableName.toUpperCase());
    }

    private boolean tableExists(DatabaseMetaData metadata, String tableName) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(null, null, tableName, null)) {
            return resultSet.next();
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalizeResource(String resource) {
        return resource.startsWith("/") ? resource.substring(1) : resource;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record MigrationState(String checksum) {}
}
