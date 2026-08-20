package io.github.gear4jtest.jdbc.persistence;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/** Validates record identifiers against the portable JDBC V1 schema. */
public final class JdbcPersistenceRecordValidator {
    public static final int MAX_IDENTIFIER_CODE_POINTS = 255;

    private JdbcPersistenceRecordValidator() {
    }

    public static void validate(AssemblyRunRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        requireIdentifier(record.assemblyLineId(), "assemblyLineId");
    }

    public static void validate(StationLogRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        requireIdentifier(record.operationId(), "operationId");
        optionalIdentifier(record.branchId(), "branchId");
        optionalIdentifier(record.itemId(), "itemId");
    }

    public static void validateAll(List<StationLogRecord> records) {
        Objects.requireNonNull(records, "records must not be null");
        records.forEach(JdbcPersistenceRecordValidator::validate);
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        optionalIdentifier(value, name);
    }

    private static void optionalIdentifier(String value, String name) {
        if (value != null && value.codePointCount(0, value.length()) > MAX_IDENTIFIER_CODE_POINTS) {
            throw new IllegalArgumentException(name + " must not exceed " + MAX_IDENTIFIER_CODE_POINTS
                    + " Unicode code points");
        }
    }
}
