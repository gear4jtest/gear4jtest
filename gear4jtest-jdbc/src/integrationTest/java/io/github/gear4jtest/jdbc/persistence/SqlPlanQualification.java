package io.github.gear4jtest.jdbc.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.jdbc.persistence.SqlPlanQualificationReport.SqlIndexEvidence;
import io.github.gear4jtest.jdbc.persistence.SqlPlanQualificationReport.SqlPlanQualificationResult;
import io.github.gear4jtest.jdbc.persistence.SqlPlanQualificationReport.SqlQueryEvidence;

/**
 * Connected qualification of critical repository reads at representative
 * cardinalities.
 */
final class SqlPlanQualification {
    private static final int ASSEMBLY_RUN_COUNT = 20_000;
    private static final int STATION_LOGS_PER_RUN = 5_000;
    private static final int ROOT_LOGS_PER_RUN = 100;
    private static final int BATCH_SIZE = 500;
    private static final int WARMUP_COUNT = 3;
    private static final int SAMPLE_COUNT = 9;
    private static final long MAX_QUERY_MILLIS = 2_000L;
    private static final String TARGET_ASSEMBLY_LINE = "plan-target";
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private final Gear4jDatabaseDialect dialect;
    private final DataSource dataSource;
    private final DatabaseAssemblyRunRepository repository;

    SqlPlanQualification(Gear4jDatabaseDialect dialect,
                         DataSource dataSource,
                         DatabaseAssemblyRunRepository repository) {
        this.dialect = dialect;
        this.dataSource = dataSource;
        this.repository = repository;
    }

    void execute() {
        List<SqlIndexEvidence> indexEvidence = SqlPlanQualificationIndexVerifier.verify(dataSource);
        DataSet dataSet = seedRepresentativeData();
        refreshOptimizerStatistics();

        List<SqlQueryEvidence> evidence = new ArrayList<>();
        evidence.add(qualifyAssemblyLineHistory());
        evidence.add(qualifyStatusHistory());
        evidence.add(qualifyGlobalHistory());
        evidence.add(qualifyRootLogs(dataSet));
        evidence.add(qualifyChildLogs(dataSet));
        evidence.add(qualifyAllRunLogs(dataSet));

        SqlPlanQualificationReport.write(new SqlPlanQualificationResult(
                dialect,
                ASSEMBLY_RUN_COUNT,
                STATION_LOGS_PER_RUN * 2,
                MAX_QUERY_MILLIS,
                indexEvidence,
                List.copyOf(evidence)));
    }

    private SqlQueryEvidence qualifyAssemblyLineHistory() {
        PageRequest page = PageRequest.first(50);
        String sql = render(DatabaseAssemblyRunSql.selectAssemblyRunsByAssemblyLineId(dialect),
                            parameters(page, quoted(TARGET_ASSEMBLY_LINE)));
        return qualify("assembly-line history", "idx_ar_assembly_line_start", sql,
                       () -> repository.findByAssemblyLineId(TARGET_ASSEMBLY_LINE, page).size());
    }

    private SqlQueryEvidence qualifyStatusHistory() {
        PageRequest page = PageRequest.first(50);
        String sql = render(DatabaseAssemblyRunSql.selectAssemblyRunsByStatus(dialect),
                            parameters(page, quoted(ExecutionStatus.FAILED.name())));
        return qualify("status history", "idx_ar_status_start", sql,
                       () -> repository.findByStatus(ExecutionStatus.FAILED, page).size());
    }

    private SqlQueryEvidence qualifyGlobalHistory() {
        PageRequest page = PageRequest.first(50);
        String sql = render(DatabaseAssemblyRunSql.selectAllAssemblyRuns(dialect), pageLiterals(page));
        return qualify("global history", "idx_ar_start", sql, () -> repository.findAll(page).size());
    }

    private SqlQueryEvidence qualifyRootLogs(DataSet dataSet) {
        PageRequest page = PageRequest.first(50);
        String sql = render(DatabaseAssemblyRunSql.selectRootStationLogsByRunId(dialect),
                            parameters(page, quoted(dataSet.targetRunId())));
        return qualify("root station logs", "idx_station_log_exec_parent", sql,
                       () -> repository.findRootLogsByRunId(dataSet.targetRunId(), page).size());
    }

