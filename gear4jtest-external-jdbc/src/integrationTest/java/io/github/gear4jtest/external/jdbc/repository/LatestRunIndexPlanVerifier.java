package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;

import static org.assertj.core.api.Assertions.assertThat;

final class LatestRunIndexPlanVerifier {
    private static final String INDEX_NAME = "idx_op_chain_latest_run";
    private static final int ASSEMBLY_LINE_COUNT = 20;
    private static final int ROWS_PER_ASSEMBLY_LINE = 1_000;
    private static final int RUN_ROWS_PER_ASSEMBLY_LINE = 10;
    private static final int BATCH_SIZE = 1_000;
    private static final int WARMUP_QUERY_COUNT = 10;
    private static final int MEASURED_QUERY_COUNT = 50;
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final String HASH = "e".repeat(64);

    private LatestRunIndexPlanVerifier() {
    }

    static Evidence verify(DataSource dataSource, Gear4jDatabaseDialect dialect, String scenarioId)
            throws SQLException {
        String targetAssemblyLineId = "latest-plan-" + scenarioId + "-0";
        insertRepresentativeHistory(dataSource, dialect, scenarioId);
        refreshOptimizerStatistics(dataSource, dialect);

        String indexDefinition;
        String explainPlan;
        try (Connection connection = dataSource.getConnection()) {
            indexDefinition = verifyIndexDefinition(connection, dialect);
            explainPlan = explainLatestRun(connection, dialect, targetAssemblyLineId);
        }
        assertThat(explainPlan.toLowerCase(Locale.ROOT))
                .as("findLatestRun EXPLAIN plan for %s", dialect)
                .contains(INDEX_NAME);

        OperationChainObject latest = OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(dialect)
                .build()
                .findLatestRun(targetAssemblyLineId)
                .orElseThrow();
        assertThat(latest.mode()).isEqualTo(ExecutionMode.RUN);
        assertThat(latest.version()).isEqualTo("run-0009");

        long averageQueryNanos = measureLatestRunQuery(dataSource, dialect, targetAssemblyLineId);
        return new Evidence(ASSEMBLY_LINE_COUNT * ROWS_PER_ASSEMBLY_LINE, indexDefinition, explainPlan,
                averageQueryNanos);
    }

