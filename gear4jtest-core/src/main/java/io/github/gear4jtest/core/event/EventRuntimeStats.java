package io.github.gear4jtest.core.event;

/**
 * Immutable snapshot of the asynchronous event runtime counters.
 *
 * <p>
 * These counters are intended for observability and debugging. They do not
 * provide transactional guarantees and should be interpreted as best-effort
 * runtime metrics.
 * </p>
 *
 * @param publishedEvents             number of events accepted by
 *                                    {@link EventManager#publish(Event)}
 * @param dispatchedEvents            number of queued events drained by the
 *                                    dispatcher thread
 * @param submittedReactions          number of reactions successfully submitted
 *                                    to the configured executor
 * @param completedReactions          number of submitted reactions that
 *                                    completed, whether successfully or
 *                                    exceptionally
 * @param droppedReactions            number of reactions dropped before
 *                                    execution, typically due to executor
 *                                    saturation or shutdown
 * @param failedReactions             number of reactions that started execution
 *                                    and failed with an exception
 * @param droppedEvents               number of events rejected before dispatch
 *                                    because the in-memory event queue was full
 * @param queuedEvents                current number of events still waiting in
 *                                    the in-memory dispatcher queue
 * @param remainingEventQueueCapacity current free slots in the bounded
 *                                    dispatcher queue
 * @param pendingReactions            number of accepted reactions that have not
 *                                    reached a terminal state yet
 * @param inFlightReactions           number of reactions currently executing in
 *                                    the configured reaction executor
 */
public record EventRuntimeStats(long publishedEvents,
                                long dispatchedEvents,
                                long submittedReactions,
                                long completedReactions,
                                long droppedReactions,
                                long failedReactions,
                                long droppedEvents,
                                long queuedEvents,
                                long remainingEventQueueCapacity,
                                long pendingReactions,
                                long inFlightReactions) {}
