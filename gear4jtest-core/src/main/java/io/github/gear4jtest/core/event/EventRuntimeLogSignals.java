package io.github.gear4jtest.core.event;

import java.time.Duration;

import io.github.gear4jtest.core.util.PeriodicLogLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal flood control for counted event-runtime diagnostics. */
final class EventRuntimeLogSignals {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
    private static final Duration REPEATED_SIGNAL_LOG_INTERVAL = Duration.ofMinutes(1L);
    private static final PeriodicLogLimiter QUEUE_REJECTION_LOGS = repeatedSignalLimiter();
    private static final PeriodicLogLimiter DISPATCH_REJECTION_LOGS = repeatedSignalLimiter();
    private static final PeriodicLogLimiter REACTION_REJECTION_LOGS = repeatedSignalLimiter();
    private static final PeriodicLogLimiter REACTION_SUBMISSION_FAILURE_LOGS = repeatedSignalLimiter();
    private static final PeriodicLogLimiter REACTION_FAILURE_LOGS = repeatedSignalLimiter();
    private static final PeriodicLogLimiter PREDICATE_FAILURE_LOGS = repeatedSignalLimiter();

    private EventRuntimeLogSignals() {
    }

    static void eventQueueRejected(String eventType, int capacity) {
        PeriodicLogLimiter.Emission emission = QUEUE_REJECTION_LOGS.acquire();
        if (emission.permitted()) {
            LOGGER.warn("Dropping event because the in-memory event queue is full. eventType={}, capacity={}, "
                    + "suppressedSincePreviousEmission={}", eventType, capacity,
                        emission.suppressedSincePreviousEmission());
        }
    }

    static void dispatchRejected() {
        PeriodicLogLimiter.Emission emission = DISPATCH_REJECTION_LOGS.acquire();
        if (emission.permitted()) {
            LOGGER.warn("Dropping pending events because the shared event dispatcher rejected the dispatch task. "
                    + "suppressedSincePreviousEmission={}", emission.suppressedSincePreviousEmission());
        }
    }

    static void reactionRejected(String eventType, String subscriptionType, RuntimeException failure) {
        PeriodicLogLimiter.Emission emission = REACTION_REJECTION_LOGS.acquire();
        if (emission.permitted()) {
            LOGGER.warn("Dropping event reaction because the reaction executor rejected the submission. "
                    + "eventType={}, subscriptionType={}, suppressedSincePreviousEmission={}", eventType,
                        subscriptionType, emission.suppressedSincePreviousEmission(), failure);
        }
    }

    static void reactionSubmissionFailed(String eventType, String subscriptionType, RuntimeException failure) {
        PeriodicLogLimiter.Emission emission = REACTION_SUBMISSION_FAILURE_LOGS.acquire();
        if (emission.permitted()) {
            LOGGER.error("Dropping event reaction because submitting it to the reaction executor failed unexpectedly. "
                    + "eventType={}, subscriptionType={}, suppressedSincePreviousEmission={}", eventType,
                         subscriptionType, emission.suppressedSincePreviousEmission(), failure);
        }
    }

    static void reactionFailed(String eventType, String subscriptionType, Exception failure) {
        PeriodicLogLimiter.Emission emission = REACTION_FAILURE_LOGS.acquire();
        if (emission.permitted()) {
            LOGGER.error("Asynchronous event reaction failed. eventType={}, subscriptionType={}, "
                    + "suppressedSincePreviousEmission={}", eventType, subscriptionType,
                         emission.suppressedSincePreviousEmission(), failure);
        }
    }

    static void predicateFailed(String eventType, String subscriptionType, RuntimeException failure) {
        PeriodicLogLimiter.Emission emission = PREDICATE_FAILURE_LOGS.acquire();
        if (emission.permitted()) {
            LOGGER.error("Asynchronous event predicate failed. eventType={}, subscriptionType={}, "
                    + "suppressedSincePreviousEmission={}", eventType, subscriptionType,
                         emission.suppressedSincePreviousEmission(), failure);
        }
    }

    private static PeriodicLogLimiter repeatedSignalLimiter() {
        return PeriodicLogLimiter.every(REPEATED_SIGNAL_LOG_INTERVAL);
    }
}
