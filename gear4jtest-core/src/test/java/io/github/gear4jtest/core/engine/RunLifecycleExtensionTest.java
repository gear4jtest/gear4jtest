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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunLifecycleExtensionTest {
    @Test
    void operatorFailureWithoutMessage_shouldRemainReachableAfterCompletionFailure() {
        // Given
        var original = new IllegalStateException();
        var completion = new IllegalArgumentException("completion failed");
        var operator = new FailingOperator(original);
        var extension = new RunLifecycleExtension() {
            @Override
            public LifecycleFailureMode failureMode() {
                return LifecycleFailureMode.CRITICAL;
            }

            @Override
            public void onRunCompleted(ExecutionContext context, RunTrace trace) {
                throw completion;
            }
        };
        var pipeline = AssemblyLines.<String>createAssemblyLine("operator-failure")
                .then(Stations.processingOperation("fail", FailingOperator.class).build()).build();
        var engine = AssemblyLineEngine.builder().resourceFactory(new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                return type.cast(operator);
            }
        }).extensionResolver(new RuntimeExtensionResolver(List.of(extension)))
                .executionContextRegistry(new ExecutionContextRegistry()).build();

        // When
        var result = engine.execute(pipeline, RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getError()).hasCause(original);
        assertThat(result.getError().getSuppressed()).contains(completion);
        assertThat(result.getExecution().getError()).isSameAs(result.getError());
    }

    private static final class FailingOperator implements Operator<String, String> {
        private final RuntimeException failure;

        private FailingOperator(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public String transform(String input, StationExecutionContext context) {
            throw failure;
        }
    }

    @Test
    void completionFailure_shouldPreserveTheOriginalRunFailureAndTrace() {
        // Given
        var original = new IllegalStateException("original run failure");
        var completion = new IllegalStateException("completion failed");
        var extension = failingLifecycle(original, completion);

        // When
        var result = engine(extension).execute(pipeline(), RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(result.getError()).isSameAs(original);
        assertThat(result.getError().getSuppressed()).containsExactly(completion);
        assertThat(result.getExecution().getError()).isSameAs(original);
    }

    @Test
    void reusedLifecycleException_shouldNotCauseSelfSuppression() {
        // Given
        var original = new IllegalStateException("shared failure");

        // When
        var result = engine(failingLifecycle(original, original))
                .execute(pipeline(), RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getError()).isSameAs(original);
        assertThat(original.getSuppressed()).isEmpty();
    }

    @Test
    void fatalCompletionFailure_shouldEscapeWithTheOriginalFailure() {
        // Given
        var original = new IllegalStateException("original failure");
        var fatal = new AssertionError("fatal completion");
        var extension = new RunLifecycleExtension() {
            @Override
            public LifecycleFailureMode failureMode() {
                return LifecycleFailureMode.CRITICAL;
            }

            @Override
            public void onRunStarted(ExecutionContext context, RunTrace trace) {
                throw original;
            }

            @Override
            public void onRunCompleted(ExecutionContext context, RunTrace trace) {
                throw fatal;
            }
        };

        // When / Then
        assertThatThrownBy(() -> engine(extension).execute(pipeline(), RunRequest.builder().input("input").build()))
                .isSameAs(fatal);
        assertThat(fatal.getSuppressed()).containsExactly(original);
    }

    private static RunLifecycleExtension failingLifecycle(RuntimeException start, RuntimeException completion) {
        return new RunLifecycleExtension() {
            @Override
            public LifecycleFailureMode failureMode() {
                return LifecycleFailureMode.CRITICAL;
            }

            @Override
            public void onRunStarted(ExecutionContext context, RunTrace trace) {
                throw start;
            }

            @Override
            public void onRunCompleted(ExecutionContext context, RunTrace trace) {
                throw completion;
            }
        };
    }

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
