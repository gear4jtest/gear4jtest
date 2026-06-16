package io.github.gear4jtest.core.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.migration.JdbcSchemaMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseAssemblyRunRepository implements AssemblyRunRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAssemblyRunRepository.class);
    private static final String ASSEMBLY_RUN_COLUMNS = "id, pipeline_id, input_parameters, context, result, "
            + "status, start_time, end_time, error_message, parent_execution_id, root_execution_id, "
            + "parent_station_log_id";
    private static final String STATION_LOG_COLUMNS = "id, pipeline_execution_id, operation_id, parent_log_id, "
            + "branch_id, status, start_time, end_time, error_message, error_handler_messages, context, item_id";
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Gear4jDatabaseDialect databaseDialect;

    public DatabaseAssemblyRunRepository(DataSource dataSource, Gear4jDatabaseDialect databaseDialect) {
        this(dataSource, databaseDialect, new ObjectMapper());
    }

    public DatabaseAssemblyRunRepository(DataSource dataSource,
                                         Gear4jDatabaseDialect databaseDialect,
                                         ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void initialize() {
        LOGGER.info("[Gear4J] Applying core schema migrations for {}", databaseDialect);
        JdbcSchemaMigrator.core(databaseDialect).migrate(dataSource);
    }

    @Override
    public void save(AssemblyRunRecord execution) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jDatabaseDialect dialect = databaseDialect;
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
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
                dialect.setInstant(stmt, 7, execution.startTime());
                dialect.setInstant(stmt, 8, execution.endTime());
                stmt.setString(9, execution.errorMessage());
                dialect.setUuid(stmt, 10, execution.parentExecutionId());
                dialect.setUuid(stmt, 11, execution.rootExecutionId());
                dialect.setUuid(stmt, 12, execution.parentStationLogId());
                stmt.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new ExecutionPersistenceException("Failed to save assembly run " + execution.id(), e);
        }
    }

    @Override
    public void update(AssemblyRunRecord execution) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jDatabaseDialect dialect = databaseDialect;
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            String sql = "UPDATE assembly_run SET context=?, result=?, status=?, end_time=?, error_message=?, "
                    + "parent_execution_id=?, root_execution_id=?, parent_station_log_id=? WHERE id=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                dialect.setJson(stmt, 1, toJson(execution.context()));
                dialect.setJson(stmt, 2, toJson(execution.result()));
                stmt.setString(3, execution.status().name());
                dialect.setInstant(stmt, 4, execution.endTime());
                stmt.setString(5, execution.errorMessage());
                dialect.setUuid(stmt, 6, execution.parentExecutionId());
                dialect.setUuid(stmt, 7, execution.rootExecutionId());
                dialect.setUuid(stmt, 8, execution.parentStationLogId());
                dialect.setUuid(stmt, 9, execution.id());
                int updatedRows = stmt.executeUpdate();
                if (updatedRows != 1) {
                    conn.rollback();
                    throw new ExecutionPersistenceException("Expected to update exactly one assembly run "
                            + execution.id() + " but updated " + updatedRows + " rows");
                }
                conn.commit();
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new ExecutionPersistenceException("Failed to update assembly run " + execution.id(), e);
        }
    }

    @Override
    public Optional<AssemblyRunRecord> findById(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jDatabaseDialect dialect = databaseDialect;
            String sql = "SELECT " + ASSEMBLY_RUN_COLUMNS + " FROM assembly_run WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                dialect.setUuid(stmt, 1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapExecution(rs, dialect));
                    }
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("find assembly run " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<AssemblyRunView> findViewById(UUID runId, PageRequest rootLogsPage) {
        Objects.requireNonNull(rootLogsPage, "rootLogsPage must not be null");
        return findById(runId).map(run -> new AssemblyRunView(run, findRootLogsByRunId(runId, rootLogsPage)));
    }

    @Override
    public List<AssemblyRunRecord> findByPipelineId(String pipelineId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        try (Connection conn = dataSource.getConnection()) {
            String sql = databaseDialect.pagedSql(
                                                  "SELECT " + ASSEMBLY_RUN_COLUMNS
                                                          + " FROM assembly_run WHERE pipeline_id = ? ORDER BY start_time DESC");
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, pipelineId);
                databaseDialect.bindPage(stmt, 2, pageRequest);
                return executeQuery(stmt, databaseDialect);
            }
        } catch (SQLException e) {
            throw persistenceFailure("find paged assembly runs for pipeline " + pipelineId, e);
        }
    }

    @Override
    public List<AssemblyRunRecord> findByStatus(ExecutionStatus status, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        try (Connection conn = dataSource.getConnection()) {
            String sql = databaseDialect.pagedSql(
                                                  "SELECT " + ASSEMBLY_RUN_COLUMNS
                                                          + " FROM assembly_run WHERE status = ? ORDER BY start_time DESC");
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                databaseDialect.bindPage(stmt, 2, pageRequest);
                return executeQuery(stmt, databaseDialect);
            }
        } catch (SQLException e) {
            throw persistenceFailure("find paged assembly runs with status " + status, e);
        }
    }

    @Override
    public void delete(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            Gear4jDatabaseDialect dialect = databaseDialect;
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
            throw persistenceFailure("delete assembly run " + id, e);
        }
    }

    @Override
    public List<AssemblyRunRecord> findAll(PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        try (Connection conn = dataSource.getConnection()) {
            String sql = databaseDialect
                    .pagedSql("SELECT " + ASSEMBLY_RUN_COLUMNS + " FROM assembly_run ORDER BY start_time DESC");
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                databaseDialect.bindPage(stmt, 1, pageRequest);
                return executeQuery(stmt, databaseDialect);
            }
        } catch (SQLException e) {
            throw persistenceFailure("find paged assembly runs", e);
        }
    }

    @Override
    public List<StationLogRecord> findRootLogsByRunId(UUID runId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        String base = "SELECT " + STATION_LOG_COLUMNS
                + " FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id IS NULL "
                + "ORDER BY start_time, id";
        String sql = databaseDialect.pagedSql(base);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            databaseDialect.setUuid(stmt, 1, runId);
            databaseDialect.bindPage(stmt, 2, pageRequest);
            return executeLogQuery(stmt, databaseDialect);
        } catch (SQLException e) {
            throw persistenceFailure("find paged root station logs for run " + runId, e);
        }
    }

    @Override
    public List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        String base = "SELECT " + STATION_LOG_COLUMNS
                + " FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id = ? "
                + "ORDER BY start_time, id";
        String sql = databaseDialect.pagedSql(base);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            databaseDialect.setUuid(stmt, 1, runId);
            databaseDialect.setUuid(stmt, 2, parentLogId);
            databaseDialect.bindPage(stmt, 3, pageRequest);
            return executeLogQuery(stmt, databaseDialect);
        } catch (SQLException e) {
            throw persistenceFailure("find paged child station logs for run " + runId + " and parent " + parentLogId,
                                     e);
        }
    }

    @Override
    public List<StationLogRecord> findAllLogsByRunId(UUID runId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        String base = "SELECT " + STATION_LOG_COLUMNS
                + " FROM station_log WHERE pipeline_execution_id = ? ORDER BY start_time, id";
        String sql = databaseDialect.pagedSql(base);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            databaseDialect.setUuid(stmt, 1, runId);
            databaseDialect.bindPage(stmt, 2, pageRequest);
            return executeLogQuery(stmt, databaseDialect);
        } catch (SQLException e) {
            throw persistenceFailure("find paged station logs for run " + runId, e);
        }
    }

    @Override
    public long countChildLogsByRunId(UUID runId, UUID parentLogId) {
        String sql = parentLogId == null
                ? "SELECT COUNT(*) FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id IS NULL"
                : "SELECT COUNT(*) FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            Gear4jDatabaseDialect dialect = databaseDialect;
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
            throw persistenceFailure("count child station logs for run " + runId + " and parent " + parentLogId, e);
        }
    }

    public void saveOperationRecordsBatch(List<StationLogRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            Gear4jDatabaseDialect dialect = databaseDialect;
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
            throw persistenceFailure("save station log batch " + describeRecords(records), e);
        }
    }

    private String describeRecords(List<StationLogRecord> records) {
        LinkedHashSet<UUID> runIds = new LinkedHashSet<>();
        LinkedHashSet<UUID> logIds = new LinkedHashSet<>();
        for (StationLogRecord record : records) {
            if (record.pipelineExecutionId() != null && runIds.size() < 5) {
                runIds.add(record.pipelineExecutionId());
            }
            if (record.id() != null && logIds.size() < 5) {
                logIds.add(record.id());
            }
        }
        return "size=" + records.size() + ", runIds=" + runIds + ", stationLogIds=" + logIds;
    }

    private void saveOperationRecord(Connection conn, Gear4jDatabaseDialect dialect, StationLogRecord rec)
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

    private int updateOpenStationLog(Connection conn, Gear4jDatabaseDialect dialect, StationLogRecord rec)
            throws SQLException {
        String sql = "UPDATE station_log SET branch_id=?, status=?, end_time=?, error_message=?, "
                + "error_handler_messages=?, context=?, item_id=? WHERE id=? AND end_time IS NULL";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rec.branchId());
            stmt.setString(2, rec.status().toString());
            dialect.setInstant(stmt, 3, rec.endedAt());
            stmt.setString(4, rec.errorMessage());
            stmt.setString(5, rec.errorHandlerMessages());
            dialect.setJson(stmt, 6, toJson(rec.context()));
            stmt.setString(7, rec.itemId());
            dialect.setUuid(stmt, 8, rec.id());
            return stmt.executeUpdate();
        }
    }

    private void insertStationLog(Connection conn, Gear4jDatabaseDialect dialect, StationLogRecord rec)
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
            dialect.setInstant(stmt, 7, rec.startedAt());
            dialect.setInstant(stmt, 8, rec.endedAt());
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

    private List<AssemblyRunRecord> executeQuery(PreparedStatement stmt, Gear4jDatabaseDialect dialect)
            throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<AssemblyRunRecord> executions = new ArrayList<>();
            while (rs.next()) {
                executions.add(mapExecution(rs, dialect));
            }
            return executions;
        }
    }

    private List<StationLogRecord> executeLogQuery(PreparedStatement stmt, Gear4jDatabaseDialect dialect)
            throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<StationLogRecord> logs = new ArrayList<>();
            while (rs.next()) {
                logs.add(mapOperation(rs, dialect));
            }
            return logs;
        }
    }

    private AssemblyRunRecord mapExecution(ResultSet rs, Gear4jDatabaseDialect dialect) throws SQLException {
        return new AssemblyRunRecord(
                dialect.getUuid(rs, "id"),
                rs.getString("pipeline_id"),
                fromJson(dialect.getJson(rs, "context"), new TypeReference<>() {}),
                fromJson(dialect.getJson(rs, "input_parameters"), Map.class),
                fromJson(dialect.getJson(rs, "result"), Object.class),
                ExecutionStatus.valueOf(rs.getString("status")),
                dialect.getInstant(rs, "start_time"),
                dialect.getInstant(rs, "end_time"),
                rs.getString("error_message"),
                dialect.getUuid(rs, "parent_execution_id"),
                dialect.getUuid(rs, "root_execution_id"),
                dialect.getUuid(rs, "parent_station_log_id"));
    }

    private StationLogRecord mapOperation(ResultSet rs, Gear4jDatabaseDialect dialect) throws SQLException {
        return new StationLogRecord(
                dialect.getUuid(rs, "id"),
                dialect.getUuid(rs, "pipeline_execution_id"),
                rs.getString("operation_id"),
                dialect.getUuid(rs, "parent_log_id"),
                rs.getString("branch_id"),
                StationLogStatus.valueOf(rs.getString("status")),
                dialect.getInstant(rs, "start_time"),
                dialect.getInstant(rs, "end_time"),
                rs.getString("error_message"),
                rs.getString("error_handler_messages"),
                fromJson(dialect.getJson(rs, "context"), new TypeReference<>() {}),
                rs.getString("item_id"));
    }

    private String toJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to serialize persistence payload", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return json != null ? objectMapper.readValue(json, clazz) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to deserialize persistence payload as " + clazz.getName(),
                    e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return json != null ? objectMapper.readValue(json, type) : null;
        } catch (Exception e) {
            throw new ExecutionPersistenceException("Failed to deserialize persistence payload", e);
        }
    }

    private ExecutionPersistenceException persistenceFailure(String operation, SQLException cause) {
        return new ExecutionPersistenceException("Failed to " + operation + " using " + databaseDialect, cause);
    }
}
