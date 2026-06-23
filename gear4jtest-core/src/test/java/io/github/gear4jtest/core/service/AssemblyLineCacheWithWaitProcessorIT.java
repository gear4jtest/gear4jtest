package io.github.gear4jtest.core.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.extras.assemblylinecache.AssemblyLineCacheExtension;
import io.github.gear4jtest.core.extras.assemblylinecache.AssemblyLineCacheKeyFactory;
import io.github.gear4jtest.core.extras.assemblylinecache.AssemblyLineCachePolicy;
import io.github.gear4jtest.core.extras.assemblylinecache.AssemblyLineCacheRuntimeKeys;
import io.github.gear4jtest.core.extras.assemblylinecache.InMemoryAssemblyLineCacheRepository;
import io.github.gear4jtest.core.extras.assemblylinecache.NoDependencyCachePolicy;
import io.github.gear4jtest.core.extras.history.ExpirableDependencyTracker;
import io.github.gear4jtest.core.extras.history.fingerprint.JsonSha256FingerprintStrategy;
import io.github.gear4jtest.core.extras.history.fingerprint.WhitelistedContextFingerprintStrategy;
import io.github.gear4jtest.core.extras.history.taskhistory.RawTaskHistoryApi;
import io.github.gear4jtest.core.extras.history.taskhistory.TaskHistoryApi;
import io.github.gear4jtest.core.extras.history.taskhistory.TaskHistoryResult;
import io.github.gear4jtest.core.extras.history.taskhistory.TrackingTaskHistoryApi;
import io.github.gear4jtest.core.sidecompute.SideComputeHandler;
import io.github.gear4jtest.core.sidecompute.SideComputeWaitProcessor;
import io.github.gear4jtest.core.sidecompute.SideComputer;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineCacheWithWaitProcessorIT {
    @Test
    void should_wait_side_compute_via_processor_and_cache_pipeline_result() {
        InMemoryAssemblyLineCacheRepository cacheRepository = new InMemoryAssemblyLineCacheRepository();

        AssemblyLineCacheExtension cacheExtension = new AssemblyLineCacheExtension(
                new AssemblyLineCachePolicy(true, NoDependencyCachePolicy.DO_NOT_CACHE, null),
                new AssemblyLineCacheKeyFactory(new JsonSha256FingerprintStrategy<>(),
                        new WhitelistedContextFingerprintStrategy(List.of("tenantId"),
                                new JsonSha256FingerprintStrategy<>())),
                cacheRepository);

        FakeRawTaskHistoryApi rawTaskHistoryApi = new FakeRawTaskHistoryApi(
                Map.of("customer:42",
                       new TaskHistoryResult<>(new CustomerDto("John"), Instant.now().plus(Duration.ofMinutes(15))),
                       "order:42",
                       new TaskHistoryResult<>(new OrderDto("ORD-42"), Instant.now().plus(Duration.ofMinutes(10)))));

        TaskHistoryApi trackingTaskHistoryApi = new TrackingTaskHistoryApi(rawTaskHistoryApi);

        AtomicInteger triggerExecutions = new AtomicInteger();
        AtomicInteger joinExecutions = new AtomicInteger();

        TriggerSideComputeOperator triggerOperator = new TriggerSideComputeOperator(triggerExecutions);
        JoinUsingContextOperator joinOperator = new JoinUsingContextOperator(joinExecutions, trackingTaskHistoryApi);

        ResourceFactory resourceFactory = new TestResourceFactory(triggerOperator, joinOperator);

        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();

        SideComputer<StationFinishedEvent, TaskHistoryResult<CustomerDto>, CustomerDto> sideComputer = SideComputer
                .<TaskHistoryResult<CustomerDto>>onStationSuccess("trigger-customer-fetch", "customer-profile")
                .computer(event -> trackingTaskHistoryApi.get("customer:" + event.getOutput(), CustomerDto.class))
                .addHandler(new TaskHistoryExpirySideComputeHandler<>()).map(TaskHistoryResult::value).build();

        WorkStation<String, String> triggerStation = Stations
                .processingOperation("trigger-customer-fetch", TriggerSideComputeOperator.class)
                .build();

        WorkStation<String, FinalOutput> joinStation = Stations
                .processingOperation("join-sidecompute-and-taskhistory", JoinUsingContextOperator.class)
                .addProcessor(SideComputeWaitProcessor.builder("customer-profile").build())
                .parameter(JoinUsingContextOperator::getCustomerParam,
                           (Function<WorkerParamsInjector.InterpretationContext<String>, CustomerDto>) iCtx -> iCtx
                                   .getSideCompute().get("customer-profile", CustomerDto.class))
                .build();

        AssemblyLine<String, FinalOutput> pipeline = AssemblyLines.<String>createAssemblyLine("customer-enrichment")
                .version("1.0.0")
                .configuration(AssemblyLine.Configuration.builder()
                        .eventHandling(EventHandlingDefinition.builder()
                                .sideComputer(sideComputer)
                                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                                        .shutdownTimeout(Duration.ofSeconds(2))
                                        .build())
                                .build())
                        .build())
                .then(triggerStation)
                .then(joinStation)
                .build();

        AssemblyLineEngine pipelineEngine = AssemblyLineEngine.builder()
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .resourceFactory(resourceFactory).extensionResolver(new RuntimeExtensionResolver(List.of()))
                .executionContextRegistry(executionContextRegistry).build();

        RunRequest request = RunRequest.builder()
                .input("42")
                .context(Map.of("tenantId", "tenant-a"))
                .with(cacheExtension)
                .build();

        ExecutionResult<FinalOutput> firstResult = pipelineEngine.execute(pipeline, request);

        assertThat(firstResult.isSuccess()).isTrue();
        assertThat(firstResult.getResult()).isEqualTo(new FinalOutput("John", "ORD-42"));
        assertThat(triggerExecutions).hasValue(1);
        assertThat(joinExecutions).hasValue(1);
        assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);

        ExecutionResult<FinalOutput> secondResult = pipelineEngine.execute(pipeline, request);

        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John", "ORD-42"));
        assertThat(triggerExecutions).hasValue(1);
        assertThat(joinExecutions).hasValue(1);
        assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);
    }

    static final class TriggerSideComputeOperator implements Operator<String, String> {
        private final AtomicInteger executions;

        TriggerSideComputeOperator(AtomicInteger executions) {
            this.executions = executions;
        }

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            executions.incrementAndGet();
            return input;
        }
    }

    static final class JoinUsingContextOperator implements Operator<String, FinalOutput> {
        private final AtomicInteger executions;
        private final TaskHistoryApi taskHistoryApi;
        private WorkerParamsInjector.Parameter<CustomerDto> customer = WorkerParamsInjector.Parameter
                .<CustomerDto>newBuilder().build();

        JoinUsingContextOperator(AtomicInteger executions, TaskHistoryApi taskHistoryApi) {
            this.executions = executions;
            this.taskHistoryApi = taskHistoryApi;
        }

        @Override
        public FinalOutput transform(String input, StationExecutionContext operationExecution) {
            executions.incrementAndGet();

            TaskHistoryResult<OrderDto> order = taskHistoryApi.get("order:" + input, OrderDto.class);

            return new FinalOutput(customer.getValue().name(), order.value().orderCode());
        }

        public WorkerParamsInjector.Parameter<CustomerDto> getCustomerParam() {
            return customer;
        }
    }

    static final class TaskHistoryExpirySideComputeHandler<T>
            implements SideComputeHandler<StationFinishedEvent, TaskHistoryResult<T>> {
        @Override
        public void handle(String sideComputeKey,
                           StationFinishedEvent event,
                           TaskHistoryResult<T> value,
                           ExecutionContext executionContext) {

            Object trackerObj = executionContext.getContext()
                    .get(AssemblyLineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER);

            if (trackerObj instanceof ExpirableDependencyTracker tracker) {
                if (value == null || value.expiresAt() == null) {
                    tracker.recordMissingExpiry("sidecompute:" + sideComputeKey);
                } else {
                    tracker.recordConsumed("sidecompute:" + sideComputeKey, value.expiresAt());
                }
            }
        }
    }

    static final class FakeRawTaskHistoryApi implements RawTaskHistoryApi {
        private final Map<String, TaskHistoryResult<?>> values;
        private final AtomicInteger totalCalls = new AtomicInteger();

        FakeRawTaskHistoryApi(Map<String, TaskHistoryResult<?>> values) {
            this.values = values;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> TaskHistoryResult<T> get(String key, Class<T> type) {
            totalCalls.incrementAndGet();
            return (TaskHistoryResult<T>) values.get(key);
        }

        int totalCalls() {
            return totalCalls.get();
        }
    }

    static final class TestResourceFactory implements ResourceFactory {
        private final TriggerSideComputeOperator triggerOperator;
        private final JoinUsingContextOperator joinOperator;

        TestResourceFactory(TriggerSideComputeOperator triggerOperator, JoinUsingContextOperator joinOperator) {
            this.triggerOperator = triggerOperator;
            this.joinOperator = joinOperator;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getResource(Class<T> clazz) {
            if (clazz.equals(TriggerSideComputeOperator.class)) {
                return (T) triggerOperator;
            }
            if (clazz.equals(JoinUsingContextOperator.class)) {
                return (T) joinOperator;
            }
            throw new IllegalArgumentException("Unsupported resource: " + clazz);
        }
    }

    record CustomerDto(String name) {}

    record OrderDto(String orderCode) {}

    record FinalOutput(String customerName, String orderCode) {}
}
