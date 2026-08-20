package io.github.gear4jtest.jdbc.execution;

/** Safe failure metadata supplied with a rejected persistence record. */
public record RejectedPersistenceRecordContext(String failureType,
                                               String sqlState,
                                               Integer vendorCode) {}
