package io.github.gear4jtest.core.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

final class AssemblyRunRecordRowMapper {
    private final Gear4jDatabaseDialect dialect;
    private final DatabasePersistenceJsonCodec jsonCodec;

    AssemblyRunRecordRowMapper(Gear4jDatabaseDialect dialect, DatabasePersistenceJsonCodec jsonCodec) {
        this.dialect = dialect;
        this.jsonCodec = jsonCodec;
    }

    AssemblyRunRecord map(ResultSet rs) throws SQLException {
        return new AssemblyRunRecord(
                dialect.getUuid(rs, "id"),
                rs.getString("pipeline_id"),
                jsonCodec.fromJson(dialect.getJson(rs, "context"), new TypeReference<>() {}),
                jsonCodec.fromJson(dialect.getJson(rs, "input_parameters"), Map.class),
                jsonCodec.fromJson(dialect.getJson(rs, "result"), Object.class),
                ExecutionStatus.valueOf(rs.getString("status")),
                dialect.getInstant(rs, "start_time"),
                dialect.getInstant(rs, "end_time"),
                rs.getString("error_message"),
                dialect.getUuid(rs, "parent_execution_id"),
                dialect.getUuid(rs, "root_execution_id"),
                dialect.getUuid(rs, "parent_station_log_id"));
    }
}