    private SqlQueryEvidence qualifyChildLogs(DataSet dataSet) {
        PageRequest page = PageRequest.first(50);
        String sql = render(DatabaseAssemblyRunSql.selectChildStationLogsByRunId(dialect),
                            parameters(page, quoted(dataSet.targetRunId()), quoted(dataSet.targetParentLogId())));
        return qualify("child station logs", "idx_station_log_exec_parent", sql,
                       () -> repository.findChildLogsByRunId(dataSet.targetRunId(), dataSet.targetParentLogId(), page)
                               .size());
    }

    private SqlQueryEvidence qualifyAllRunLogs(DataSet dataSet) {
        PageRequest page = PageRequest.first(50);
        String sql = render(DatabaseAssemblyRunSql.selectAllStationLogsByRunId(dialect),
                            parameters(page, quoted(dataSet.targetRunId())));
        return qualify("all station logs", "idx_station_log_run_start", sql,
                       () -> repository.findAllLogsByRunId(dataSet.targetRunId(), page).size());
    }

    private SqlQueryEvidence qualify(String name, String referenceIndex, String sql, IntSupplier query) {
        int repositoryRows = query.getAsInt();
        requireRows(name, repositoryRows);
        QueryMeasurement measurement = measure(name, sql);
        if (repositoryRows != measurement.returnedRows()) {
            throw new AssertionError(name + " repository/result measurement mismatch: repository="
                    + repositoryRows + ", measured=" + measurement.returnedRows());
        }

        long maximum = Arrays.stream(measurement.samples()).max().orElseThrow();
        if (maximum >= TimeUnit.MILLISECONDS.toNanos(MAX_QUERY_MILLIS)) {
            throw new AssertionError(name + " exceeded the portable " + MAX_QUERY_MILLIS
                    + " ms catastrophic-regression ceiling on " + dialect + ": "
                    + nanosToMillis(maximum) + " ms");
        }

        String planMode = planMode();
        String plan = explain(name, sql);
        SqlPlanObservation planObservation = SqlPlanObservation.inspect(dialect, referenceIndex, plan);
        return new SqlQueryEvidence(name, referenceIndex, planMode, measurement.returnedRows(),
                percentileMillis(measurement.samples(), 0.50), percentileMillis(measurement.samples(), 0.95),
                nanosToMillis(maximum), planObservation.referenceIndexSelected(),
                planObservation.fullScanObserved(), plan);
    }

