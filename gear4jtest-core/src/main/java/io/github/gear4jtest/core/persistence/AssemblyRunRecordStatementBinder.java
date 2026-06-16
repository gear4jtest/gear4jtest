package io.github.gear4jtest.core.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;

final class AssemblyRunRecordStatementBinder {
    private final Gear4jDatabaseDialect dialect;
    private final DatabasePersistenceJsonCodec jsonCodec;

    AssemblyRunRecordStatementBinder(Gear4jDatabaseDialect dialect, DatabasePersistenceJsonCodec jsonCodec) {
        this.dialect = dialect;
        this.jsonCodec = jsonCodec;
    }

    void bindInsert(PreparedStatement stmt, AssemblyRunRecord execution) throws SQLException {
        dialect.setUuid(stmt, 1, execution.id());
        stmt.setString(2, execution.pipelineId());
        dialect.setJson(stmt, 3, jsonCodec.toJson(execution.inputParams()));
        dialect.setJson(stmt, 4, jsonCodec.toJson(execution.context()));
        dialect.setJson(stmt, 5, jsonCodec.toJson(execution.result()));
        stmt.setString(6, execution.status().name());
        dialect.setInstant(stmt, 7, execution.startTime());
        dialect.setInstant(stmt, 8, execution.endTime());
        stmt.setString(9, execution.errorMessage());
        dialect.setUuid(stmt, 10, execution.parentExecutionId());
        dialect.setUuid(stmt, 11, execution.rootExecutionId());
        dialect.setUuid(stmt, 12, execution.parentStationLogId());
    }

    void bindUpdate(PreparedStatement stmt, AssemblyRunRecord execution) throws SQLException {
        dialect.setJson(stmt, 1, jsonCodec.toJson(execution.context()));
        dialect.setJson(stmt, 2, jsonCodec.toJson(execution.result()));
        stmt.setString(3, execution.status().name());
        dialect.setInstant(stmt, 4, execution.endTime());
        stmt.setString(5, execution.errorMessage());
        dialect.setUuid(stmt, 6, execution.parentExecutionId());
        dialect.setUuid(stmt, 7, execution.rootExecutionId());
        dialect.setUuid(stmt, 8, execution.parentStationLogId());
        dialect.setUuid(stmt, 9, execution.id());
    }
}
