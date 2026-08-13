package io.github.gear4jtest.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionOutcome;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunLifecycleExtensionTest {
    @Test
    void onRunStarted_shouldObserveStartedTrace() {
        // Given
        StartSnapshotExtension extension = new StartSnapshotExtension();
        AssemblyLineEngine engine = engine(extension);

        // When
        var result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(extension.status()).hasValue(ExecutionStatus.RUNNING);
        assertThat(extension.startTime().get()).as("run start hook should observe the official run start time")
                .isNotNull();
        assertThat(extension.endTimeAtStart().get()).as("run timing must still be open during the start hook")
                .isNull();
        assertThat(extension.endTimeAtCompletion().get())
                .as("normal completion hooks must observe the already closed runtime interval")
                .isEqualTo(result.getExecution().getEndTime());
    }

    @Test
    void criticalRunStartedFailure_shouldBeReturnedAsFailedExecutionResult() {
        // Given
        AssemblyLineEngine engine = engine(new FailingRunLifecycleExtension(true));

        // When
        var result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(result.getExecution().getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getError()).hasMessageContaining("run start failed");
        assertThat(result.getExecution().getEndTime()).isNotNull();
    }

    @Test
    void criticalRunCompletedFailure_shouldBeReturnedAsFailedExecutionResult() {
        // Given
        AssemblyLineEngine engine = engine(new FailingRunLifecycleExtension(false));

        // When
        var result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(result.getExecution().getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getError()).hasMessageContaining("run completion failed");
        assertThat(result.getExecution().getEndTime()).isNotNull();
    }

    @Test
    void criticalRunCompletedFailure_shouldBeVisibleToLaterPersistenceExtension() {
        // Given
        RecordingRunManager manager = new RecordingRunManager();
        AssemblyLineEngine engine = engine(List.of(new PersistenceExtension(manager),
                                                   new FailingRunLifecycleExtension(false)));

        // When
        var result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(manager.completedStatus()).hasValue(ExecutionStatus.FAILED);
        assertThat(manager.completedEndTime()).hasValue(result.getExecution().getEndTime());
        assertThat(manager.completedError()).hasValue(result.getError());
    }

    @Test
    void criticalRunStartedFailure_shouldStillPairEveryLifecycleStartAndCompletion() {
        // Given
        List<String> calls = new ArrayList<>();
        var low = new OrderedRunLifecycleExtension("low", 0, false, calls);
        var failing = new OrderedRunLifecycleExtension("failing", 50, true, calls);
        var high = new OrderedRunLifecycleExtension("high", 100, false, calls);
        AssemblyLineEngine engine = engine(List.of(low, failing, high));

        // When
        var result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(calls).containsExactly(
                                          "high-start", "failing-start", "low-start",
                                          "low-completed", "failing-completed", "high-completed");
    }

    private static AssemblyLineEngine engine(RuntimeExtension extension) {
        return engine(List.of(extension));
    }

    private static AssemblyLineEngine engine(List<? extends RuntimeExtension> extensions) {
        return AssemblyLineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(List.copyOf(extensions)))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();
    }

    private static AssemblyLine<String, String> pipeline() {
        return AssemblyLines.<String>createAssemblyLine("run-lifecycle")
                .then(Stations.processingOperation("echo", EchoOperator.class).build())
                .build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    public static final class EchoOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }

    private static final class StartSnapshotExtension implements RunLifecycleExtension {
        private final AtomicReference<ExecutionStatus> status = new AtomicReference<>();
        private final AtomicReference<java.time.Instant> startTime = new AtomicReference<>();
        private final AtomicReference<java.time.Instant> endTimeAtStart = new AtomicReference<>();
        private final AtomicReference<java.time.Instant> endTimeAtCompletion = new AtomicReference<>();

        @Override
        public void onRunStarted(ExecutionContext ctx, RunTrace run) {
            status.set(run.getStatus());
            startTime.set(run.getStartTime());
            endTimeAtStart.set(run.getEndTime());
        }

        @Override
        public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
            endTimeAtCompletion.set(run.getEndTime());
        }

        private AtomicReference<ExecutionStatus> status() {
            return status;
        }

        private AtomicReference<java.time.Instant> startTime() {
            return startTime;
        }

        private AtomicReference<java.time.Instant> endTimeAtStart() {
            return endTimeAtStart;
        }

        private AtomicReference<java.time.Instant> endTimeAtCompletion() {
            return endTimeAtCompletion;
        }
    }

    private record OrderedRunLifecycleExtension(String name,
                                                int order,
                                                boolean failOnStart,
                                                List<String> calls)
            implements RunLifecycleExtension {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public LifecycleFailureMode failureMode() {
            return LifecycleFailureMode.CRITICAL;
        }

        @Override
        public void onRunStarted(ExecutionContext ctx, RunTrace run) {
            calls.add(name + "-start");
            if (failOnStart) {
                throw new IllegalStateException("ordered start failure");
            }
        }

        @Override
        public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
            calls.add(name + "-completed");
        }
    }

    private static final class RecordingRunManager implements RunPersistenceManager {
        private final AtomicReference<ExecutionStatus> completedStatus = new AtomicReference<>();
        private final AtomicReference<java.time.Instant> completedEndTime = new AtomicReference<>();
        private final AtomicReference<Exception> completedError = new AtomicReference<>();

        @Override
        public void start(RunTrace execution) {
            // no-op
        }

        @Override
        public void append(StationLogRecord stationLogRecord) {
            // no-op
        }

        @Override
        public void end(RunTrace finalExecution) {
            completedStatus.set(finalExecution.getStatus());
            completedEndTime.set(finalExecution.getEndTime());
            completedError.set(finalExecution.getError());
        }

        private AtomicReference<ExecutionStatus> completedStatus() {
            return completedStatus;
        }

        private AtomicReference<java.time.Instant> completedEndTime() {
            return completedEndTime;
        }

        private AtomicReference<Exception> completedError() {
            return completedError;
        }
    }

    private static final class FailingRunLifecycleExtension implements RunLifecycleExtension {
        private final boolean failOnStart;

        private FailingRunLifecycleExtension(boolean failOnStart) {
            this.failOnStart = failOnStart;
        }

        @Override
        public LifecycleFailureMode failureMode() {
            return LifecycleFailureMode.CRITICAL;
        }

        @Override
        public void onRunStarted(ExecutionContext ctx, RunTrace run) {
            if (failOnStart) {
                throw new IllegalStateException("run start failed");
            }
        }

        @Override
        public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
            if (!failOnStart) {
                throw new IllegalStateException("run completion failed");
            }
        }
    }
}
