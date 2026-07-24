package io.github.gear4jtest.jdbc.migration;

/**
 * Durable state of a Gear4J-managed schema migration.
 *
 * <p>
 * {@link #STARTED} and {@link #FAILED} require operator inspection before the
 * migration can be retried. This is especially important on databases where DDL
 * statements may commit independently of the surrounding JDBC transaction.
 * </p>
 */
public enum SchemaMigrationState {
    STARTED,
    APPLIED,
    FAILED
}
