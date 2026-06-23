package io.github.gear4jtest.core.api.station;

/**
 * Logical station type used by processors and strategies to filter behavior
 * without relying on {@code instanceof}.
 */
public enum StationKind {
    PROCESSING, CONTAINER, SEQUENCE, ITERATOR, SIGNAL, ASSEMBLY_LINE, ROOT, CUSTOM
}
