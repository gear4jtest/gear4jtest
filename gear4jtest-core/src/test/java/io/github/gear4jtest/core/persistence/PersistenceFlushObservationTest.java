package io.github.gear4jtest.core.persistence;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceFlushObservationTest {
    @Test
    void shouldRejectInvalidObservations() {
        assertThatThrownBy(() -> new PersistenceFlushObservation(null,
                PersistenceFlushObservation.Trigger.ASYNC, PersistenceFlushObservation.Outcome.SUCCEEDED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("duration must not be null");
        assertThatThrownBy(() -> new PersistenceFlushObservation(Duration.ofNanos(-1),
                PersistenceFlushObservation.Trigger.ASYNC, PersistenceFlushObservation.Outcome.SUCCEEDED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duration must not be negative");
        assertThatThrownBy(() -> new PersistenceFlushObservation(Duration.ZERO, null,
                PersistenceFlushObservation.Outcome.SUCCEEDED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("trigger must not be null");
        assertThatThrownBy(() -> new PersistenceFlushObservation(Duration.ZERO,
                PersistenceFlushObservation.Trigger.ASYNC, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("outcome must not be null");
    }
}
