package io.github.gear4jtest.core.event.durable;

/** Dispatch state of a durable event envelope. */
public enum DurableEventStatus {
    PENDING,
    CLAIMED,
    PUBLISHED,
    FAILED,
    DEAD_LETTERED
}
