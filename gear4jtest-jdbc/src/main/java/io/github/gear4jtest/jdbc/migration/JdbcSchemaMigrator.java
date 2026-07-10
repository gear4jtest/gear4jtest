package io.github.gear4jtest.jdbc.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
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
    private static final String ASSEMBLY_RUN_TABLE = "assembly_run";

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcSchemaMigrator.class);
    private static final String HISTORY_TABLE = "gear4j_schema_history";
    private static final String LOCK_TABLE = "gear4j_schema_lock";

    private final String moduleId;
    private final Gear4jDatabaseDialect dialect;
    private final String migrationListResource;
    private final String baselineTableName;
    private final boolean baselineOnMigrate;
    private final BaselineSchemaValidator baselineSchemaValidator;
    private final ClassLoader classLoader;
    private final JdbcStatementOptions statementOptions;

    public static Builder builder() {
        return new Builder();
    }

    private JdbcSchemaMigrator(Builder builder) {
        this.moduleId = requireNonBlank(builder.moduleId, "moduleId");
        this.dialect = Objects.requireNonNull(builder.dialect, "dialect must not be null");
        this.migrationListResource = normalizeResource(requireNonBlank(builder.migrationListResource,
                                                                       "migrationListResource"));
        this.baselineTableName = requireNonBlank(builder.baselineTableName, "baselineTableName");
        this.baselineOnMigrate = builder.baselineOnMigrate;
        this.baselineSchemaValidator = new BaselineSchemaValidator(moduleId, baselineTableName,
                immutableRequirements(builder.requiredColumns), immutableRequirements(builder.requiredIndexes));
        this.classLoader = builder.classLoader != null ? builder.classLoader
                : JdbcSchemaMigrator.class.getClassLoader();
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
    }

    public static final class Builder {
        private String moduleId;
        private Gear4jDatabaseDialect dialect;
        private String migrationListResource;
        private String baselineTableName;
        private boolean baselineOnMigrate;
        private final Map<String, Set<String>> requiredColumns = new LinkedHashMap<>();
        private final Map<String, Set<String>> requiredIndexes = new LinkedHashMap<>();
        private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        private JdbcStatementOptions statementOptions = JdbcStatementOptions.defaults();

        private Builder() {
        }

        public Builder moduleId(String moduleId) {
            this.moduleId = moduleId;
            return this;
        }

        public Builder dialect(Gear4jDatabaseDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder migrationListResource(String migrationListResource) {
            this.migrationListResource = migrationListResource;
            return this;
        }

        public Builder baselineTableName(String baselineTableName) {
            this.baselineTableName = baselineTableName;
            return this;
        }

        /**
         * Explicitly allows an existing schema without Gear4J history to be marked at
         * the first migration after its expected tables, columns and indexes have been
         * validated.
         */
        public Builder baselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
            return this;
        }

        /** Adds columns that must exist before an existing schema can be baselined. */
        public Builder requiredColumns(String tableName, String... columns) {
            addRequirements(requiredColumns, tableName, "column", columns);
            return this;
        }

        /** Adds indexes that must exist before an existing schema can be baselined. */
        public Builder requiredIndexes(String tableName, String... indexes) {
            addRequirements(requiredIndexes, tableName, "index", indexes);
            return this;
        }

        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public Builder statementOptions(JdbcStatementOptions statementOptions) {
            this.statementOptions = statementOptions;
            return this;
        }

        public JdbcSchemaMigrator build() {
            return new JdbcSchemaMigrator(this);
        }
    }

    public static JdbcSchemaMigrator core(Gear4jDatabaseDialect dialect) {
        return core(dialect, false);
    }

    public static JdbcSchemaMigrator core(Gear4jDatabaseDialect dialect, boolean baselineOnMigrate) {
        return builder()
                .moduleId("gear4j-core")
                .dialect(dialect)
                .migrationListResource("io/github/gear4j/db/" + dialect.resourceDirectory()
                        + "/migrations/migrations.list")
                .baselineTableName(ASSEMBLY_RUN_TABLE)
                .baselineOnMigrate(baselineOnMigrate)
                .requiredColumns(ASSEMBLY_RUN_TABLE, "id", "assembly_line_id", "input_parameters", "context",
                                 "result", "status", "start_time", "end_time", "error_message",
                                 "parent_execution_id", "root_execution_id", "parent_station_log_id")
                .requiredColumns("station_log", "id", "assembly_line_execution_id", "operation_id",
                                 "parent_log_id", "branch_id", "status", "start_time", "end_time",
                                 "error_message", "error_handler_messages", "context", "item_id")
                .requiredIndexes(ASSEMBLY_RUN_TABLE, "idx_ar_assembly_line_id", "idx_ar_status",
                                 "idx_ar_assembly_line_start", "idx_ar_status_start")
                .requiredIndexes("station_log", "idx_sl_assembly_line_execution_id",
                                 "idx_station_log_exec_parent", "idx_station_log_run_start")
                .build();
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
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            boolean ownsTransaction = previousAutoCommit;
            if (ownsTransaction) {
                connection.setAutoCommit(false);
            }
            try {
                ensureInfrastructureTables(connection);
                acquireSchemaLock(connection);
                runMigrations(connection);
                if (ownsTransaction) {
                    connection.commit();
                }
            } catch (SQLException | IOException | RuntimeException e) {
                if (ownsTransaction) {
                    rollback(connection, e);
                }
                throw e;
            } finally {
                if (ownsTransaction) {
                    connection.setAutoCommit(previousAutoCommit);
                }
            }
        } catch (SQLException | IOException e) {
            throw new SchemaMigrationException("Failed to migrate Gear4J schema for module " + moduleId, e);
        }
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }

    private Statement createStatement(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        statementOptions.apply(statement);
        return statement;
    }

    private void ensureInfrastructureTables(Connection connection) throws SQLException {
        ensureHistoryTable(connection);
        ensureLockTable(connection);
        ensureLockRow(connection);
    }

    private void runMigrations(Connection connection) throws SQLException, IOException {
        List<SchemaMigration> migrations = loadMigrations();
        if (migrations.isEmpty()) {
            return;
        }
        if (hasNoHistory(connection) && baselineSchemaValidator.tableExists(connection, baselineTableName)) {
            if (!baselineOnMigrate) {
                throw new SchemaMigrationException("Existing Gear4J schema detected for module " + moduleId
                        + " without migration history. Enable baselineOnMigrate explicitly only after verifying "
                        + "that the schema is compatible with the bundled migrations");
            }
            baselineExistingSchema(connection, migrations.get(0));
            migrations = migrations.subList(1, migrations.size());
        }
        for (SchemaMigration migration : migrations) {
            applyIfNeeded(connection, migration);
        }
    }

    private void ensureHistoryTable(Connection connection) throws SQLException {
        executeCreateTableIfMissing(connection, HISTORY_TABLE, historyTableSql());
    }

    private void ensureLockTable(Connection connection) throws SQLException {
        executeCreateTableIfMissing(connection, LOCK_TABLE, lockTableSql());
    }

    private void executeCreateTableIfMissing(Connection connection, String tableName, String sql) throws SQLException {
        if (baselineSchemaValidator.tableExists(connection, tableName)) {
            return;
        }
        try (Statement statement = createStatement(connection)) {
            statement.execute(sql);
        } catch (SQLException e) {
            if (baselineSchemaValidator.tableExists(connection, tableName)) {
                LOGGER.debug("[Gear4J] Schema infrastructure table {} was created concurrently", tableName);
                return;
            }
            throw e;
        }
    }

    private String historyTableSql() {
        return switch (dialect) {
            case POSTGRESQL -> "CREATE TABLE IF NOT EXISTS gear4j_schema_history ("
                    + "module_id VARCHAR(100) NOT NULL, "
                    + "version VARCHAR(40) NOT NULL, "
                    + "description VARCHAR(300) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "installed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), "
                    + "PRIMARY KEY (module_id, version))";
            case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS gear4j_schema_history ("
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

    private String lockTableSql() {
        return switch (dialect) {
            case POSTGRESQL -> "CREATE TABLE IF NOT EXISTS gear4j_schema_lock ("
                    + "lock_name VARCHAR(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMPTZ NOT NULL DEFAULT NOW())";
            case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS gear4j_schema_lock ("
                    + "lock_name VARCHAR(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";
            case ORACLE -> "CREATE TABLE gear4j_schema_lock ("
                    + "lock_name VARCHAR2(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)";
            case H2 -> "CREATE TABLE IF NOT EXISTS gear4j_schema_lock ("
                    + "lock_name VARCHAR(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";
        };
    }

    private void ensureLockRow(Connection connection) throws SQLException {
        try (PreparedStatement statement = prepare(connection, lockRowInsertSql())) {
            statement.setString(1, moduleId);
            dialect.setInstant(statement, 2, Instant.now());
            statement.executeUpdate();
        }
    }

    private String lockRowInsertSql() {
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO gear4j_schema_lock(lock_name, locked_at) VALUES (?,?) "
                    + "ON CONFLICT (lock_name) DO NOTHING";
            case MYSQL, MARIADB -> "INSERT IGNORE INTO gear4j_schema_lock(lock_name, locked_at) VALUES (?,?)";
            case ORACLE -> "MERGE INTO gear4j_schema_lock target "
                    + "USING (SELECT ? lock_name, ? locked_at FROM dual) source "
                    + "ON (target.lock_name = source.lock_name) "
                    + "WHEN NOT MATCHED THEN INSERT (lock_name, locked_at) "
                    + "VALUES (source.lock_name, source.locked_at)";
            case H2 -> "MERGE INTO gear4j_schema_lock(lock_name, locked_at) KEY(lock_name) VALUES (?,?)";
        };
    }

    private void acquireSchemaLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "SELECT lock_name FROM gear4j_schema_lock WHERE lock_name = ? FOR UPDATE")) {
            statement.setString(1, moduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SchemaMigrationException("Gear4J schema lock row is missing for module " + moduleId);
                }
            }
        }
        try (PreparedStatement statement = prepare(connection,
                                                   "UPDATE gear4j_schema_lock SET locked_at = ? WHERE lock_name = ?")) {
            dialect.setInstant(statement, 1, Instant.now());
            statement.setString(2, moduleId);
            statement.executeUpdate();
        }
    }

    private boolean hasNoHistory(Connection connection) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
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
        baselineSchemaValidator.validate(connection, baseline.version(), content);
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
        try (PreparedStatement statement = prepare(connection,
                                                   "SELECT checksum FROM gear4j_schema_history WHERE module_id=? AND version=?")) {
            statement.setString(1, moduleId);
            statement.setString(2, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? new MigrationState(resultSet.getString(1)) : null;
            }
        }
    }

    private void insertHistory(Connection connection, SchemaMigration migration, String checksum) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "INSERT INTO gear4j_schema_history(module_id, version, description, checksum, installed_at) "
                                                           + "VALUES (?,?,?,?,?)")) {
            statement.setString(1, moduleId);
            statement.setString(2, migration.version());
            statement.setString(3, migration.description());
            statement.setString(4, checksum);
            dialect.setInstant(statement, 5, Instant.now());
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
        try (Statement statement = createStatement(connection)) {
            for (String sql : splitSqlStatements(scriptContent)) {
                statement.execute(sql);
            }
        }
    }

    static List<String> splitSqlStatements(String scriptContent) {
        return SqlScriptSplitter.split(scriptContent);
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
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

    private static void addRequirements(Map<String, Set<String>> target,
                                        String tableName,
                                        String kind,
                                        String... names) {
        String requiredTable = requireNonBlank(tableName, "tableName").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(names, kind + " names must not be null");
        Set<String> requirements = target.computeIfAbsent(requiredTable, ignored -> new LinkedHashSet<>());
        for (String name : names) {
            requirements.add(requireNonBlank(name, kind + " name").toLowerCase(Locale.ROOT));
        }
    }

    private static Map<String, Set<String>> immutableRequirements(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((table, names) -> copy.put(table, Set.copyOf(names)));
        return Map.copyOf(copy);
    }

    private record MigrationState(String checksum) {}
}
