package io.github.gear4jtest.core.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.gear4jtest.core.model.StationLogStatus;

final class StationLogRecordRowMapper {
    private final Gear4jDatabaseDialect dialect;
    private final DatabasePersistenceJsonCodec jsonCodec;

    StationLogRecordRowMapper(Gear4jDatabaseDialect dialect, DatabasePersistenceJsonCodec jsonCodec) {
        this.dialect = dialect;
        this.jsonCodec = jsonCodec;
    }

    StationLogRecord map(ResultSet rs) throws SQLException {
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
                jsonCodec.fromJson(dialect.getJson(rs, "context"), new TypeReference<>() {}),
                rs.getString("item_id"));
    }
}
