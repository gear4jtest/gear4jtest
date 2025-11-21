package io.github.gear4jtest.core.model.refactor;

/**
 * Type logique d'opération (processing, container, iterator, signal, ...).
 * Permet aux processors de filtrer proprement sans instanceof.
 */
public enum OperationKind {
    PROCESSING,
    CONTAINER,
    ITERATOR,
    SIGNAL,
    OTHER
}