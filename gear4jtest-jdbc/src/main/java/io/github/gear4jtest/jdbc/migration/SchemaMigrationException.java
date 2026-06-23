package io.github.gear4jtest.jdbc.migration;

/**
 * Raised when Gear4J cannot apply or validate its internal JDBC schema
 * migrations.
 */
public class SchemaMigrationException extends RuntimeException {
    public SchemaMigrationException(String message) {
        super(message);
    }

    public SchemaMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
