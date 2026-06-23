package io.github.gear4jtest.core.event;

import java.util.UUID;

import io.github.gear4jtest.core.spi.security.RedactionTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EventPayloadPolicyTest {
    @Test
    void policies_shouldMapPayloadsAccordingToTheirContracts() {
        assertThat(EventPayloadPolicy.passthrough().mapStationInput("input", null)).isEqualTo("input");
        assertThat(EventPayloadPolicy.passthrough().mapStationOutput("output", null)).isEqualTo("output");

        assertThat(EventPayloadPolicy.discard().mapStationInput("input", null)).isNull();
        assertThat(EventPayloadPolicy.discard().mapStationOutput("output", null)).isNull();

        EventPayloadPolicy stringsOnly = EventPayloadPolicy.keepOnlyTypes(CharSequence.class, null);
        assertThat(stringsOnly.mapStationInput("kept", null)).isEqualTo("kept");
        assertThat(stringsOnly.mapStationInput(123, null)).isNull();
        assertThat(stringsOnly.mapStationInput(null, null)).isNull();
    }

    @Test
    void redacting_shouldDelegateThenApplyRedactorPerTarget() {
        EventPayloadPolicy delegate = EventPayloadPolicy.keepIf(String.class::isInstance);
        EventPayloadPolicy redacting = EventPayloadPolicy.redacting(delegate,
                                                                    (target, value) -> target.name() + ":" + value);

        assertThat(redacting.mapStationInput("secret", null))
                .isEqualTo(RedactionTarget.EVENT_INPUT.name() + ":secret");
        assertThat(redacting.mapStationOutput("result", null))
                .isEqualTo(RedactionTarget.EVENT_OUTPUT.name() + ":result");
    }

    @Test
    void factories_shouldRejectNullMandatoryArguments() {
        assertThatNullPointerException().isThrownBy(() -> EventPayloadPolicy.keepIf(null));
        assertThatNullPointerException().isThrownBy(() -> EventPayloadPolicy.keepOnlyTypes((Class<?>[]) null));
        assertThatNullPointerException().isThrownBy(() -> EventPayloadPolicy.redacting(null, (target, value) -> value));
        assertThatNullPointerException()
                .isThrownBy(() -> EventPayloadPolicy.redacting(EventPayloadPolicy.discard(), null));
    }

    @Test
    void operationEvents_shouldExposeTheirFields() {
        UUID executionId = UUID.randomUUID();
        OperationBaseEvent base = new OperationBaseEvent("pipeline", executionId, "TYPE", "op", "in", "out");

        assertThat(base.getAssemblyLineId()).isEqualTo("pipeline");
        assertThat(base.getExecutionId()).isEqualTo(executionId);
        assertThat(base.getName()).isEqualTo("TYPE");
        assertThat(base.getOperationId()).isEqualTo("op");
        assertThat(base.getInput()).isEqualTo("in");
        assertThat(base.getOutput()).isEqualTo("out");

        IllegalArgumentException error = new IllegalArgumentException("boom");
        OperationErrorEvent errorEvent = new OperationErrorEvent("pipeline", executionId, "op", "in", error);
        assertThat(errorEvent.getName()).isEqualTo("OPERATION_ERROR");
        assertThat(errorEvent.getException()).isSameAs(error);
        assertThat(errorEvent.getOutput()).isNull();
    }
}
