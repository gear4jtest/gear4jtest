package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.config.FlowConfig.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

class ParallelContainerBranchExecutorTest {
    @Test
    void execute_shouldRecordRejectedBranchSubmissionsAsFailures() {
        // Given
        ExecutorService rejectedExecutor = Executors.newSingleThreadExecutor();
        rejectedExecutor.shutdown();
        ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(rejectedExecutor)
                .withSubLine("rejected", new TestStation("branch"))
                .build();
        TestStationExecutionContext context = stationContext("container");

        // When
        ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                .execute(station, "input", successfulRunner(), context, DEFAULT, Duration.ofMillis(100));

        // Then
        assertThat(aggregation.results()).hasSize(1);
        StationLogTrace branchLog = aggregation.results().get(0);
        assertThat(branchLog.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(branchLog.getBranchId()).isEqualTo("rejected");
        assertThat(branchLog.getParentOperationId()).isEqualTo(context.record().getId());
        assertThat(aggregation.interruptingChild()).isSameAs(branchLog);
    }

    @Test
    void execute_shouldCreateSkippedSyntheticLogsForBranchesWithFalseConditions() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ContainerBaseStation<String, Void> station = new ContainerBaseStation.Builder<String, Void>(executor)
                    .withSubLine("skipped", new TestStation("branch"), (input, ctx) -> false)
                    .build();
            TestStationExecutionContext context = stationContext("container");

            // When
            ContainerExecutionAggregation aggregation = new ParallelContainerBranchExecutor()
                    .execute(station, "input", successfulRunner(), context, DEFAULT, Duration.ofMillis(100));

            // Then
            assertThat(aggregation.results()).hasSize(1);
            StationLogTrace branchLog = aggregation.results().get(0);
            assertThat(branchLog.getStatus()).isEqualTo(StationLogStatus.SKIPPED);
            assertThat(branchLog.getBranchId()).isEqualTo("skipped");
            assertThat(aggregation.interruptingChild()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private static io.github.gear4jtest.core.spi.runner.StationRunner successfulRunner() {
        return (input, station, ctx) -> {
            StationLogTrace log = StationLogTrace.start(ctx.getGlobalContext().getExecutionId(), station.getId(), null);
            log.markSuccess(input);
            return log;
        };
    }

    private static TestStationExecutionContext stationContext(String operationId) {
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "pipeline", Map.of());
        ExecutionContext globalContext = ExecutionContext.builder()
                .executionId(run.getId())
                .pipelineId("pipeline")
                .services(new ExecutionServices(null, noResources()))
                .assemblyRun(run)
                .build();
        return new TestStationExecutionContext(operationId, globalContext,
                StationLogTrace.start(run.getId(), operationId, null), new ExecutionSupport(null, null, null));
    }

    private static ResourceFactory noResources() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
    }

    private record TestStationExecutionContext(String operationId,
                                               ExecutionContext globalContext,
                                               StationLogTrace record,
                                               ExecutionSupport support)
            implements StationExecutionContext {
        @Override
        public String getOperationId() {
            return operationId;
        }

        @Override
        public StationKind getKind() {
            return StationKind.CONTAINER;
        }

        @Override
        public ExecutionContext getGlobalContext() {
            return globalContext;
        }

        @Override
        public StationLogTrace getRecord() {
            return record;
        }

        @Override
        public ExecutionSupport getSupport() {
            return support;
        }

        @Override
        public <T> Optional<T> getCapability(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public ResolvedParameters getResolvedParameters() {
            return new ResolvedParameters();
        }
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation(String id) {
            super(id, StationKind.PROCESSING);
        }
    }
}
