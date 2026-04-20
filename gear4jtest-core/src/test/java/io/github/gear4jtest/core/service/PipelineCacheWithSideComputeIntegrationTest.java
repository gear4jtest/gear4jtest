package io.github.gear4jtest.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.extras.history.ExpirableDependencyTracker;
import io.github.gear4jtest.core.extras.history.fingerprint.JsonSha256FingerprintStrategy;
import io.github.gear4jtest.core.extras.history.fingerprint.WhitelistedContextFingerprintStrategy;
import io.github.gear4jtest.core.extras.history.taskhistory.RawTaskHistoryApi;
import io.github.gear4jtest.core.extras.history.taskhistory.TaskHistoryApi;
import io.github.gear4jtest.core.extras.history.taskhistory.TaskHistoryResult;
import io.github.gear4jtest.core.extras.history.taskhistory.TrackingTaskHistoryApi;
import io.github.gear4jtest.core.extras.pipelinecache.InMemoryPipelineCacheRepository;
import io.github.gear4jtest.core.extras.pipelinecache.NoDependencyCachePolicy;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheExtension;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheKey;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheKeyFactory;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCachePolicy;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheRuntimeKeys;
import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.sidecompute.SideComputeHandler;
import io.github.gear4jtest.core.sidecompute.SideComputer;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PipelineCacheWithSideComputeIntegrationTest {

    @Test
    void should_cache_pipeline_output_after_first_run_and_short_circuit_second_run() {
        InMemoryPipelineCacheRepository repository = new InMemoryPipelineCacheRepository();

        PipelineCacheExtension extension =
                new PipelineCacheExtension(
                        new PipelineCachePolicy(true, NoDependencyCachePolicy.DO_NOT_CACHE, null),
                        new PipelineCacheKeyFactory(
                                new JsonSha256FingerprintStrategy<>(),
                                new WhitelistedContextFingerprintStrategy(
                                        List.of("tenantId"),
                                        new JsonSha256FingerprintStrategy<>())),
                        repository);

        FakeRawTaskHistoryApi rawTaskHistoryApi = new FakeRawTaskHistoryApi(
                Map.of(
                        "customer:42", new TaskHistoryResult<>(new CustomerDto("John"), Instant.now().plus(Duration.ofMinutes(15))),
                        "order:42", new TaskHistoryResult<>(new CustomerDto("Order-John"), Instant.now().plus(Duration.ofMinutes(10)))));

        TaskHistoryApi taskHistoryApi = new TrackingTaskHistoryApi(rawTaskHistoryApi);

        AssemblyLine<String, FinalOutput> pipeline =
                AssemblyLine.<String, FinalOutput>builder("customer-enrichment")
                        .version("1.0.0")
                        .build();

        RunRequest request =
                RunRequest.builder()
                        .input("42")
                        .context(Map.of("tenantId", "tenant-a"))
                        .build();

        AtomicInteger businessExecutions = new AtomicInteger();

        ExecutionContextRegistry firstRegistry = new ExecutionContextRegistry();
        SideComputer<StationFinishedEvent, TaskHistoryResult<CustomerDto>, CustomerDto> sideComputer =
                SideComputer.<TaskHistoryResult<CustomerDto>>onStationSuccess("fetch-customer", "customer-profile")
                        .computer(event -> taskHistoryApi.get("customer:" + event.getOutput(), CustomerDto.class))
                        .addHandler(new TaskHistoryExpirySideComputeHandler<>())
                        .map(TaskHistoryResult::value)
                        .build();
        EventManager firstEventManager = eventManager(firstRegistry, sideComputer);
        ExecutionContext firstCtx = newExecutionContext(pipeline.getId(), request.getContext(), firstEventManager);
        firstRegistry.register(firstCtx);

        try {
            ExecutionResult<FinalOutput> firstResult =
                    extension.aroundRun(
                            pipeline,
                            request,
                            firstCtx,
                            () -> {
                                businessExecutions.incrementAndGet();

                                firstEventManager.publish(stationSuccessEvent(
                                        pipeline.getId(),
                                        firstCtx.getExecutionId(),
                                        "fetch-customer",
                                        "42",
                                        "42"));

                                CustomerDto customer =
                                        firstCtx.getSideComputeContext()
                                                .<CustomerDto>getOrCreateFuture("customer-profile")
                                                .join();

                                TaskHistoryResult<CustomerDto> order =
                                        taskHistoryApi.get("order:42", CustomerDto.class);

                                FinalOutput output = new FinalOutput(customer.name(), order.value().name());
                                firstCtx.getPipelineExecution().setStatus(ExecutionStatus.SUCCEEDED);
                                firstCtx.getPipelineExecution().setResult(output);
                                return ExecutionResult.success(output, firstCtx.getPipelineExecution());
                            });

            PipelineCacheKey expectedKey =
                    new PipelineCacheKeyFactory(
                                    new JsonSha256FingerprintStrategy<>(),
                                    new WhitelistedContextFingerprintStrategy(
                                            List.of("tenantId"),
                                            new JsonSha256FingerprintStrategy<>()))
                            .create(pipeline.getId(), pipeline.getVersion(), request.getInput(), firstCtx);

            assertThat(firstResult.isSuccess()).isTrue();
            assertThat(firstResult.getResult()).isEqualTo(new FinalOutput("John", "Order-John"));
            assertThat(businessExecutions).hasValue(1);
            assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);
            assertThat(repository.findValid(expectedKey, Instant.now())).isPresent();
        } finally {
            firstEventManager.shutdown();
        }

        EventManager secondEventManager = eventManager(new ExecutionContextRegistry());
        ExecutionContext secondCtx = newExecutionContext(
                pipeline.getId(), request.getContext(), secondEventManager);

        try {
            ExecutionResult<FinalOutput> secondResult =
                    extension.aroundRun(
                            pipeline,
                            request,
                            secondCtx,
                            () -> {
                                businessExecutions.incrementAndGet();
                                FinalOutput unexpected = new FinalOutput("SHOULD", "NOT-RUN");
                                secondCtx.getPipelineExecution().setStatus(ExecutionStatus.SUCCEEDED);
                                secondCtx.getPipelineExecution().setResult(unexpected);
                                return ExecutionResult.success(unexpected, secondCtx.getPipelineExecution());
                            });

            assertThat(secondResult.isSuccess()).isTrue();
            assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John", "Order-John"));
            assertThat(businessExecutions).hasValue(1);
            assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);
        } finally {
            secondEventManager.shutdown();
        }
    }

    @Test
    void should_not_cache_pipeline_when_side_compute_dependency_has_no_expiry() {
        InMemoryPipelineCacheRepository repository = new InMemoryPipelineCacheRepository();

        PipelineCacheExtension extension =
                new PipelineCacheExtension(
                        new PipelineCachePolicy(true, NoDependencyCachePolicy.DO_NOT_CACHE, null),
                        new PipelineCacheKeyFactory(
                                new JsonSha256FingerprintStrategy<>(),
                                new WhitelistedContextFingerprintStrategy(
                                        List.of("tenantId"),
                                        new JsonSha256FingerprintStrategy<>())),
                        repository);

        AssemblyLine<String, FinalOutput> pipeline =
                AssemblyLine.<String, FinalOutput>builder("customer-enrichment")
                        .version("1.0.0")
                        .build();

        RunRequest request =
                RunRequest.builder()
                        .input("42")
                        .context(Map.of("tenantId", "tenant-a"))
                        .build();

        AtomicInteger businessExecutions = new AtomicInteger();

        SideComputer<StationFinishedEvent, RichCustomerPayload, CustomerDto> sideComputer =
                SideComputer.<RichCustomerPayload>onStationSuccess("fetch-customer", "customer-profile")
                        .computer(event -> new RichCustomerPayload(new CustomerDto("John"), null))
                        .addHandler(new RichCustomerPayloadExpiryHandler())
                        .map(RichCustomerPayload::value)
                        .build();

        ExecutionContextRegistry firstRegistry = new ExecutionContextRegistry();
        EventManager firstEventManager = eventManager(firstRegistry, sideComputer);
        ExecutionContext firstCtx = newExecutionContext(pipeline.getId(), request.getContext(), firstEventManager);
        firstRegistry.register(firstCtx);

        try {
            ExecutionResult<FinalOutput> firstResult =
                    extension.aroundRun(
                            pipeline,
                            request,
                            firstCtx,
                            () -> {
                                businessExecutions.incrementAndGet();

                                firstEventManager.publish(stationSuccessEvent(
                                        pipeline.getId(),
                                        firstCtx.getExecutionId(),
                                        "fetch-customer",
                                        "42",
                                        "42"));

                                CustomerDto customer =
                                        firstCtx.getSideComputeContext()
                                                .<CustomerDto>getOrCreateFuture("customer-profile")
                                                .join();

                                FinalOutput output = new FinalOutput(customer.name(), "no-order");
                                firstCtx.getPipelineExecution().setStatus(ExecutionStatus.SUCCEEDED);
                                firstCtx.getPipelineExecution().setResult(output);
                                return ExecutionResult.success(output, firstCtx.getPipelineExecution());
                            });

            PipelineCacheKey expectedKey =
                    new PipelineCacheKeyFactory(
                                    new JsonSha256FingerprintStrategy<>(),
                                    new WhitelistedContextFingerprintStrategy(
                                            List.of("tenantId"),
                                            new JsonSha256FingerprintStrategy<>()))
                            .create(pipeline.getId(), pipeline.getVersion(), request.getInput(), firstCtx);

            assertThat(firstResult.isSuccess()).isTrue();
            assertThat(firstResult.getResult()).isEqualTo(new FinalOutput("John", "no-order"));
            assertThat(repository.findValid(expectedKey, Instant.now())).isEmpty();
        } finally {
            firstEventManager.shutdown();
        }

        ExecutionContextRegistry secondRegistry = new ExecutionContextRegistry();
        EventManager secondEventManager = eventManager(secondRegistry, sideComputer);
        ExecutionContext secondCtx = newExecutionContext(pipeline.getId(), request.getContext(), secondEventManager);
        secondRegistry.register(secondCtx);

        try {
            ExecutionResult<FinalOutput> secondResult =
                    extension.aroundRun(
                            pipeline,
                            request,
                            secondCtx,
                            () -> {
                                businessExecutions.incrementAndGet();

                                secondEventManager.publish(stationSuccessEvent(
                                        pipeline.getId(),
                                        secondCtx.getExecutionId(),
                                        "fetch-customer",
                                        "42",
                                        "42"));

                                CustomerDto customer =
                                        secondCtx.getSideComputeContext()
                                                .<CustomerDto>getOrCreateFuture("customer-profile")
                                                .join();

                                FinalOutput output = new FinalOutput(customer.name(), "no-order");
                                secondCtx.getPipelineExecution().setStatus(ExecutionStatus.SUCCEEDED);
                                secondCtx.getPipelineExecution().setResult(output);
                                return ExecutionResult.success(output, secondCtx.getPipelineExecution());
                            });

            assertThat(secondResult.isSuccess()).isTrue();
            assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John", "no-order"));
            assertThat(businessExecutions).hasValue(2);
        } finally {
            secondEventManager.shutdown();
        }
    }

    @SafeVarargs
    private static EventManager eventManager(
            ExecutionContextRegistry registry,
            SideComputer<?, ?, ?>... sideComputers) {
        EventHandlingDefinition.Builder builder = EventHandlingDefinition.builder()
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .build());
        for (SideComputer<?, ?, ?> sideComputer : sideComputers) {
            builder.sideComputer(sideComputer);
        }
        return new EventManager(builder.build(), registry);
    }

    private static ExecutionContext newExecutionContext(
            String pipelineId,
            Map<String, Object> context,
            EventManager eventManager) {
        AssemblyRun assemblyRun = new AssemblyRun(UUID.randomUUID(), pipelineId, context);
        ExecutionContext executionContext =
                new ExecutionContext(
                        UUID.randomUUID(),
                        pipelineId,
                        eventManager,
                        new NoOpResourceFactory(),
                        assemblyRun);
        executionContext.getContext().putAll(context);
        return executionContext;
    }

    private static StationFinishedEvent stationSuccessEvent(
            String pipelineId,
            UUID executionId,
            String operationId,
            Object input,
            Object output) {
        return new StationFinishedEvent(
                pipelineId,
                executionId,
                UUID.randomUUID(),
                operationId,
                null,
                null,
                input,
                StationLog.Status.SUCCEEDED,
                output,
                null);
    }

    private record CustomerDto(String name) {}

    private record FinalOutput(String customerName, String orderName) {}

    private record RichCustomerPayload(CustomerDto value, Instant expiresAt) {}

    private static final class RichCustomerPayloadExpiryHandler
            implements SideComputeHandler<StationFinishedEvent, RichCustomerPayload> {

        @Override
        public void handle(
                String sideComputeKey,
                StationFinishedEvent event,
                RichCustomerPayload value,
                ExecutionContext executionContext) {

            Object trackerObj =
                    executionContext.getContext().get(PipelineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER);

            if (trackerObj instanceof ExpirableDependencyTracker tracker) {
                if (value == null || value.expiresAt() == null) {
                    tracker.recordMissingExpiry("sidecompute:" + sideComputeKey);
                } else {
                    tracker.recordConsumed("sidecompute:" + sideComputeKey, value.expiresAt());
                }
            }
        }
    }

    private static final class TaskHistoryExpirySideComputeHandler<T>
            implements SideComputeHandler<StationFinishedEvent, TaskHistoryResult<T>> {

        @Override
        public void handle(
                String sideComputeKey,
                StationFinishedEvent event,
                TaskHistoryResult<T> value,
                ExecutionContext executionContext) {

            Object trackerObj =
                    executionContext.getContext().get(PipelineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER);

            if (trackerObj instanceof ExpirableDependencyTracker tracker) {
                if (value == null || value.expiresAt() == null) {
                    tracker.recordMissingExpiry("sidecompute:" + sideComputeKey);
                } else {
                    tracker.recordConsumed("sidecompute:" + sideComputeKey, value.expiresAt());
                }
            }
        }
    }

    private static final class FakeRawTaskHistoryApi implements RawTaskHistoryApi {

        private final Map<String, TaskHistoryResult<?>> values;
        private final AtomicInteger totalCalls = new AtomicInteger();

        private FakeRawTaskHistoryApi(Map<String, TaskHistoryResult<?>> values) {
            this.values = values;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> TaskHistoryResult<T> get(String key, Class<T> type) {
            totalCalls.incrementAndGet();
            return (TaskHistoryResult<T>) values.get(key);
        }

        public int totalCalls() {
            return totalCalls.get();
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
