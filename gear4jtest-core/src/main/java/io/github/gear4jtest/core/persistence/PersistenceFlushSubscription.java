package io.github.gear4jtest.core.persistence;

/** Removable subscription to persistence-flush observations. */
@FunctionalInterface
public interface PersistenceFlushSubscription extends AutoCloseable {
    @Override
    void close();
}