    private QueryMeasurement measure(String name, String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            for (int index = 0; index < WARMUP_COUNT; index++) {
                requireRows(name, executeAndCountRows(statement, sql));
            }

            long[] samples = new long[SAMPLE_COUNT];
            int returnedRows = 0;
            for (int index = 0; index < SAMPLE_COUNT; index++) {
                long startedAt = System.nanoTime();
                returnedRows = executeAndCountRows(statement, sql);
                samples[index] = System.nanoTime() - startedAt;
                requireRows(name, returnedRows);
            }
            return new QueryMeasurement(returnedRows, samples);
        } catch (SQLException exception) {
            throw new AssertionError("Failed to measure " + name + " on " + dialect, exception);
        }
    }

    private static int executeAndCountRows(Statement statement, String sql) throws SQLException {
        int rows = 0;
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows++;
            }
        }
        return rows;
    }

    private DataSet seedRepresentativeData() {
        UUID targetRunId = deterministicUuid("gear4j-plan-target-run");
        UUID noiseRunId = deterministicUuid("gear4j-plan-noise-run");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertAssemblyRuns(connection, targetRunId, noiseRunId);
                UUID targetParentLogId = insertStationLogs(connection, targetRunId, "target");
                insertStationLogs(connection, noiseRunId, "noise");
                connection.commit();
                return new DataSet(targetRunId, targetParentLogId);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new AssertionError("Failed to seed SQL-plan qualification data for " + dialect, exception);
        }
    }

    private void insertAssemblyRuns(Connection connection, UUID targetRunId, UUID noiseRunId) throws SQLException {
        String sql = "INSERT INTO assembly_run (id, assembly_line_id, status, start_time) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int pending = 0;
            for (int index = 0; index < ASSEMBLY_RUN_COUNT; index++) {
                UUID runId = index == 0 ? targetRunId
                        : index == 1 ? noiseRunId : deterministicUuid("gear4j-plan-run-" + index);
                String assemblyLineId = index % 200 == 0 ? TARGET_ASSEMBLY_LINE
                        : "plan-noise-" + index % 200;
                ExecutionStatus status = index % 100 == 0 ? ExecutionStatus.FAILED : ExecutionStatus.SUCCEEDED;
                dialect.setUuid(statement, 1, runId);
                statement.setString(2, assemblyLineId);
                statement.setString(3, status.name());
                dialect.setInstant(statement, 4, BASE_TIME.plusMillis(index / 4));
                statement.addBatch();
                pending = executeBatchIfFull(statement, pending + 1);
            }
            executePendingBatch(statement, pending);
        }
    }

    private UUID insertStationLogs(Connection connection, UUID runId, String label) throws SQLException {
        String sql = "INSERT INTO station_log (id, assembly_line_execution_id, operation_id, parent_log_id, "
                + "status, start_time) VALUES (?,?,?,?,?,?)";
        List<UUID> rootIds = new ArrayList<>(ROOT_LOGS_PER_RUN);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int pending = 0;
            for (int index = 0; index < ROOT_LOGS_PER_RUN; index++) {
                UUID logId = deterministicUuid("gear4j-plan-" + label + "-root-" + index);
                rootIds.add(logId);
                bindStationLog(statement, runId, logId, "root-" + index, null, index);
                statement.addBatch();
                pending = executeBatchIfFull(statement, pending + 1);
            }
            executePendingBatch(statement, pending);

            pending = 0;
            for (int index = ROOT_LOGS_PER_RUN; index < STATION_LOGS_PER_RUN; index++) {
                UUID logId = deterministicUuid("gear4j-plan-" + label + "-child-" + index);
                UUID parentId = rootIds.get((index - ROOT_LOGS_PER_RUN) % ROOT_LOGS_PER_RUN);
                bindStationLog(statement, runId, logId, "child-" + index, parentId, index);
                statement.addBatch();
                pending = executeBatchIfFull(statement, pending + 1);
            }
            executePendingBatch(statement, pending);
        }
        return rootIds.get(0);
    }

    private void bindStationLog(PreparedStatement statement,
                                UUID runId,
                                UUID logId,
                                String operationId,
                                UUID parentLogId,
                                int index)
            throws SQLException {
        dialect.setUuid(statement, 1, logId);
        dialect.setUuid(statement, 2, runId);
        statement.setString(3, operationId);
        dialect.setUuid(statement, 4, parentLogId);
        statement.setString(5, "SUCCEEDED");
        dialect.setInstant(statement, 6, BASE_TIME.plusNanos(index / 4 * 1_000L));
    }

    private static int executeBatchIfFull(PreparedStatement statement, int pending) throws SQLException {
        if (pending < BATCH_SIZE) {
            return pending;
        }
        statement.executeBatch();
        return 0;
    }

    private static void executePendingBatch(PreparedStatement statement, int pending) throws SQLException {
        if (pending > 0) {
            statement.executeBatch();
        }
    }

    private void refreshOptimizerStatistics() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            switch (dialect) {
                case POSTGRESQL -> {
                    statement.execute("ANALYZE assembly_run");
                    statement.execute("ANALYZE station_log");
                }
                case MYSQL, MARIADB -> statement.execute("ANALYZE TABLE assembly_run, station_log");
                case ORACLE -> statement.execute("BEGIN "
                        + "DBMS_STATS.GATHER_TABLE_STATS(USER, 'ASSEMBLY_RUN', cascade => TRUE); "
                        + "DBMS_STATS.GATHER_TABLE_STATS(USER, 'STATION_LOG', cascade => TRUE); END;");
                case H2 -> throw new IllegalArgumentException("H2 is not a production SQL-plan qualification dialect");
            }
        } catch (SQLException exception) {
            throw new AssertionError("Failed to refresh optimizer statistics for " + dialect, exception);
        }
    }

    private String explain(String name, String sql) {
        return switch (dialect) {
            case POSTGRESQL -> queryPlan("EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) " + sql);
            case MYSQL -> queryPlan("EXPLAIN ANALYZE FORMAT=TREE " + sql);
            case MARIADB -> queryPlan("ANALYZE FORMAT=JSON " + sql);
            case ORACLE -> oraclePlan(name, sql);
            case H2 -> throw new IllegalArgumentException("H2 is not a production SQL-plan qualification dialect");
        };
    }

    private String oraclePlan(String name, String sql) {
        String statementId = "G4J" + deterministicUuid(name).toString().replace("-", "").substring(0, 24);
        String deletePlan = "DELETE FROM PLAN_TABLE WHERE STATEMENT_ID = '" + statementId + "'";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            statement.executeUpdate(deletePlan);
            try {
                statement.executeUpdate("EXPLAIN PLAN SET STATEMENT_ID = '" + statementId + "' FOR " + sql);
                try (PreparedStatement display = connection.prepareStatement(
                                                                             "SELECT PLAN_TABLE_OUTPUT FROM TABLE("
                                                                                     + "DBMS_XPLAN.DISPLAY('PLAN_TABLE', ?, 'BASIC +PREDICATE'))")) {
                    display.setQueryTimeout(30);
                    display.setString(1, statementId);
                    return readRows(display.executeQuery());
                }
            } finally {
                statement.executeUpdate(deletePlan);
            }
        } catch (SQLException exception) {
            throw new AssertionError("Failed to explain " + name + " on " + dialect, exception);
        }
    }

    private String queryPlan(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            return readRows(statement.executeQuery(sql));
        } catch (SQLException exception) {
            throw new AssertionError("Failed to execute plan qualification on " + dialect + ": " + sql, exception);
        }
    }

    private static String readRows(ResultSet resultSet) throws SQLException {
        StringBuilder output = new StringBuilder();
        ResultSetMetaData metadata = resultSet.getMetaData();
        while (resultSet.next()) {
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                if (column > 1) {
                    output.append('\t');
                }
                output.append(resultSet.getString(column));
            }
            output.append(System.lineSeparator());
        }
        return output.toString();
    }

    private String planMode() {
        return switch (dialect) {
            case POSTGRESQL -> "EXPLAIN ANALYZE";
            case MYSQL -> "EXPLAIN ANALYZE FORMAT=TREE";
            case MARIADB -> "ANALYZE FORMAT=JSON";
            case ORACLE -> "EXPLAIN PLAN plus timed repository execution";
            case H2 -> "not qualified";
        };
    }

    private List<String> pageLiterals(PageRequest page) {
        if (dialect == Gear4jDatabaseDialect.ORACLE) {
            return List.of(Integer.toString(page.offset()), Integer.toString(page.limit()));
        }
        return List.of(Integer.toString(page.limit()), Integer.toString(page.offset()));
    }

    private List<String> parameters(PageRequest page, String... leadingParameters) {
        List<String> parameters = new ArrayList<>(Arrays.asList(leadingParameters));
        parameters.addAll(pageLiterals(page));
        return parameters;
    }

    private static String render(String sql, List<String> parameters) {
        StringBuilder rendered = new StringBuilder();
        int parameterIndex = 0;
        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (character == '?') {
                if (parameterIndex >= parameters.size()) {
                    throw new IllegalArgumentException("Missing SQL plan parameter for " + sql);
                }
                rendered.append(parameters.get(parameterIndex++));
            } else {
                rendered.append(character);
            }
        }
        if (parameterIndex != parameters.size()) {
            throw new IllegalArgumentException("Unused SQL plan parameters for " + sql);
        }
        return rendered.toString();
    }

    private static String quoted(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String quoted(UUID value) {
        return quoted(value.toString());
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireRows(String name, int returnedRows) {
        if (returnedRows <= 0) {
            throw new AssertionError(name + " returned no representative rows");
        }
    }

    private static double percentileMillis(long[] samples, double percentile) {
        long[] ordered = samples.clone();
        Arrays.sort(ordered);
        int index = Math.max(0, (int) Math.ceil(ordered.length * percentile) - 1);
        return nanosToMillis(ordered[index]);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record DataSet(UUID targetRunId, UUID targetParentLogId) {}

    private record QueryMeasurement(int returnedRows, long[] samples) {}
}
