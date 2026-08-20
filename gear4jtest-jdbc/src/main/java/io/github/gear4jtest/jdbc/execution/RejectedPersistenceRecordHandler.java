package io.github.gear4jtest.jdbc.execution;

import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles a redacted station log that cannot be stored in the normal JDBC table
 * because of a record-specific permanent failure.
 *
 * <p>
 * Implementations that require zero loss must durably store the supplied record
 * before returning. If a handler throws, Gear4J keeps the record in the normal
 * in-memory buffer and retries later.
 * </p>
 */
@FunctionalInterface
public interface RejectedPersistenceRecordHandler {
    void handle(StationLogRecord record, RejectedPersistenceRecordContext context);

    /**
     * Returns the safe default handler. It logs identifiers and failure metadata,
     * but never the record payload, error message or failure cause.
     */
    static RejectedPersistenceRecordHandler loggingOnly() {
        Logger logger = LoggerFactory.getLogger(RejectedPersistenceRecordHandler.class);
        return (record, context) -> logger.error(
                                                 "Station log rejected by JDBC persistence. runId={}, stationLogId={}, operationId={}, "
                                                         + "sqlState={}, vendorCode={}, failureType={}",
                                                 record.assemblyLineExecutionId(), record.id(), record.operationId(),
                                                 context.sqlState(),
                                                 context.vendorCode(), context.failureType());
    }
}
