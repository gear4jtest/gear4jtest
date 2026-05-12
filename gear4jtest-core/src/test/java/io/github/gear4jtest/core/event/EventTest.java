package io.github.gear4jtest.core.event;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventTest {

    @Test
    void constructor_shouldInitializeAllFields() {
        String pipelineId = "pipeline-1";
        var executionId = UUID.randomUUID();
        String type = "MY_EVENT";

        Event event = new Event(pipelineId, executionId, type);

        assertThat(event.getId()).isNotNull().isInstanceOf(UUID.class);
        assertThat(event.getPipelineId()).isEqualTo(pipelineId);
        assertThat(event.getExecutionId()).isEqualTo(executionId);
        assertThat(event.getName()).isEqualTo(type);
    }

    @Test
    void eachEventShouldHaveADifferentId() {
        var executionId = UUID.randomUUID();
        Event e1 = new Event("p", executionId, "TYPE");
        Event e2 = new Event("p", executionId, "TYPE");

        assertThat(e1.getId()).isNotEqualTo(e2.getId());
    }
}
