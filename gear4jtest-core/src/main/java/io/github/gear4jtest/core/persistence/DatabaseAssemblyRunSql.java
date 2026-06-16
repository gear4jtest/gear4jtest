package io.github.gear4jtest.core.persistence;

final class DatabaseAssemblyRunSql {
    static final String ASSEMBLY_RUN_COLUMNS = "id, pipeline_id, input_parameters, context, result, "
            + "status, start_time, end_time, error_message, parent_execution_id, root_execution_id, "
            + "parent_station_log_id";
    static final String STATION_LOG_COLUMNS = "id, pipeline_execution_id, operation_id, parent_log_id, "
            + "branch_id, status, start_time, end_time, error_message, error_handler_messages, context, item_id";

    private DatabaseAssemblyRunSql() {
    }

    static String insertAssemblyRun() {
        return "INSERT INTO assembly_run (id, pipeline_id, input_parameters, context, result, "
                + "status, start_time, end_time, error_message, parent_execution_id, root_execution_id, "
                + "parent_station_log_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    static String updateAssemblyRun() {
        return "UPDATE assembly_run SET context=?, result=?, status=?, end_time=?, error_message=?, "
                + "parent_execution_id=?, root_execution_id=?, parent_station_log_id=? WHERE id=?";
    }

    static String selectAssemblyRunById() {
        return "SELECT " + ASSEMBLY_RUN_COLUMNS + " FROM assembly_run WHERE id = ?";
    }

    static String selectAssemblyRunsByPipelineId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql("SELECT " + ASSEMBLY_RUN_COLUMNS
                + " FROM assembly_run WHERE pipeline_id = ? ORDER BY start_time DESC");
    }

    static String selectAssemblyRunsByStatus(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql("SELECT " + ASSEMBLY_RUN_COLUMNS
                + " FROM assembly_run WHERE status = ? ORDER BY start_time DESC");
    }

    static String selectAllAssemblyRuns(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql("SELECT " + ASSEMBLY_RUN_COLUMNS + " FROM assembly_run ORDER BY start_time DESC");
    }

    static String deleteStationLogsByRunId() {
        return "DELETE FROM station_log WHERE pipeline_execution_id = ?";
    }

    static String deleteAssemblyRunById() {
        return "DELETE FROM assembly_run WHERE id = ?";
    }

    static String selectRootStationLogsByRunId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql("SELECT " + STATION_LOG_COLUMNS
                + " FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id IS NULL "
                + "ORDER BY start_time, id");
    }

    static String selectChildStationLogsByRunId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql("SELECT " + STATION_LOG_COLUMNS
                + " FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id = ? "
                + "ORDER BY start_time, id");
    }

    static String selectAllStationLogsByRunId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql("SELECT " + STATION_LOG_COLUMNS
                + " FROM station_log WHERE pipeline_execution_id = ? ORDER BY start_time, id");
    }

    static String countRootStationLogsByRunId() {
        return "SELECT COUNT(*) FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id IS NULL";
    }

    static String countChildStationLogsByRunId() {
        return "SELECT COUNT(*) FROM station_log WHERE pipeline_execution_id = ? AND parent_log_id = ?";
    }

    static String updateOpenStationLog() {
        return "UPDATE station_log SET branch_id=?, status=?, end_time=?, error_message=?, "
                + "error_handler_messages=?, context=?, item_id=? WHERE id=? AND end_time IS NULL";
    }

    static String insertStationLog() {
        return "INSERT INTO station_log (id, pipeline_execution_id, operation_id, parent_log_id, branch_id, "
                + "status, start_time, end_time, error_message, error_handler_messages, context, item_id) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    }
}
