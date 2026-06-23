package io.github.gear4jtest.jdbc.persistence;

import java.util.List;

final class DatabaseAssemblyRunSql {
    private static final String SELECT = "SELECT ";

    static final List<String> ASSEMBLY_RUN_COLUMN_NAMES = List.of("id", "assembly_line_id", "input_parameters",
                                                                  "context", "result", "status", "start_time",
                                                                  "end_time", "error_message", "parent_execution_id",
                                                                  "root_execution_id", "parent_station_log_id");
    static final List<String> STATION_LOG_COLUMN_NAMES = List.of("id", "assembly_line_execution_id", "operation_id",
                                                                 "parent_log_id", "branch_id", "status", "start_time",
                                                                 "end_time", "error_message",
                                                                 "error_handler_messages", "context", "item_id");

    static final String ASSEMBLY_RUN_COLUMNS = String.join(", ", ASSEMBLY_RUN_COLUMN_NAMES);
    static final String STATION_LOG_COLUMNS = String.join(", ", STATION_LOG_COLUMN_NAMES);

    private DatabaseAssemblyRunSql() {
    }

    static String insertAssemblyRun(Gear4jDatabaseDialect dialect) {
        return "INSERT INTO assembly_run (" + ASSEMBLY_RUN_COLUMNS + ") VALUES (?,?," + jsonParameter(dialect)
                + "," + jsonParameter(dialect) + "," + jsonParameter(dialect) + ",?,?,?,?,?,?,?)";
    }

    static String updateAssemblyRun(Gear4jDatabaseDialect dialect) {
        return "UPDATE assembly_run SET context=" + jsonParameter(dialect) + ", result=" + jsonParameter(dialect)
                + ", status=?, end_time=?, error_message=?, parent_execution_id=?, root_execution_id=?, "
                + "parent_station_log_id=? WHERE id=?";
    }

    static String selectAssemblyRunById() {
        return SELECT + ASSEMBLY_RUN_COLUMNS + " FROM assembly_run WHERE id = ?";
    }

    static String selectAssemblyRunsByAssemblyLineId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql(SELECT + ASSEMBLY_RUN_COLUMNS
                + " FROM assembly_run WHERE assembly_line_id = ? ORDER BY start_time DESC, id DESC");
    }

    static String selectAssemblyRunsByStatus(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql(SELECT + ASSEMBLY_RUN_COLUMNS
                + " FROM assembly_run WHERE status = ? ORDER BY start_time DESC, id DESC");
    }

    static String selectAllAssemblyRuns(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql(SELECT + ASSEMBLY_RUN_COLUMNS
                + " FROM assembly_run ORDER BY start_time DESC, id DESC");
    }

    static String deleteStationLogsByRunId() {
        return "DELETE FROM station_log WHERE assembly_line_execution_id = ?";
    }

    static String deleteAssemblyRunById() {
        return "DELETE FROM assembly_run WHERE id = ?";
    }

    static String selectRootStationLogsByRunId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql(SELECT + STATION_LOG_COLUMNS
                + " FROM station_log WHERE assembly_line_execution_id = ? AND parent_log_id IS NULL "
                + "ORDER BY start_time, id");
    }

    static String selectChildStationLogsByRunId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql(SELECT + STATION_LOG_COLUMNS
                + " FROM station_log WHERE assembly_line_execution_id = ? AND parent_log_id = ? "
                + "ORDER BY start_time, id");
    }

    static String selectAllStationLogsByRunId(Gear4jDatabaseDialect dialect) {
        return dialect.pagedSql(SELECT + STATION_LOG_COLUMNS
                + " FROM station_log WHERE assembly_line_execution_id = ? ORDER BY start_time, id");
    }

    static String countRootStationLogsByRunId() {
        return SELECT + "COUNT(*) FROM station_log WHERE assembly_line_execution_id = ? AND parent_log_id IS NULL";
    }

    static String countChildStationLogsByRunId() {
        return SELECT + "COUNT(*) FROM station_log WHERE assembly_line_execution_id = ? AND parent_log_id = ?";
    }

    static String updateOpenStationLog(Gear4jDatabaseDialect dialect) {
        return "UPDATE station_log SET branch_id=?, status=?, end_time=?, error_message=?, "
                + "error_handler_messages=?, context=" + jsonParameter(dialect)
                + ", item_id=? WHERE id=? AND end_time IS NULL";
    }

    static String insertStationLog(Gear4jDatabaseDialect dialect) {
        return stationLogInsertBase(dialect);
    }

    static String upsertStationLog(Gear4jDatabaseDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL -> postgresqlUpsertStationLog();
            case MYSQL, MARIADB -> mysqlUpsertStationLog();
            case H2, ORACLE -> throw new IllegalArgumentException("Native station-log upsert is not supported for "
                    + dialect);
        };
    }

    private static String stationLogInsertBase(Gear4jDatabaseDialect dialect) {
        return "INSERT INTO station_log (" + STATION_LOG_COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,"
                + jsonParameter(dialect) + ",?)";
    }

    private static String jsonParameter(Gear4jDatabaseDialect dialect) {
        return dialect == Gear4jDatabaseDialect.H2 ? "? FORMAT JSON" : "?";
    }

    private static String postgresqlUpsertStationLog() {
        return stationLogInsertBase(Gear4jDatabaseDialect.POSTGRESQL)
                + " ON CONFLICT (id) DO UPDATE SET "
                + "branch_id = EXCLUDED.branch_id, "
                + "status = EXCLUDED.status, "
                + "end_time = EXCLUDED.end_time, "
                + "error_message = EXCLUDED.error_message, "
                + "error_handler_messages = EXCLUDED.error_handler_messages, "
                + "context = EXCLUDED.context, "
                + "item_id = EXCLUDED.item_id "
                + "WHERE station_log.end_time IS NULL";
    }

    private static String mysqlUpsertStationLog() {
        return stationLogInsertBase(Gear4jDatabaseDialect.MYSQL)
                + " ON DUPLICATE KEY UPDATE "
                + "branch_id = IF(end_time IS NULL, VALUES(branch_id), branch_id), "
                + "status = IF(end_time IS NULL, VALUES(status), status), "
                + "error_message = IF(end_time IS NULL, VALUES(error_message), error_message), "
                + "error_handler_messages = IF(end_time IS NULL, VALUES(error_handler_messages), "
                + "error_handler_messages), "
                + "context = IF(end_time IS NULL, VALUES(context), context), "
                + "item_id = IF(end_time IS NULL, VALUES(item_id), item_id), "
                + "end_time = IF(end_time IS NULL, VALUES(end_time), end_time)";
    }
}
