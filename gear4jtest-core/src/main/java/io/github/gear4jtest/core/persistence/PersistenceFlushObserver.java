package io.github.gear4jtest.core.persistence;

/** Receives completed persistence-flush observations. */
@FunctionalInterface
public interface PersistenceFlushObserver {
    void onFlush(PersistenceFlushObservation observation);
}
