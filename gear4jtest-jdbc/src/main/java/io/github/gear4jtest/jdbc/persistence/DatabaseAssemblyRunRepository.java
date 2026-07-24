package io.github.gear4jtest.jdbc.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.AssemblyRunRepository;
import io.github.gear4jtest.core.persistence.AssemblyRunView;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.migration.JdbcSchemaMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseAssemblyRunRepository implements AssemblyRunRepository {
    private static final String PAGE_REQUEST_REQUIRED = "pageRequest must not be null";

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAssemblyRunRepository.class);

    private final DataSource dataSource;
    private final Gear4jDatabaseDialect databaseDialect;
    private final PersistenceJsonCodec jsonCodec;
    private final AssemblyRunRecordRowMapper assemblyRunMapper;
    private final StationLogRecordRowMapper stationLogMapper;
    private final AssemblyRunRecordStatementBinder assemblyRunBinder;
    private final StationLogRecordStatementBinder stationLogBinder;
    private final JdbcStatementOptions statementOptions;
    private final boolean baselineOnMigrate;

    public static Builder builder() {
        return new Builder();
    }

    private DatabaseAssemblyRunRepository(Builder builder) {
        this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource must not be null");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
        this.baselineOnMigrate = builder.baselineOnMigrate;
        this.jsonCodec = Objects.requireNonNull(builder.jsonCodec, "jsonCodec must not be null");
        this.assemblyRunMapper = new AssemblyRunRecordRowMapper(databaseDialect, jsonCodec);
        this.stationLogMapper = new StationLogRecordRowMapper(databaseDialect, jsonCodec);
        this.assemblyRunBinder = new AssemblyRunRecordStatementBinder(databaseDialect, jsonCodec);
        this.stationLogBinder = new StationLogRecordStatementBinder(databaseDialect, jsonCodec);
    }

    public static final class Builder {
        private DataSource dataSource;
        private Gear4jDatabaseDialect databaseDialect;
        private PersistenceJsonCodec jsonCodec = PersistenceJsonCodec.jackson(new ObjectMapper());
        private JdbcStatementOptions statementOptions = JdbcStatementOptions.defaults();
        private boolean baselineOnMigrate;

        private Builder() {
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder databaseDialect(Gear4jDatabaseDialect databaseDialect) {
            this.databaseDialect = databaseDialect;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.jsonCodec = PersistenceJsonCodec.jackson(objectMapper);
            return this;
        }

        public Builder jsonCodec(PersistenceJsonCodec jsonCodec) {
            this.jsonCodec = jsonCodec;
            return this;
        }

        public Builder jdbcStatementTimeout(Duration jdbcStatementTimeout) {
            this.statementOptions = JdbcStatementOptions.of(jdbcStatementTimeout);
            return this;
        }

        public Builder statementOptions(JdbcStatementOptions statementOptions) {
            this.statementOptions = statementOptions;
            return this;
        }

        /**
         * Explicitly allows a compatible pre-existing schema without Gear4J migration
         * history to be baselined during
         * {@link DatabaseAssemblyRunRepository#initialize()}.
         */
        public Builder baselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
            return this;
        }

        public DatabaseAssemblyRunRepository build() {
            return new DatabaseAssemblyRunRepository(this);
        }
    }

    @Override
    public void initialize() {
        LOGGER.info("[Gear4J] Applying core schema migrations for {}", databaseDialect);
        JdbcSchemaMigrator.core(databaseDialect, baselineOnMigrate).migrate(dataSource);
    }

    /**
     * Executes a bounded provider-specific query to verify current connectivity.
     */
    public boolean checkConnectivity(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(databaseDialect.validationQuery())) {
            statement.setQueryTimeout(toQueryTimeoutSeconds(timeout));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new ExecutionPersistenceException("Persistence connectivity probe failed for " + databaseDialect,
                    exception);
        }
    }

    private static int toQueryTimeoutSeconds(Duration timeout) {
        long seconds = timeout.getSeconds();
        if (timeout.getNano() > 0 && seconds < Long.MAX_VALUE) {
            seconds++;
        }
        seconds = Math.max(1L, seconds);
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    @Override
    public void save(AssemblyRunRecord execution) {
        try {
            JdbcRepositoryTransaction.run(dataSource, conn -> {
                try (PreparedStatement stmt = prepare(conn,
                                                      DatabaseAssemblyRunSql.insertAssemblyRun(databaseDialect))) {
                    assemblyRunBinder.bindInsert(stmt, execution);
                    stmt.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new ExecutionPersistenceException("Failed to save assembly run " + execution.id(), e);
        }
    }

    @Override
    public void update(AssemblyRunRecord execution) {
        try {
            JdbcRepositoryTransaction.run(dataSource, conn -> {
                try (PreparedStatement stmt = prepare(conn,
                                                      DatabaseAssemblyRunSql.updateAssemblyRun(databaseDialect))) {
                    assemblyRunBinder.bindUpdate(stmt, execution);
                    int updatedRows = stmt.executeUpdate();
                    if (updatedRows != 1) {
                        throw new ExecutionPersistenceException("Expected to update exactly one assembly run "
                                + execution.id() + " but updated " + updatedRows + " rows");
                    }
                }
            });
        } catch (SQLException e) {
            throw new ExecutionPersistenceException("Failed to update assembly run " + execution.id(), e);
        }
    }

    @Override
    public Optional<AssemblyRunRecord> findById(UUID id) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = prepare(conn, DatabaseAssemblyRunSql.selectAssemblyRunById())) {
            databaseDialect.setUuid(stmt, 1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(assemblyRunMapper.map(rs));
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
    public List<AssemblyRunRecord> findByAssemblyLineId(String assemblyLineId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, PAGE_REQUEST_REQUIRED);
        String sql = DatabaseAssemblyRunSql.selectAssemblyRunsByAssemblyLineId(databaseDialect);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = prepare(conn, sql)) {
            stmt.setString(1, assemblyLineId);
            databaseDialect.bindPage(stmt, 2, pageRequest);
            return executeAssemblyRunQuery(stmt);
        } catch (SQLException e) {
            throw persistenceFailure("find paged assembly runs for assembly line " + assemblyLineId, e);
        }
    }

    @Override
    public List<AssemblyRunRecord> findByStatus(ExecutionStatus status, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, PAGE_REQUEST_REQUIRED);
        String sql = DatabaseAssemblyRunSql.selectAssemblyRunsByStatus(databaseDialect);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = prepare(conn, sql)) {
            stmt.setString(1, status.name());
            databaseDialect.bindPage(stmt, 2, pageRequest);
            return executeAssemblyRunQuery(stmt);
        } catch (SQLException e) {
            throw persistenceFailure("find paged assembly runs with status " + status, e);
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            JdbcRepositoryTransaction.run(dataSource, conn -> {
                try (PreparedStatement deleteLogs = prepare(conn, DatabaseAssemblyRunSql.deleteStationLogsByRunId());
                        PreparedStatement deleteRun = prepare(conn, DatabaseAssemblyRunSql.deleteAssemblyRunById())) {
                    databaseDialect.setUuid(deleteLogs, 1, id);
                    deleteLogs.executeUpdate();
                    databaseDialect.setUuid(deleteRun, 1, id);
                    deleteRun.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw persistenceFailure("delete assembly run " + id, e);
        }
    }

    @Override
    public List<AssemblyRunRecord> findAll(PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, PAGE_REQUEST_REQUIRED);
        String sql = DatabaseAssemblyRunSql.selectAllAssemblyRuns(databaseDialect);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = prepare(conn, sql)) {
            databaseDialect.bindPage(stmt, 1, pageRequest);
            return executeAssemblyRunQuery(stmt);
        } catch (SQLException e) {
            throw persistenceFailure("find paged assembly runs", e);
        }
    }

    @Override
    public List<StationLogRecord> findRootLogsByRunId(UUID runId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, PAGE_REQUEST_REQUIRED);
        String sql = DatabaseAssemblyRunSql.selectRootStationLogsByRunId(databaseDialect);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = prepare(conn, sql)) {
            databaseDialect.setUuid(stmt, 1, runId);
            databaseDialect.bindPage(stmt, 2, pageRequest);
            return executeStationLogQuery(stmt);
        } catch (SQLException e) {
            throw persistenceFailure("find paged root station logs for run " + runId, e);
        }
    }

    @Override
    public List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, PAGE_REQUEST_REQUIRED);
        String sql = DatabaseAssemblyRunSql.selectChildStationLogsByRunId(databaseDialect);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = prepare(conn, sql)) {
            databaseDialect.setUuid(stmt, 1, runId);
            databaseDialect.setUuid(stmt, 2, parentLogId);
            databaseDialect.bindPage(stmt, 3, pageRequest);
            return executeStationLogQuery(stmt);
        } catch (SQLException e) {
            throw persistenceFailure("find paged child station logs for run " + runId + " and parent " + parentLogId,
                                     e);
        }
    }

    @Override
    public List<StationLogRecord> findAllLogsByRunId(UUID runId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, PAGE_REQUEST_REQUIRED);
        String sql = DatabaseAssemblyRunSql.selectAllStationLogsByRunId(databaseDialect);
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = prepare(conn, sql)) {
            databaseDialect.setUuid(stmt, 1, runId);
            databaseDialect.bindPage(stmt, 2, pageRequest);
            return executeStationLogQuery(stmt);
        } catch (SQLException e) {
            throw persistenceFailure("find paged station logs for run " + runId, e);
        }
    }

    @Override
    public long countChildLogsByRunId(UUID runId, UUID parentLogId) {
        String sql = parentLogId == null ? DatabaseAssemblyRunSql.countRootStationLogsByRunId()
                : DatabaseAssemblyRunSql.countChildStationLogsByRunId();
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = prepare(conn, sql)) {
            databaseDialect.setUuid(stmt, 1, runId);
            if (parentLogId != null) {
                databaseDialect.setUuid(stmt, 2, parentLogId);
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

        try {
            JdbcRepositoryTransaction.run(dataSource, conn -> saveOperationRecordsBatch(conn, records));
        } catch (SQLException e) {
            throw persistenceFailure("save station log batch " + describeRecords(records), e);
        }
    }

    public void saveOperationRecord(StationLogRecord stationLogRecord) {
        saveOperationRecordsBatch(List.of(stationLogRecord));
    }

    private void saveOperationRecordsBatch(Connection conn, List<StationLogRecord> records) throws SQLException {
        if (databaseDialect.supportsNativeStationLogUpsert()) {
            upsertStationLogsBatch(conn, records);
            return;
        }

        List<StationLogRecord> insertCandidates = updateOpenStationLogsBatch(conn, records);
        if (insertCandidates.isEmpty()) {
            return;
        }
        insertStationLogsBatch(conn, insertCandidates);
    }

    private void saveOperationRecord(Connection conn, StationLogRecord rec) throws SQLException {
        if (updateOpenStationLog(conn, rec) > 0) {
            return;
        }

        try {
            insertStationLog(conn, rec);
        } catch (SQLException e) {
            if (!isUniqueViolation(e)) {
                throw e;
            }
            // The row already exists, either because another writer inserted it or because
            // the log is already finalized. Try one last open-row update; if it still
            // updates zero rows, keep the existing finalized record unchanged.
            updateOpenStationLog(conn, rec);
        }
    }

    private List<StationLogRecord> updateOpenStationLogsBatch(Connection conn, List<StationLogRecord> records)
            throws SQLException {
        try (PreparedStatement stmt = prepare(conn, DatabaseAssemblyRunSql.updateOpenStationLog(databaseDialect))) {
            for (StationLogRecord rec : records) {
                stationLogBinder.bindUpdateOpen(stmt, rec);
                stmt.addBatch();
            }
            int[] counts = stmt.executeBatch();
            return recordsRequiringInsert(records, counts);
        }
    }

    List<StationLogRecord> recordsRequiringInsert(List<StationLogRecord> records, int[] updateCounts) {
        if (updateCounts == null || updateCounts.length != records.size()) {
            return List.copyOf(records);
        }
        List<StationLogRecord> insertCandidates = new ArrayList<>();
        for (int i = 0; i < updateCounts.length; i++) {
            int count = updateCounts[i];
            if (count == 0) {
                insertCandidates.add(records.get(i));
            } else if (count == Statement.EXECUTE_FAILED) {
                return List.copyOf(records);
            }
        }
        return insertCandidates;
    }

    private void upsertStationLogsBatch(Connection conn, List<StationLogRecord> records) throws SQLException {
        try (PreparedStatement stmt = prepare(conn, DatabaseAssemblyRunSql.upsertStationLog(databaseDialect))) {
            for (StationLogRecord rec : records) {
                stationLogBinder.bindInsert(stmt, rec);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertStationLogsBatch(Connection conn, List<StationLogRecord> records) throws SQLException {
        try (PreparedStatement stmt = prepare(conn, DatabaseAssemblyRunSql.insertStationLog(databaseDialect))) {
            for (StationLogRecord rec : records) {
                stationLogBinder.bindInsert(stmt, rec);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            if (!isUniqueViolation(e)) {
                throw e;
            }
            // A batch insert can fail after only part of the batch was applied, and JDBC
            // drivers expose this differently. Fall back to the single-record algorithm so
            // every candidate is either inserted, updated if still open, or deliberately
            // left unchanged when the existing row is already finalized.
            for (StationLogRecord rec : records) {
                saveOperationRecord(conn, rec);
            }
        }
    }

    private int updateOpenStationLog(Connection conn, StationLogRecord rec) throws SQLException {
        try (PreparedStatement stmt = prepare(conn, DatabaseAssemblyRunSql.updateOpenStationLog(databaseDialect))) {
            stationLogBinder.bindUpdateOpen(stmt, rec);
            return stmt.executeUpdate();
        }
    }

    private void insertStationLog(Connection conn, StationLogRecord rec) throws SQLException {
        try (PreparedStatement stmt = prepare(conn, DatabaseAssemblyRunSql.insertStationLog(databaseDialect))) {
            stationLogBinder.bindInsert(stmt, rec);
            stmt.executeUpdate();
        }
    }

    private PreparedStatement prepare(Connection conn, String sql) throws SQLException {
        return statementOptions.prepare(conn, sql);
    }

    private List<AssemblyRunRecord> executeAssemblyRunQuery(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<AssemblyRunRecord> executions = new ArrayList<>();
            while (rs.next()) {
                executions.add(assemblyRunMapper.map(rs));
            }
            return executions;
        }
    }

    private List<StationLogRecord> executeStationLogQuery(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<StationLogRecord> logs = new ArrayList<>();
            while (rs.next()) {
                logs.add(stationLogMapper.map(rs));
            }
            return logs;
        }
    }

    private String describeRecords(List<StationLogRecord> records) {
        LinkedHashSet<UUID> runIds = new LinkedHashSet<>();
        LinkedHashSet<UUID> logIds = new LinkedHashSet<>();
        for (StationLogRecord stationLogRecord : records) {
            if (stationLogRecord.assemblyLineExecutionId() != null && runIds.size() < 5) {
                runIds.add(stationLogRecord.assemblyLineExecutionId());
            }
            if (stationLogRecord.id() != null && logIds.size() < 5) {
                logIds.add(stationLogRecord.id());
            }
        }
        return "size=" + records.size() + ", runIds=" + runIds + ", stationLogIds=" + logIds;
    }

    private boolean isUniqueViolation(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if (databaseDialect.isUniqueViolation(current)) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private ExecutionPersistenceException persistenceFailure(String operation, SQLException cause) {
        return new ExecutionPersistenceException("Failed to " + operation + " using " + databaseDialect, cause);
    }
}
