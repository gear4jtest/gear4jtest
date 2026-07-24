package io.github.gear4jtest.jdbc.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.github.gear4jtest.core.persistence.AssemblyRunRecord;

final class AssemblyRunRecordStatementBinder {
    private final Gear4jDatabaseDialect dialect;
    private final PersistenceJsonCodec jsonCodec;

    AssemblyRunRecordStatementBinder(Gear4jDatabaseDialect dialect, PersistenceJsonCodec jsonCodec) {
        this.dialect = dialect;
        this.jsonCodec = jsonCodec;
    }

    void bindInsert(PreparedStatement stmt, AssemblyRunRecord execution) throws SQLException {
        int index = 1;
        dialect.setUuid(stmt, index++, execution.id());
        stmt.setString(index++, execution.assemblyLineId());
        dialect.setJson(stmt, index++, jsonCodec.toJson(execution.inputParams()));
        dialect.setJson(stmt, index++, jsonCodec.toJson(execution.context()));
        dialect.setJson(stmt, index++, jsonCodec.toJson(execution.result()));
        stmt.setString(index++, execution.status().name());
        dialect.setInstant(stmt, index++, execution.startTime());
        dialect.setInstant(stmt, index++, execution.endTime());
        stmt.setString(index++, execution.errorMessage());
        dialect.setUuid(stmt, index++, execution.parentExecutionId());
        dialect.setUuid(stmt, index++, execution.rootExecutionId());
        dialect.setUuid(stmt, index, execution.parentStationLogId());
    }

    void bindUpdate(PreparedStatement stmt, AssemblyRunRecord execution) throws SQLException {
        int index = 1;
        dialect.setJson(stmt, index++, jsonCodec.toJson(execution.context()));
        dialect.setJson(stmt, index++, jsonCodec.toJson(execution.result()));
        stmt.setString(index++, execution.status().name());
        dialect.setInstant(stmt, index++, execution.endTime());
        stmt.setString(index++, execution.errorMessage());
        dialect.setUuid(stmt, index++, execution.parentExecutionId());
        dialect.setUuid(stmt, index++, execution.rootExecutionId());
        dialect.setUuid(stmt, index++, execution.parentStationLogId());
        dialect.setUuid(stmt, index, execution.id());
    }
}
