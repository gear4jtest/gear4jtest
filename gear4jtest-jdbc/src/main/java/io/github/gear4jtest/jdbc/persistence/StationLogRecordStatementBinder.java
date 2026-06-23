package io.github.gear4jtest.jdbc.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.github.gear4jtest.core.persistence.StationLogRecord;

final class StationLogRecordStatementBinder {
    private final Gear4jDatabaseDialect dialect;
    private final DatabasePersistenceJsonCodec jsonCodec;

    StationLogRecordStatementBinder(Gear4jDatabaseDialect dialect, DatabasePersistenceJsonCodec jsonCodec) {
        this.dialect = dialect;
        this.jsonCodec = jsonCodec;
    }

    void bindUpdateOpen(PreparedStatement stmt, StationLogRecord rec) throws SQLException {
        int index = 1;
        stmt.setString(index++, rec.branchId());
        stmt.setString(index++, rec.status().toString());
        dialect.setInstant(stmt, index++, rec.endedAt());
        stmt.setString(index++, rec.errorMessage());
        stmt.setString(index++, rec.errorHandlerMessages());
        dialect.setJson(stmt, index++, jsonCodec.toJson(rec.context()));
        stmt.setString(index++, rec.itemId());
        dialect.setUuid(stmt, index, rec.id());
    }

    void bindInsert(PreparedStatement stmt, StationLogRecord rec) throws SQLException {
        int index = 1;
        dialect.setUuid(stmt, index++, rec.id());
        dialect.setUuid(stmt, index++, rec.assemblyLineExecutionId());
        stmt.setString(index++, rec.operationId());
        dialect.setUuid(stmt, index++, rec.parentOperationId());
        stmt.setString(index++, rec.branchId());
        stmt.setString(index++, rec.status().toString());
        dialect.setInstant(stmt, index++, rec.startedAt());
        dialect.setInstant(stmt, index++, rec.endedAt());
        stmt.setString(index++, rec.errorMessage());
        stmt.setString(index++, rec.errorHandlerMessages());
        dialect.setJson(stmt, index++, jsonCodec.toJson(rec.context()));
        stmt.setString(index, rec.itemId());
    }
}
