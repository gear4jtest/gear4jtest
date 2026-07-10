package io.github.gear4jtest.core.sidecompute;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SideComputerTest {
    @Test
    void toSubscription_shouldFilterComputeHandleAndMapRegisteredEvents() throws Exception {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext context = executionContext();
        registry.register(context);
        AtomicReference<String> handled = new AtomicReference<>();
        SideComputer<CustomEvent, String, Integer> computer = SideComputer
                .<CustomEvent, String>onEvent(CustomEvent.class, "length")
                .filter(event -> event.payload().startsWith("go"))
                .computer(CustomEvent::payload)
                .addHandler((key, event, value, executionContext) -> handled.set(key + ":" + value + ":"
                        + executionContext.getExecutionId()))
                .map(String::length)
                .build();
        EventSubscription<CustomEvent> subscription = computer.toSubscription(registry);

        assertThat(computer.key()).isEqualTo("length");
        assertThat(subscription.accepts(new Event("pipe", context.getExecutionId()))).isFalse();
        assertThat(subscription.accepts(new CustomEvent("pipe", context.getExecutionId(), "skip"))).isFalse();
        assertThat(subscription.accepts(new CustomEvent("pipe", context.getExecutionId(), "go-now"))).isTrue();

        subscription.handle(new CustomEvent("pipe", context.getExecutionId(), "go-now"));

        assertThat(context.getSideComputeContext().<Integer>getOrCreateFuture("length").join()).isEqualTo(6);
        assertThat(handled.get()).isEqualTo("length:go-now:" + context.getExecutionId());
    }

    @Test
    void toSubscription_shouldIgnoreEventsWithoutRegisteredExecutionContext() throws Exception {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        AtomicReference<String> computed = new AtomicReference<>();
        SideComputer<CustomEvent, String, String> computer = SideComputer
                .<CustomEvent, String>onEvent(CustomEvent.class, "missing")
                .computer(event -> {
                    computed.set(event.payload());
                    return event.payload();
                })
                .build();

        computer.toSubscription(registry).handle(new CustomEvent("pipe", UUID.randomUUID(), "value"));

        assertThat(computed.get()).isNull();
    }

    @Test
    void toSubscription_shouldCompleteFutureExceptionallyWhenComputerFails() throws Exception {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext context = executionContext();
        registry.register(context);
        SideComputer<CustomEvent, String, String> computer = SideComputer
                .<CustomEvent, String>onEvent(CustomEvent.class, "failure")
                .computer(event -> {
                    throw new IllegalStateException("compute failed");
                })
                .build();

        computer.toSubscription(registry).handle(new CustomEvent("pipe", context.getExecutionId(), "value"));

        assertThatThrownBy(() -> context.getSideComputeContext().<String>getOrCreateFuture("failure").join())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("compute failed");
    }

    @Test
    void stationStatusFactories_shouldBuildPredicatesForFinishedEvents() {
        SideComputer<StationFinishedEvent, String, String> success = SideComputer
                .<String>onStationSuccess("station", "success")
                .computer(event -> String.valueOf(event.getOutput()))
                .build();
        SideComputer<StationFinishedEvent, String, String> failure = SideComputer
                .<String>onStationFailure("station", "failure")
                .computer(event -> event.getStatus().name())
                .build();
        StationFinishedEvent succeeded = stationFinished("station", StationLogStatus.SUCCEEDED);
        StationFinishedEvent failed = stationFinished("station", StationLogStatus.FAILED);
        StationFinishedEvent otherOperation = stationFinished("other", StationLogStatus.SUCCEEDED);

        assertThat(success.toSubscription(new ExecutionContextRegistry()).accepts(succeeded)).isTrue();
        assertThat(success.toSubscription(new ExecutionContextRegistry()).accepts(failed)).isFalse();
        assertThat(success.toSubscription(new ExecutionContextRegistry()).accepts(otherOperation)).isFalse();
        assertThat(failure.toSubscription(new ExecutionContextRegistry()).accepts(failed)).isTrue();
    }

    @Test
    void build_shouldRejectMissingComputer() {
        assertThatThrownBy(() -> SideComputer.<CustomEvent, String>onEvent(CustomEvent.class, "missing-computer")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("computer");
    }

    @Test
    void toSubscription_shouldExecuteComputerAndHandlersOnlyOnce() throws Exception {
        // Given
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext context = executionContext();
        registry.register(context);
        AtomicInteger computations = new AtomicInteger();
        AtomicInteger handlers = new AtomicInteger();
        SideComputer<CustomEvent, String, String> computer = SideComputer
                .<CustomEvent, String>onEvent(CustomEvent.class, "once")
                .computer(event -> {
                    computations.incrementAndGet();
                    return event.payload();
                })
                .addHandler((key, event, value, executionContext) -> handlers.incrementAndGet())
                .build();
        EventSubscription<CustomEvent> subscription = computer.toSubscription(registry);

        // When
        subscription.handle(new CustomEvent("pipe", context.getExecutionId(), "first"));
        subscription.handle(new CustomEvent("pipe", context.getExecutionId(), "second"));

        // Then
        assertThat(computations.get()).isEqualTo(1);
        assertThat(handlers.get()).isEqualTo(1);
        assertThat(context.getSideComputeContext().<String>getOrCreateFuture("once").join()).isEqualTo("first");
    }

    @Test
    void toSubscription_shouldCompleteExceptionallyWhenMapperReturnsNull() throws Exception {
        // Given
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        ExecutionContext context = executionContext();
        registry.register(context);
        SideComputer<CustomEvent, String, String> computer = SideComputer
                .<CustomEvent, String>onEvent(CustomEvent.class, "null-result")
                .computer(CustomEvent::payload)
                .map(value -> (String) null)
                .build();

        // When
        computer.toSubscription(registry).handle(new CustomEvent("pipe", context.getExecutionId(), "value"));

        // Then
        assertThatThrownBy(() -> context.getSideComputeContext().<String>getOrCreateFuture("null-result").join())
                .hasRootCauseMessage("Side compute 'null-result' returned null; null results are not supported");
    }

    private static StationFinishedEvent stationFinished(String operationId, StationLogStatus status) {
        return new StationFinishedEvent("pipe", UUID.randomUUID(), UUID.randomUUID(), operationId, null, null,
                "input", status, "output", status == StationLogStatus.FAILED ? new RuntimeException("boom") : null);
    }

    private static ExecutionContext executionContext() {
        UUID executionId = UUID.randomUUID();
        return ExecutionContext.builder()
                .executionId(executionId)
                .assemblyLineId("pipe")
                .services(new ExecutionServices(null, new NoOpResourceFactory()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()))
                .build();
    }

    private static final class CustomEvent extends Event {
        private final String payload;

        private CustomEvent(String assemblyLineId, UUID executionId, String payload) {
            super(assemblyLineId, executionId);
            this.payload = payload;
        }

        String payload() {
            return payload;
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
