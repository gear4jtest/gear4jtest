package io.github.gear4jtest.core.engine.runner;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FailurePolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationLifecycleRunnerTest {
    private static final AtomicInteger EXECUTIONS = new AtomicInteger();

    @Test
    void bestEffortStationLifecycleFailure_shouldNotFailRun() {
        // Given
        AssemblyLineEngine engine = engine(new FailingStationLifecycleExtension(LifecycleFailureMode.BEST_EFFORT,
                true));

        // When
        ExecutionResult<String> result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("ok");
    }

    @Test
    void criticalStationStartedFailure_shouldFailThroughStationStatusWithoutExecutingDelegate() {
        // Given
        EXECUTIONS.set(0);
        AssemblyLineEngine engine = engine(new FailingStationLifecycleExtension(LifecycleFailureMode.CRITICAL, true));

        // When
        ExecutionResult<String> result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("onStationStarted")
                .hasMessageContaining("station lifecycle start failed");
        assertThat(EXECUTIONS).hasValue(0);
    }

    @Test
    void criticalStationCompletedFailure_shouldTurnSuccessfulStationIntoFailedStatus() {
        // Given
        EXECUTIONS.set(0);
        AssemblyLineEngine engine = engine(new FailingStationLifecycleExtension(LifecycleFailureMode.CRITICAL, false));

        // When
        ExecutionResult<String> result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("onStationCompleted")
                .hasMessageContaining("station lifecycle completion failed");
        assertThat(EXECUTIONS).hasValue(1);
    }

    @Test
    void persistenceExtension_shouldPersistTheNormalizedFailedStatusAfterCriticalCompletionFailure() {
        // Given
        RecordingAssemblyRunManager manager = new RecordingAssemblyRunManager("echo");
        AssemblyLineEngine engine = engine(List.of(new PersistenceExtension(manager),
                                                   new FailingStationLifecycleExtension(LifecycleFailureMode.CRITICAL,
                                                           false, "echo")));

        // When
        ExecutionResult<String> result = engine.execute(pipeline(), RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(manager.lastStatus()).hasValue(StationLogStatus.FAILED);
    }

    @Test
    void criticalChildLifecycleFailure_shouldBeHandledByParentFlowPolicy() {
        // Given
        AssemblyLineEngine engine = engine(new FailingStationLifecycleExtension(LifecycleFailureMode.CRITICAL, true,
                "first"));
        SequenceStation<String, String> sequence = SequenceStation.Builder.<String>create("sequence")
                .next(Stations.processingOperation("first", EchoOperator.class).build())
                .next(Stations.processingOperation("second", AppendOperator.class).build())
                .flowConfig(new FlowConfig(FailurePolicy.IGNORE_AND_CONTINUE, StopPolicy.PROPAGATE_STOP,
                        CancelPolicy.PROPAGATE_CANCEL))
                .build();
        AssemblyLine<String, String> pipeline = AssemblyLines.<String>createAssemblyLine("flow-policy")
                .then(sequence).build();

        // When
        ExecutionResult<String> result = engine.execute(pipeline, RunRequest.builder().input("ok").build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("ok-second");
    }

    private static AssemblyLineEngine engine(StationLifecycleExtension extension) {
        return engine(List.of(extension));
    }

    private static AssemblyLineEngine engine(List<? extends RuntimeExtension> extensions) {
        return AssemblyLineEngine.builder().resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(List.copyOf(extensions)))
                .executionContextRegistry(new ExecutionContextRegistry()).build();
    }

    private static AssemblyLine<String, String> pipeline() {
        return AssemblyLines.<String>createAssemblyLine("lifecycle-test")
                .then(Stations.processingOperation("echo", EchoOperator.class).build()).build();
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
            EXECUTIONS.incrementAndGet();
            return input;
        }
    }

    public static final class AppendOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "-second";
        }
    }

    private static final class RecordingAssemblyRunManager implements AssemblyRunManager {
        private final String stationId;
        private final AtomicReference<StationLogStatus> lastStatus = new AtomicReference<>();

        private RecordingAssemblyRunManager(String stationId) {
            this.stationId = stationId;
        }

        @Override
        public void start(AssemblyRunTrace execution) {
            // no-op
        }

        @Override
        public void append(StationLogRecord stationLogRecord) {
            if (stationId.equals(stationLogRecord.operationId())) {
                lastStatus.set(stationLogRecord.status());
            }
        }

        @Override
        public void end(AssemblyRunTrace finalExecution) {
            // no-op
        }

        private AtomicReference<StationLogStatus> lastStatus() {
            return lastStatus;
        }
    }

    private static final class FailingStationLifecycleExtension implements StationLifecycleExtension {
        private final LifecycleFailureMode failureMode;
        private final boolean failOnStart;
        private final String stationId;

        private FailingStationLifecycleExtension(LifecycleFailureMode failureMode, boolean failOnStart) {
            this(failureMode, failOnStart, null);
        }

        private FailingStationLifecycleExtension(LifecycleFailureMode failureMode,
                                                 boolean failOnStart,
                                                 String stationId) {
            this.failureMode = failureMode;
            this.failOnStart = failOnStart;
            this.stationId = stationId;
        }

        @Override
        public LifecycleFailureMode failureMode() {
            return failureMode;
        }

        @Override
        public void onStationStarted(ExecutionContext runCtx,
                                     StationExecutionContext stationCtx,
                                     StationLogRecord snapshot) {
            if (failOnStart && (stationId == null || stationId.equals(stationCtx.getOperationId()))) {
                throw new IllegalStateException("station lifecycle start failed");
            }
        }

        @Override
        public void onStationCompleted(ExecutionContext runCtx,
                                       StationExecutionContext stationCtx,
                                       StationLogRecord snapshot) {
            if (!failOnStart && (stationId == null || stationId.equals(stationCtx.getOperationId()))) {
                throw new IllegalStateException("station lifecycle completion failed");
            }
        }
    }
}
