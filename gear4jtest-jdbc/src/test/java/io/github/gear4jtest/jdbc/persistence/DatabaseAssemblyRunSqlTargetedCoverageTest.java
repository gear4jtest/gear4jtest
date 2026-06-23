package io.github.gear4jtest.jdbc.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseAssemblyRunSqlTargetedCoverageTest {
    @Test
    void assemblyRunSql_shouldRenderJsonPlaceholdersAccordingToDialect() {
        assertThat(DatabaseAssemblyRunSql.insertAssemblyRun(Gear4jDatabaseDialect.H2))
                .contains("? FORMAT JSON,? FORMAT JSON,? FORMAT JSON");
        assertThat(DatabaseAssemblyRunSql.updateAssemblyRun(Gear4jDatabaseDialect.POSTGRESQL))
                .contains("context=?, result=?")
                .doesNotContain("FORMAT JSON");
    }

    @Test
    void pagedQueries_shouldDelegateToDialectPaginationSyntax() {
        assertThat(DatabaseAssemblyRunSql.selectAllAssemblyRuns(Gear4jDatabaseDialect.ORACLE))
                .endsWith("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        assertThat(DatabaseAssemblyRunSql.selectRootStationLogsByRunId(Gear4jDatabaseDialect.POSTGRESQL))
                .endsWith("LIMIT ? OFFSET ?");
        assertThat(DatabaseAssemblyRunSql.selectChildStationLogsByRunId(Gear4jDatabaseDialect.H2))
                .contains("parent_log_id = ?")
                .endsWith("LIMIT ? OFFSET ?");
        assertThat(DatabaseAssemblyRunSql.selectAllStationLogsByRunId(Gear4jDatabaseDialect.MYSQL))
                .contains("ORDER BY start_time, id")
                .endsWith("LIMIT ? OFFSET ?");
    }

    @Test
    void stationLogSql_shouldRenderUpsertAndRejectUnsupportedNativeUpsertDialects() {
        assertThat(DatabaseAssemblyRunSql.insertStationLog(Gear4jDatabaseDialect.H2))
                .contains("? FORMAT JSON");
        assertThat(DatabaseAssemblyRunSql.updateOpenStationLog(Gear4jDatabaseDialect.H2))
                .contains("context=? FORMAT JSON")
                .contains("end_time IS NULL");
        assertThat(DatabaseAssemblyRunSql.upsertStationLog(Gear4jDatabaseDialect.POSTGRESQL))
                .contains("ON CONFLICT (id) DO UPDATE")
                .contains("WHERE station_log.end_time IS NULL");
        assertThat(DatabaseAssemblyRunSql.upsertStationLog(Gear4jDatabaseDialect.MARIADB))
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("IF(end_time IS NULL");

        assertThatThrownBy(() -> DatabaseAssemblyRunSql.upsertStationLog(Gear4jDatabaseDialect.ORACLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Native station-log upsert is not supported for Oracle");
    }

    @Test
    void deleteAndCountSql_shouldExposeStableStatements() {
        assertThat(DatabaseAssemblyRunSql.selectAssemblyRunById())
                .startsWith("SELECT id, assembly_line_id")
                .endsWith("WHERE id = ?");
        assertThat(DatabaseAssemblyRunSql.deleteStationLogsByRunId())
                .isEqualTo("DELETE FROM station_log WHERE assembly_line_execution_id = ?");
        assertThat(DatabaseAssemblyRunSql.deleteAssemblyRunById())
                .isEqualTo("DELETE FROM assembly_run WHERE id = ?");
        assertThat(DatabaseAssemblyRunSql.countRootStationLogsByRunId())
                .contains("parent_log_id IS NULL");
        assertThat(DatabaseAssemblyRunSql.countChildStationLogsByRunId())
                .contains("parent_log_id = ?");
    }
}
