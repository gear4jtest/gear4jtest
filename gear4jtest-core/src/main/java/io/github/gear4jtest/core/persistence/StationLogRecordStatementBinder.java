package io.github.gear4jtest.core.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;

final class StationLogRecordStatementBinder {
    private final Gear4jDatabaseDialect dialect;
    private final DatabasePersistenceJsonCodec jsonCodec;

    StationLogRecordStatementBinder(Gear4jDatabaseDialect dialect, DatabasePersistenceJsonCodec jsonCodec) {
        this.dialect = dialect;
        this.jsonCodec = jsonCodec;
    }

    void bindUpdateOpen(PreparedStatement stmt, StationLogRecord rec) throws SQLException {
        stmt.setString(1, rec.branchId());
        stmt.setString(2, rec.status().toString());
        dialect.setInstant(stmt, 3, rec.endedAt());
        stmt.setString(4, rec.errorMessage());
        stmt.setString(5, rec.errorHandlerMessages());
        dialect.setJson(stmt, 6, jsonCodec.toJson(rec.context()));
        stmt.setString(7, rec.itemId());
        dialect.setUuid(stmt, 8, rec.id());
    }

    void bindInsert(PreparedStatement stmt, StationLogRecord rec) throws SQLException {
        dialect.setUuid(stmt, 1, rec.id());
        dialect.setUuid(stmt, 2, rec.pipelineExecutionId());
        stmt.setString(3, rec.operationId());
        dialect.setUuid(stmt, 4, rec.parentOperationId());
        stmt.setString(5, rec.branchId());
        stmt.setString(6, rec.status().toString());
        dialect.setInstant(stmt, 7, rec.startedAt());
        dialect.setInstant(stmt, 8, rec.endedAt());
        stmt.setString(9, rec.errorMessage());
        stmt.setString(10, rec.errorHandlerMessages());
        dialect.setJson(stmt, 11, jsonCodec.toJson(rec.context()));
        stmt.setString(12, rec.itemId());
    }
}
