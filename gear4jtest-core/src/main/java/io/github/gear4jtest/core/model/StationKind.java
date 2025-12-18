package io.github.gear4jtest.core.model;

/**
 * Type logique d'opération (processing, container, iterator, signal, ...).
 * Permet aux processors de filtrer proprement sans instanceof.
 */
public enum StationKind {
    PROCESSING,
    CONTAINER,
    ITERATOR,
    SIGNAL,
    PIPELINE,
    OTHER
}