    private static void insertRepresentativeHistory(DataSource dataSource,
                                                    Gear4jDatabaseDialect dialect,
                                                    String scenarioId)
            throws SQLException {
        String sql = "INSERT INTO operation_chain_object "
                + "(al_id, version, publication_mode, content_hash, size_bytes, published_at) "
                + "VALUES (?,?,?,?,?,?)";
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int pendingBatchRows = 0;
                for (int assemblyLine = 0; assemblyLine < ASSEMBLY_LINE_COUNT; assemblyLine++) {
                    String assemblyLineId = "latest-plan-" + scenarioId + "-" + assemblyLine;
                    for (int row = 0; row < ROWS_PER_ASSEMBLY_LINE; row++) {
                        boolean run = row < RUN_ROWS_PER_ASSEMBLY_LINE;
                        statement.setString(1, assemblyLineId);
                        statement.setString(2, (run ? "run-" : "test-") + "%04d".formatted(row));
                        ExternalRepositorySqlDialect.bindExecutionMode(
                                                                       dialect,
                                                                       statement,
                                                                       3,
                                                                       run ? ExecutionMode.RUN
                                                                               : ExecutionMode.TEST);
                        statement.setString(4, HASH);
                        statement.setLong(5, 42L);
                        long publishedOffset = run ? row : ROWS_PER_ASSEMBLY_LINE + row;
                        statement.setTimestamp(6, Timestamp.from(BASE_TIME.plusSeconds(publishedOffset)));
                        statement.addBatch();
                        pendingBatchRows++;
                        if (pendingBatchRows == BATCH_SIZE) {
                            statement.executeBatch();
                            pendingBatchRows = 0;
                        }
                    }
                }
                if (pendingBatchRows > 0) {
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static void refreshOptimizerStatistics(DataSource dataSource, Gear4jDatabaseDialect dialect)
            throws SQLException {
        String sql = switch (dialect) {
            case POSTGRESQL -> "ANALYZE operation_chain_object";
            case MYSQL, MARIADB -> "ANALYZE TABLE operation_chain_object";
            case ORACLE ->
                "BEGIN DBMS_STATS.GATHER_TABLE_STATS(USER, 'OPERATION_CHAIN_OBJECT', cascade => TRUE); END;";
            case H2 -> "ANALYZE";
        };
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String verifyIndexDefinition(Connection connection, Gear4jDatabaseDialect dialect)
            throws SQLException {
        return switch (dialect) {
            case POSTGRESQL -> verifyPostgresqlIndex(connection);
            case MYSQL, MARIADB -> verifyMySqlFamilyIndex(connection);
            case ORACLE -> verifyOracleIndex(connection);
            case H2 -> verifyH2Index(connection);
        };
    }

    private static String verifyPostgresqlIndex(Connection connection) throws SQLException {
        String definition = queryText(connection,
                                      "SELECT indexdef FROM pg_indexes "
                                              + "WHERE schemaname=current_schema() "
                                              + "AND tablename='operation_chain_object' "
                                              + "AND indexname='" + INDEX_NAME + "'");
        assertThat(definition)
                .contains("al_id", "published_at DESC", "id DESC", "publication_mode", "RUN")
                .doesNotContain("(al_id, publication_mode");
        return definition;
    }

    private static String verifyMySqlFamilyIndex(Connection connection) throws SQLException {
        String definition = queryText(connection,
                                      "SELECT column_name, collation FROM information_schema.statistics "
                                              + "WHERE table_schema=DATABASE() "
                                              + "AND table_name='operation_chain_object' "
                                              + "AND index_name='" + INDEX_NAME + "' "
                                              + "ORDER BY seq_in_index");
        assertIndexColumns(definition, "al_id", "publication_mode", "published_at", "id");
        assertThat(definition.toLowerCase(Locale.ROOT))
                .contains("published_at|d", "id|d");
        return definition;
    }

    private static String verifyOracleIndex(Connection connection) throws SQLException {
        List<OracleIndexColumn> columns = queryOracleIndexColumns(connection);
        assertThat(columns)
                .extracting(OracleIndexColumn::logicalName)
                .containsExactly("al_id", "publication_mode", "published_at", "id");
        assertThat(columns)
                .extracting(OracleIndexColumn::direction)
                .containsExactly("ASC", "ASC", "DESC", "DESC");
        return String.join(System.lineSeparator(),
                           columns.stream().map(OracleIndexColumn::definition).toList());
    }

    private static List<OracleIndexColumn> queryOracleIndexColumns(Connection connection) throws SQLException {
        String sql = "SELECT c.column_position, c.column_name, c.descend, e.column_expression "
                + "FROM user_ind_columns c "
                + "LEFT JOIN user_ind_expressions e "
                + "ON e.index_name=c.index_name "
                + "AND e.table_name=c.table_name "
                + "AND e.column_position=c.column_position "
                + "WHERE c.index_name=UPPER('" + INDEX_NAME + "') "
                + "ORDER BY c.column_position";
        try (var statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            List<OracleIndexColumn> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(new OracleIndexColumn(resultSet.getInt(1),
                        resultSet.getString(2), resultSet.getString(3), resultSet.getString(4)));
            }
            return List.copyOf(columns);
        }
    }

    private static String verifyH2Index(Connection connection) throws SQLException {
        String definition = queryText(connection,
                                      "SELECT column_name, ordering_specification "
                                              + "FROM information_schema.index_columns "
                                              + "WHERE table_name='OPERATION_CHAIN_OBJECT' "
                                              + "AND index_name=UPPER('" + INDEX_NAME + "') "
                                              + "ORDER BY ordinal_position");
        assertIndexColumns(definition, "al_id", "publication_mode", "published_at", "id");
        return definition;
    }

    private static void assertIndexColumns(String definition, String... expectedColumns) {
        List<String> actualColumns = definition.lines()
                .filter(line -> !line.isBlank())
                .map(line -> line.substring(0, line.indexOf('|')).toLowerCase(Locale.ROOT))
                .toList();
        assertThat(actualColumns).containsExactly(expectedColumns);
    }

    private static String explainLatestRun(Connection connection,
                                           Gear4jDatabaseDialect dialect,
                                           String assemblyLineId)
            throws SQLException {
        String query = latestRunQuery(dialect, assemblyLineId);
        return switch (dialect) {
            case POSTGRESQL -> queryText(connection, "EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) " + query);
            case MYSQL -> queryText(connection, "EXPLAIN ANALYZE " + query);
            case MARIADB -> queryText(connection, "ANALYZE FORMAT=JSON " + query);
            case ORACLE -> explainOracle(connection, query);
            case H2 -> queryText(connection, "EXPLAIN ANALYZE " + query);
        };
    }

    private static String explainOracle(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM plan_table WHERE statement_id='GEAR4J_R11'");
            statement.execute("EXPLAIN PLAN SET STATEMENT_ID='GEAR4J_R11' FOR " + query);
        }
        return queryText(connection,
                         "SELECT plan_table_output FROM TABLE("
                                 + "DBMS_XPLAN.DISPLAY('PLAN_TABLE', 'GEAR4J_R11', 'BASIC +PREDICATE'))");
    }

    private static String latestRunQuery(Gear4jDatabaseDialect dialect, String assemblyLineId) {
        String orderedSql = "SELECT id, al_id, version, publication_mode, content_hash, size_bytes, mime_type, "
                + "created_at, created_by, published_at FROM operation_chain_object "
                + "WHERE al_id='" + assemblyLineId + "' AND publication_mode='RUN' "
                + "ORDER BY published_at DESC, id DESC";
        return dialect == Gear4jDatabaseDialect.ORACLE
                ? orderedSql + " OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"
                : orderedSql + " LIMIT 1 OFFSET 0";
    }

    private static long measureLatestRunQuery(DataSource dataSource,
                                              Gear4jDatabaseDialect dialect,
                                              String assemblyLineId)
            throws SQLException {
        String orderedSql = "SELECT id FROM operation_chain_object "
                + "WHERE al_id=? AND publication_mode='RUN' ORDER BY published_at DESC, id DESC";
        String sql = ExternalRepositorySqlDialect.pagedSql(dialect, orderedSql);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, assemblyLineId);
            ExternalRepositorySqlDialect.bindPage(dialect, statement, 2, PageRequest.first(1));
            executeMeasuredQuery(statement, WARMUP_QUERY_COUNT);
            long start = System.nanoTime();
            executeMeasuredQuery(statement, MEASURED_QUERY_COUNT);
            return (System.nanoTime() - start) / MEASURED_QUERY_COUNT;
        }
    }

    private static void executeMeasuredQuery(PreparedStatement statement, int iterations) throws SQLException {
        for (int iteration = 0; iteration < iterations; iteration++) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("findLatestRun measurement query returned no row");
                }
            }
        }
    }

    private static String queryText(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            int columnCount = resultSet.getMetaData().getColumnCount();
            List<String> lines = new ArrayList<>();
            while (resultSet.next()) {
                var line = new StringBuilder();
                for (int column = 1; column <= columnCount; column++) {
                    if (column > 1) {
                        line.append('|');
                    }
                    line.append(resultSet.getString(column));
                }
                lines.add(line.toString());
            }
            return String.join(System.lineSeparator(), lines);
        }
    }

    record Evidence(int rowCount, String indexDefinition, String explainPlan, long averageQueryNanos) {
        String report() {
            return "rows=" + rowCount + System.lineSeparator()
                    + "average_query_nanos=" + averageQueryNanos + System.lineSeparator()
                    + "index=" + indexDefinition + System.lineSeparator()
                    + "plan=" + explainPlan;
        }
    }

    private record OracleIndexColumn(int position, String physicalName, String direction, String expression) {
        String logicalName() {
            if (expression == null) {
                return physicalName.toLowerCase(Locale.ROOT);
            }
            String logicalName = expression.trim();
            String descendingFunction = "SYS_OP_DESCEND(";
            if (logicalName.regionMatches(true, 0, descendingFunction, 0, descendingFunction.length())
                    && logicalName.endsWith(")")) {
                logicalName = logicalName.substring(descendingFunction.length(), logicalName.length() - 1);
            }
            return logicalName.replace("\"", "").toLowerCase(Locale.ROOT);
        }

        String definition() {
            return position + "|" + physicalName + "|" + direction + "|"
                    + (expression == null ? "" : expression);
        }
    }
}
