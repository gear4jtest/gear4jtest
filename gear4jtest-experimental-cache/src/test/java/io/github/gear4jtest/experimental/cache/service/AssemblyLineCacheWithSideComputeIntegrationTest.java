package io.github.gear4jtest.experimental.cache.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.sidecompute.SideComputeHandler;
import io.github.gear4jtest.core.sidecompute.SideComputer;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCacheExtension;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCacheKey;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCacheKeyFactory;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCachePolicy;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCacheRuntimeKeys;
import io.github.gear4jtest.experimental.cache.assemblylinecache.InMemoryAssemblyLineCacheRepository;
import io.github.gear4jtest.experimental.cache.assemblylinecache.NoDependencyCachePolicy;
import io.github.gear4jtest.experimental.cache.history.ExpirableDependencyTracker;
import io.github.gear4jtest.experimental.cache.history.fingerprint.JsonSha256FingerprintStrategy;
import io.github.gear4jtest.experimental.cache.history.fingerprint.WhitelistedContextFingerprintStrategy;
import io.github.gear4jtest.experimental.cache.history.taskhistory.RawTaskHistoryApi;
import io.github.gear4jtest.experimental.cache.history.taskhistory.TaskHistoryApi;
import io.github.gear4jtest.experimental.cache.history.taskhistory.TaskHistoryResult;
import io.github.gear4jtest.experimental.cache.history.taskhistory.TrackingTaskHistoryApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineCacheWithSideComputeIntegrationTest {
    @SafeVarargs
    private static EventManager eventManager(ExecutionContextRegistry registry,
                                             SideComputer<?, ?, ?>... sideComputers) {
        EventHandlingDefinition.Builder builder = EventHandlingDefinition.builder()
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .shutdownTimeout(Duration.ofSeconds(2)).build());
        for (SideComputer<?, ?, ?> sideComputer : sideComputers) {
            builder.sideComputer(sideComputer);
        }
        return new EventManager(builder.build(), registry);
    }

    private static ExecutionContext newExecutionContext(String assemblyLineId,
                                                        Map<String, Object> context,
                                                        EventManager eventManager) {
        AssemblyRunTrace assemblyRun = new AssemblyRunTrace(UUID.randomUUID(), assemblyLineId, context);
        ExecutionContext executionContext = ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId(assemblyLineId)
                .services(new ExecutionServices(eventManager, new NoOpResourceFactory()))
                .assemblyRun(assemblyRun)
                .build();
        executionContext.getContext().putAll(context);
        return executionContext;
    }

    private static StationFinishedEvent stationSuccessEvent(String assemblyLineId,
                                                            UUID executionId,
                                                            String operationId,
                                                            Object input,
                                                            Object output) {
        return new StationFinishedEvent(assemblyLineId, executionId, UUID.randomUUID(), operationId, null, null, input,
                StationLogStatus.SUCCEEDED, output, null);
    }

    @Test
    void should_cache_pipeline_output_after_first_run_and_short_circuit_second_run() {
        InMemoryAssemblyLineCacheRepository repository = cacheRepository();

        AssemblyLineCacheExtension extension = new AssemblyLineCacheExtension(
                new AssemblyLineCachePolicy(true, NoDependencyCachePolicy.DO_NOT_CACHE, null),
                new AssemblyLineCacheKeyFactory(new JsonSha256FingerprintStrategy<>(),
                        new WhitelistedContextFingerprintStrategy(List.of("tenantId"),
                                new JsonSha256FingerprintStrategy<>())),
                repository);

        FakeRawTaskHistoryApi rawTaskHistoryApi = new FakeRawTaskHistoryApi(
                Map.of("customer:42",
                       new TaskHistoryResult<>(new CustomerDto("John"), Instant.now().plus(Duration.ofMinutes(15))),
                       "order:42", new TaskHistoryResult<>(new CustomerDto("Order-John"),
                               Instant.now().plus(Duration.ofMinutes(10)))));

        TaskHistoryApi taskHistoryApi = new TrackingTaskHistoryApi(rawTaskHistoryApi);

        AssemblyLine<String, FinalOutput> pipeline = AssemblyLine.<String, FinalOutput>builder("customer-enrichment")
                .version("1.0.0").build();

        RunRequest<String> request = RunRequest.builder().input("42").context(Map.of("tenantId", "tenant-a"))
                .build();

        AtomicInteger businessExecutions = new AtomicInteger();

        ExecutionContextRegistry firstRegistry = new ExecutionContextRegistry();
        SideComputer<StationFinishedEvent, TaskHistoryResult<CustomerDto>, CustomerDto> sideComputer = SideComputer
                .<TaskHistoryResult<CustomerDto>>onStationSuccess("fetch-customer", "customer-profile")
                .computer(event -> taskHistoryApi.get("customer:" + event.getOutput(), CustomerDto.class))
                .addHandler(new TaskHistoryExpirySideComputeHandler<>()).map(TaskHistoryResult::value).build();
        EventManager firstEventManager = eventManager(firstRegistry, sideComputer);
        ExecutionContext firstCtx = newExecutionContext(pipeline.getId(), request.getContext(), firstEventManager);
        firstRegistry.register(firstCtx);

        try {
            ExecutionResult<FinalOutput> firstResult = extension.aroundRun(pipeline, request, firstCtx, () -> {
                businessExecutions.incrementAndGet();

                firstEventManager.publish(stationSuccessEvent(pipeline.getId(), firstCtx.getExecutionId(),
                                                              "fetch-customer", "42", "42"));

                CustomerDto customer = firstCtx.getSideComputeContext()
                        .<CustomerDto>getOrCreateFuture("customer-profile").join();

                TaskHistoryResult<CustomerDto> order = taskHistoryApi.get("order:42", CustomerDto.class);

                FinalOutput output = new FinalOutput(customer.name(), order.value().name());
                return ExecutionResult.success(output, firstCtx.getAssemblyLineExecution());
            });

            AssemblyLineCacheKey expectedKey = new AssemblyLineCacheKeyFactory(new JsonSha256FingerprintStrategy<>(),
                    new WhitelistedContextFingerprintStrategy(List.of("tenantId"),
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
        ExecutionContext secondCtx = newExecutionContext(pipeline.getId(), request.getContext(), secondEventManager);

        try {
            ExecutionResult<FinalOutput> secondResult = extension.aroundRun(pipeline, request, secondCtx, () -> {
                businessExecutions.incrementAndGet();
                FinalOutput unexpected = new FinalOutput("SHOULD", "NOT-RUN");
                return ExecutionResult.success(unexpected, secondCtx.getAssemblyLineExecution());
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
        InMemoryAssemblyLineCacheRepository repository = cacheRepository();

        AssemblyLineCacheExtension extension = new AssemblyLineCacheExtension(
                new AssemblyLineCachePolicy(true, NoDependencyCachePolicy.DO_NOT_CACHE, null),
                new AssemblyLineCacheKeyFactory(new JsonSha256FingerprintStrategy<>(),
                        new WhitelistedContextFingerprintStrategy(List.of("tenantId"),
                                new JsonSha256FingerprintStrategy<>())),
                repository);

        AssemblyLine<String, FinalOutput> pipeline = AssemblyLine.<String, FinalOutput>builder("customer-enrichment")
                .version("1.0.0").build();

        RunRequest<String> request = RunRequest.builder().input("42").context(Map.of("tenantId", "tenant-a"))
                .build();

        AtomicInteger businessExecutions = new AtomicInteger();

        SideComputer<StationFinishedEvent, RichCustomerPayload, CustomerDto> sideComputer = SideComputer
                .<RichCustomerPayload>onStationSuccess("fetch-customer", "customer-profile")
                .computer(event -> new RichCustomerPayload(new CustomerDto("John"), null))
                .addHandler(new RichCustomerPayloadExpiryHandler()).map(RichCustomerPayload::value).build();

        ExecutionContextRegistry firstRegistry = new ExecutionContextRegistry();
        EventManager firstEventManager = eventManager(firstRegistry, sideComputer);
        ExecutionContext firstCtx = newExecutionContext(pipeline.getId(), request.getContext(), firstEventManager);
        firstRegistry.register(firstCtx);

        try {
            ExecutionResult<FinalOutput> firstResult = extension.aroundRun(pipeline, request, firstCtx, () -> {
                businessExecutions.incrementAndGet();

                firstEventManager.publish(stationSuccessEvent(pipeline.getId(), firstCtx.getExecutionId(),
                                                              "fetch-customer", "42", "42"));

                CustomerDto customer = firstCtx.getSideComputeContext()
                        .<CustomerDto>getOrCreateFuture("customer-profile").join();

                FinalOutput output = new FinalOutput(customer.name(), "no-order");
                return ExecutionResult.success(output, firstCtx.getAssemblyLineExecution());
            });

            AssemblyLineCacheKey expectedKey = new AssemblyLineCacheKeyFactory(new JsonSha256FingerprintStrategy<>(),
                    new WhitelistedContextFingerprintStrategy(List.of("tenantId"),
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
            ExecutionResult<FinalOutput> secondResult = extension.aroundRun(pipeline, request, secondCtx, () -> {
                businessExecutions.incrementAndGet();

                secondEventManager.publish(stationSuccessEvent(pipeline.getId(), secondCtx.getExecutionId(),
                                                               "fetch-customer", "42", "42"));

                CustomerDto customer = secondCtx.getSideComputeContext()
                        .<CustomerDto>getOrCreateFuture("customer-profile").join();

                FinalOutput output = new FinalOutput(customer.name(), "no-order");
                return ExecutionResult.success(output, secondCtx.getAssemblyLineExecution());
            });

            assertThat(secondResult.isSuccess()).isTrue();
            assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John", "no-order"));
            assertThat(businessExecutions).hasValue(2);
        } finally {
            secondEventManager.shutdown();
        }
    }

    private record CustomerDto(String name) {}

    private record FinalOutput(String customerName, String orderName) {}

    private static InMemoryAssemblyLineCacheRepository cacheRepository() {
        return new InMemoryAssemblyLineCacheRepository(128, knownImmutableTestOutputCloner());
    }

    private static PayloadCloner knownImmutableTestOutputCloner() {
        return new PayloadCloner() {
            @Override
            public <T> T clonePayload(T payload) {
                return payload;
            }
        };
    }

    private record RichCustomerPayload(CustomerDto value, Instant expiresAt) {}

    private static final class RichCustomerPayloadExpiryHandler
            implements SideComputeHandler<StationFinishedEvent, RichCustomerPayload> {
        @Override
        public void handle(String sideComputeKey,
                           StationFinishedEvent event,
                           RichCustomerPayload value,
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

    private static final class TaskHistoryExpirySideComputeHandler<T>
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
