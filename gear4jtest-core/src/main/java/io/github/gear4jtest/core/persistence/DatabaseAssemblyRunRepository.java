package io.github.gear4jtest.core.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.model.StationLogStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseAssemblyRunRepository implements AssemblyRunRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAssemblyRunRepository.class);
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Gear4jJdbcDialect jdbcDialect;

    public DatabaseAssemblyRunRepository(DataSource dataSource) {
        this(dataSource, resolveDialect(dataSource), new ObjectMapper());
    }

    public DatabaseAssemblyRunRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this(dataSource, resolveDialect(dataSource), objectMapper);
    }

    public DatabaseAssemblyRunRepository(DataSource dataSource, Gear4jJdbcDialect jdbcDialect) {
        this(dataSource, jdbcDialect, new ObjectMapper());
    }

    public DatabaseAssemblyRunRepository(DataSource dataSource,
                                         Gear4jJdbcDialect jdbcDialect,
                                         ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.jdbcDialect = Objects.requireNonNull(jdbcDialect, "jdbcDialect must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public static DatabaseAssemblyRunRepository autoDetecting(DataSource dataSource) {
        return new DatabaseAssemblyRunRepository(dataSource);
    }

    @Override
    public void initialize() {
        try (Connection conn = dataSource.getConnection()) {
            if (isSchemaInitialized(conn)) {
                return;
            }

            String scriptPath = jdbcDialect.schemaScriptPath();
            LOGGER.info("[Gear4J] Initializing schema using script: {} ({})", scriptPath, jdbcDialect);
            executeScript(conn, scriptPath);
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Error while initializing Gear4J schema", e);
        }
    }

    private boolean isSchemaInitialized(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        return tableExists(meta, "assembly_run") || tableExists(meta, "ASSEMBLY_RUN");
    }

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private static Gear4jJdbcDialect resolveDialect(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        try (Connection conn = dataSource.getConnection()) {
            return Gear4jJdbcDialect.from(conn.getMetaData());
        } catch (SQLException e) {
            throw new RuntimeException("Could not resolve Gear4J JDBC dialect", e);
        }
    }

    private void executeScript(Connection conn, String scriptPath) throws IOException, SQLException {
        try (InputStream is = getClass().getResourceAsStream(scriptPath)) {
            if (is == null) {
                throw new IOException("Script file not found in classpath: " + scriptPath);
            }

            String scriptContent;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                scriptContent = reader.lines().collect(Collectors.joining("\n"));
            }

            String[] statements = scriptContent.split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String raw : statements) {
                    String sql = raw.trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                }
            }
        }
    }

    @Override
    public void save(AssemblyRunRecord execution) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            String sql = "INSERT INTO assembly_run (id, pipeline_id, input_parameters, context, result, "
                    + "status, start_time, end_time, error_message, parent_execution_id, root_execution_id, "
                    + "parent_station_log_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                dialect.setUuid(stmt, 1, execution.id());
                stmt.setString(2, execution.pipelineId());
                dialect.setJson(stmt, 3, toJson(execution.inputParams()));
                dialect.setJson(stmt, 4, toJson(execution.context()));
                dialect.setJson(stmt, 5, toJson(execution.result()));
                stmt.setString(6, execution.status().name());
                stmt.setTimestamp(7, execution.startTime() != null ? Timestamp.from(execution.startTime()) : null);
                stmt.setTimestamp(8, execution.endTime() != null ? Timestamp.from(execution.endTime()) : null);
                stmt.setString(9, execution.errorMessage());
                dialect.setUuid(stmt, 10, execution.parentExecutionId());
                dialect.setUuid(stmt, 11, execution.rootExecutionId());
                dialect.setUuid(stmt, 12, execution.parentStationLogId());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(AssemblyRunRecord execution) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            String sql = "UPDATE assembly_run SET context=?, result=?, status=?, end_time=?, error_message=?, "
                    + "parent_execution_id=?, root_execution_id=?, parent_station_log_id=? WHERE id=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                dialect.setJson(stmt, 1, toJson(execution.context()));
                dialect.setJson(stmt, 2, toJson(execution.result()));
                stmt.setString(3, execution.status().name());
                stmt.setTimestamp(4, execution.endTime() != null ? Timestamp.from(execution.endTime()) : null);
                stmt.setString(5, execution.errorMessage());
                dialect.setUuid(stmt, 6, execution.parentExecutionId());
                dialect.setUuid(stmt, 7, execution.rootExecutionId());
                dialect.setUuid(stmt, 8, execution.parentStationLogId());
                dialect.setUuid(stmt, 9, execution.id());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<AssemblyRunRecord> findById(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            String sql = "SELECT * FROM assembly_run WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                dialect.setUuid(stmt, 1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapExecution(rs, dialect));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<AssemblyRunView> findViewById(UUID runId) {
        return findById(runId).map(run -> new AssemblyRunView(run, findRootLogsByRunId(runId)));
    }

    @Override
    public List<AssemblyRunRecord> findByPipelineId(String pipelineId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM assembly_run WHERE pipeline_id = ? ORDER BY start_time DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, pipelineId);
                return executeQuery(stmt, jdbcDialect);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AssemblyRunRecord> findByStatus(ExecutionStatus status) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM assembly_run WHERE status = ? ORDER BY start_time DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                return executeQuery(stmt, jdbcDialect);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement stmt1 = conn
                    .prepareStatement("DELETE FROM station_log WHERE pipeline_execution_id = ?");
                    PreparedStatement stmt2 = conn.prepareStatement("DELETE FROM assembly_run WHERE id = ?")) {
                dialect.setUuid(stmt1, 1, id);
                stmt1.executeUpdate();
                dialect.setUuid(stmt2, 1, id);
                stmt2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AssemblyRunRecord> findAll() {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM assembly_run";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                return executeQuery(stmt, jdbcDialect);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<StationLogRecord> findRootLogsByRunId(UUID runId) {
        String sql = "SELECT * FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id IS NULL ORDER BY start_time, id";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            dialect.setUuid(stmt, 1, runId);
            return executeLogQuery(stmt, dialect);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId) {
        String sql = "SELECT * FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id = ? ORDER BY start_time, id";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            dialect.setUuid(stmt, 1, runId);
            dialect.setUuid(stmt, 2, parentLogId);
            return executeLogQuery(stmt, dialect);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long countChildLogsByRunId(UUID runId, UUID parentLogId) {
        String sql = parentLogId == null
                ? "SELECT COUNT(*) FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id IS NULL"
                : "SELECT COUNT(*) FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            dialect.setUuid(stmt, 1, runId);
            if (parentLogId != null) {
                dialect.setUuid(stmt, 2, parentLogId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<StationLogRecord> findAllLogsByRunId(UUID runId) {
        String sql = "SELECT * FROM station_log WHERE pipeline_execution_id = ? ORDER BY start_time, id";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            dialect.setUuid(stmt, 1, runId);
            return executeLogQuery(stmt, dialect);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveOperationRecordsBatch(List<StationLogRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            Gear4jJdbcDialect dialect = jdbcDialect;
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (StationLogRecord rec : records) {
                    saveOperationRecord(conn, dialect, rec);
                }
                conn.commit();
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveOperationRecord(Connection conn, Gear4jJdbcDialect dialect, StationLogRecord rec)
            throws SQLException {
        if (updateOpenStationLog(conn, dialect, rec) > 0) {
            return;
        }

        try {
            insertStationLog(conn, dialect, rec);
        } catch (SQLException e) {
            if (!dialect.isUniqueViolation(e)) {
                throw e;
            }
            // The row already exists, either because another writer inserted it or because
            // the log is already finalized. Try one last open-row update; if it still
            // updates zero rows, keep the existing finalized record unchanged.
            updateOpenStationLog(conn, dialect, rec);
        }
    }

    private int updateOpenStationLog(Connection conn, Gear4jJdbcDialect dialect, StationLogRecord rec)
            throws SQLException {
        String sql = "UPDATE station_log SET branch_id=?, status=?, end_time=?, error_message=?, "
                + "error_handler_messages=?, context=?, item_id=? WHERE id=? AND end_time IS NULL";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rec.branchId());
            stmt.setString(2, rec.status().toString());
            stmt.setTimestamp(3, rec.endedAt() != null ? Timestamp.from(rec.endedAt()) : null);
            stmt.setString(4, rec.errorMessage());
            stmt.setString(5, rec.errorHandlerMessages());
            dialect.setJson(stmt, 6, toJson(rec.context()));
            stmt.setString(7, rec.itemId());
            dialect.setUuid(stmt, 8, rec.id());
            return stmt.executeUpdate();
        }
    }

    private void insertStationLog(Connection conn, Gear4jJdbcDialect dialect, StationLogRecord rec)
            throws SQLException {
        String sql = "INSERT INTO station_log (id, pipeline_execution_id, operation_id, parent_log_id, branch_id, "
                + "status, start_time, end_time, error_message, error_handler_messages, context, item_id) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            dialect.setUuid(stmt, 1, rec.id());
            dialect.setUuid(stmt, 2, rec.pipelineExecutionId());
            stmt.setString(3, rec.operationId());
            dialect.setUuid(stmt, 4, rec.parentOperationId());
            stmt.setString(5, rec.branchId());
            stmt.setString(6, rec.status().toString());
            stmt.setTimestamp(7, rec.startedAt() != null ? Timestamp.from(rec.startedAt()) : null);
            stmt.setTimestamp(8, rec.endedAt() != null ? Timestamp.from(rec.endedAt()) : null);
            stmt.setString(9, rec.errorMessage());
            stmt.setString(10, rec.errorHandlerMessages());
            dialect.setJson(stmt, 11, toJson(rec.context()));
            stmt.setString(12, rec.itemId());
            stmt.executeUpdate();
        }
    }

    private static void rollback(Connection conn, SQLException original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    public void saveOperationRecord(StationLogRecord record) {
        saveOperationRecordsBatch(List.of(record));
    }

    private List<AssemblyRunRecord> executeQuery(PreparedStatement stmt, Gear4jJdbcDialect dialect)
            throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<AssemblyRunRecord> executions = new ArrayList<>();
            while (rs.next()) {
                executions.add(mapExecution(rs, dialect));
            }
            return executions;
        }
    }

    private List<StationLogRecord> executeLogQuery(PreparedStatement stmt, Gear4jJdbcDialect dialect)
            throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<StationLogRecord> logs = new ArrayList<>();
            while (rs.next()) {
                logs.add(mapOperation(rs, dialect));
            }
            return logs;
        }
    }

    private AssemblyRunRecord mapExecution(ResultSet rs, Gear4jJdbcDialect dialect) throws SQLException {
        return new AssemblyRunRecord(
                dialect.getUuid(rs, "id"),
                rs.getString("pipeline_id"),
                fromJson(dialect.getJson(rs, "context"), new TypeReference<>() {}),
                fromJson(dialect.getJson(rs, "input_parameters"), Map.class),
                fromJson(dialect.getJson(rs, "result"), Object.class),
                ExecutionStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("start_time")),
                toInstant(rs.getTimestamp("end_time")),
                rs.getString("error_message"),
                dialect.getUuid(rs, "parent_execution_id"),
                dialect.getUuid(rs, "root_execution_id"),
                dialect.getUuid(rs, "parent_station_log_id"));
    }

    private StationLogRecord mapOperation(ResultSet rs, Gear4jJdbcDialect dialect) throws SQLException {
        return new StationLogRecord(
                dialect.getUuid(rs, "id"),
                dialect.getUuid(rs, "pipeline_execution_id"),
                rs.getString("operation_id"),
                dialect.getUuid(rs, "parent_log_id"),
                rs.getString("branch_id"),
                StationLogStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("start_time")),
                toInstant(rs.getTimestamp("end_time")),
                rs.getString("error_message"),
                rs.getString("error_handler_messages"),
                fromJson(dialect.getJson(rs, "context"), new TypeReference<>() {}),
                rs.getString("item_id"));
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private String toJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return json != null ? objectMapper.readValue(json, clazz) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return json != null ? objectMapper.readValue(json, type) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
