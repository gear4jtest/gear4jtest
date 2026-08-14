package io.github.gear4jtest.core.engine.runner;

import java.time.Duration;

import io.github.gear4jtest.core.util.PeriodicLogLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Isolates built-in station event publication from the business execution. */
final class BestEffortStationEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(BestEffortStationEventPublisher.class);
    private static final PeriodicLogLimiter FAILURE_LOGS = PeriodicLogLimiter.every(Duration.ofMinutes(1L));

    private BestEffortStationEventPublisher() {
    }

    static void publish(String eventType, String operationId, Runnable publication) {
        try {
            publication.run();
        } catch (Exception failure) {
            PeriodicLogLimiter.Emission emission = FAILURE_LOGS.acquire();
            if (emission.permitted()) {
                LOGGER.warn("Dropping built-in station event after payload mapping or publication failure. "
                        + "eventType={}, operationId={}, suppressedSincePreviousEmission={}", eventType, operationId,
                            emission.suppressedSincePreviousEmission(), failure);
            }
        }
    }
}
