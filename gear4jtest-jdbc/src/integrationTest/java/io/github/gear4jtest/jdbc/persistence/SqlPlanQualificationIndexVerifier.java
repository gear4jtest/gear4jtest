package io.github.gear4jtest.jdbc.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.SqlPlanQualificationReport.SqlIndexEvidence;

/**
 * Verifies ordered-index definitions independently from optimizer plan choice.
 */
final class SqlPlanQualificationIndexVerifier {
    private static final List<ExpectedIndex> EXPECTED_INDEXES = List.of(
                                                                        new ExpectedIndex("assembly_run",
                                                                                "idx_ar_assembly_line_start",
                                                                                List.of("assembly_line_id",
                                                                                        "start_time", "id")),
                                                                        new ExpectedIndex("assembly_run",
                                                                                "idx_ar_status_start",
                                                                                List.of("status", "start_time", "id")),
                                                                        new ExpectedIndex("assembly_run",
                                                                                "idx_ar_start",
                                                                                List.of("start_time", "id")),
                                                                        new ExpectedIndex("station_log",
                                                                                "idx_station_log_exec_parent",
                                                                                List.of("assembly_line_execution_id",
                                                                                        "parent_log_id", "start_time",
                                                                                        "id")),
                                                                        new ExpectedIndex("station_log",
                                                                                "idx_station_log_run_start",
                                                                                List.of("assembly_line_execution_id",
                                                                                        "start_time", "id")));

    private SqlPlanQualificationIndexVerifier() {
    }

    static List<SqlIndexEvidence> verify(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            List<SqlIndexEvidence> evidence = new ArrayList<>();
            for (ExpectedIndex expected : EXPECTED_INDEXES) {
                List<String> actualColumns = indexColumns(connection, expected.table(), expected.name());
                if (!actualColumns.equals(expected.columns())) {
                    throw new AssertionError("Unexpected definition for " + expected.name() + ": expected="
                            + expected.columns() + ", actual=" + actualColumns);
                }
                evidence.add(new SqlIndexEvidence(expected.table(), expected.name(), actualColumns));
            }
            return List.copyOf(evidence);
        } catch (SQLException exception) {
            throw new AssertionError("Failed to inspect ordered SQL-plan qualification indexes", exception);
        }
    }

    private static List<String> indexColumns(Connection connection, String tableName, String indexName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : List.of(tableName, tableName.toLowerCase(Locale.ROOT),
                                        tableName.toUpperCase(Locale.ROOT))) {
            Map<Short, String> columnsByPosition = new TreeMap<>();
            try (ResultSet resultSet = metadata.getIndexInfo(null, null, candidate, false, false)) {
                while (resultSet.next()) {
                    String actualIndexName = resultSet.getString("INDEX_NAME");
                    String columnName = resultSet.getString("COLUMN_NAME");
                    if (actualIndexName != null && actualIndexName.equalsIgnoreCase(indexName)
                            && columnName != null) {
                        columnsByPosition.put(resultSet.getShort("ORDINAL_POSITION"),
                                              columnName.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (!columnsByPosition.isEmpty()) {
                return List.copyOf(columnsByPosition.values());
            }
        }
        return List.of();
    }

    private record ExpectedIndex(String table, String name, List<String> columns) {}
}
