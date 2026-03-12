package io.github.gear4jtest.core.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.engine.core.RunRequest;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.event.OperationCompletedEvent;
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
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.ExecutionResult;
import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.sidecompute.SideComputeHandler;
import io.github.gear4jtest.core.sidecompute.SideComputeListener;
import io.github.gear4jtest.core.sidecompute.SideComputer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineCacheWithSideComputeIntegrationTest {

    @Test
    void should_cache_pipeline_output_after_first_run_and_short_circuit_second_run() {
        // given
        InMemoryPipelineCacheRepository repository = new InMemoryPipelineCacheRepository();

        PipelineCacheExtension extension =
                new PipelineCacheExtension(
                        new PipelineCachePolicy(
                                true,
                                NoDependencyCachePolicy.DO_NOT_CACHE,
                                null),
                        new PipelineCacheKeyFactory(
                                new JsonSha256FingerprintStrategy<>(),
                                new WhitelistedContextFingerprintStrategy(
                                        List.of("tenantId"),
                                        new JsonSha256FingerprintStrategy<>())),
                        repository);

        FakeRawTaskHistoryApi rawTaskHistoryApi = new FakeRawTaskHistoryApi(
                Map.of(
                        "customer:42", new TaskHistoryResult<>(new CustomerDto("John"), Instant.now().plus(Duration.ofMinutes(15))),
                        "order:42", new TaskHistoryResult<>(new CustomerDto("Order-John"), Instant.now().plus(Duration.ofMinutes(10)))
                )
        );

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

        // first run
        ExecutionContext firstCtx = newExecutionContext(pipeline.getId(), request.getContext());

        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        registry.register(firstCtx);

        SideComputer<TaskHistoryResult<CustomerDto>, CustomerDto> sideComputer =
                SideComputer.<TaskHistoryResult<CustomerDto>>builder("fetch-customer", "customer-profile")
                        .computer(event -> taskHistoryApi.get("customer:" + event.getOutput(), CustomerDto.class))
                        .addHandler(new TaskHistoryExpirySideComputeHandler<>())
                        .map(TaskHistoryResult::value)
                        .build();

        SideComputeListener listener = new SideComputeListener(List.of(sideComputer), registry);

        ExecutionResult<FinalOutput> firstResult =
                extension.aroundRun(
                        pipeline,
                        request,
                        firstCtx,
                        () -> {
                            businessExecutions.incrementAndGet();

                            // simulate completed station -> triggers side-compute
                            listener.handleEvent(
                                    new OperationCompletedEvent(
                                            pipeline.getId(),
                                            firstCtx.getExecutionId(),
                                            "fetch-customer",
                                            "42",
                                            "42"));

                            // final mapped value available in side-compute future
                            CustomerDto customer =
                                    firstCtx.getSideComputeContext()
                                            .<CustomerDto>getOrCreateFuture("customer-profile")
                                            .join();

                            // direct taskHistory call from "operator business code"
                            TaskHistoryResult<CustomerDto> order =
                                    taskHistoryApi.get("order:42", CustomerDto.class);

                            FinalOutput output = new FinalOutput(customer.name(), order.value().name());
                            firstCtx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
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

        // then
        assertThat(firstResult.isSuccess()).isTrue();
        assertThat(firstResult.getResult()).isEqualTo(new FinalOutput("John", "Order-John"));
        assertThat(businessExecutions).hasValue(1);
        assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);
        assertThat(repository.findValid(expectedKey, Instant.now())).isPresent();

        // second run -> should hit cache, no business re-execution
        ExecutionContext secondCtx = newExecutionContext(pipeline.getId(), request.getContext());

        ExecutionResult<FinalOutput> secondResult =
                extension.aroundRun(
                        pipeline,
                        request,
                        secondCtx,
                        () -> {
                            businessExecutions.incrementAndGet();
                            FinalOutput unexpected = new FinalOutput("SHOULD", "NOT-RUN");
                            secondCtx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
                            secondCtx.getPipelineExecution().setResult(unexpected);
                            return ExecutionResult.success(unexpected, secondCtx.getPipelineExecution());
                        });

        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John", "Order-John"));
        assertThat(businessExecutions).hasValue(1);
        assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);
    }

    @Test
    void should_not_cache_pipeline_when_side_compute_dependency_has_no_expiry() {
        // given
        InMemoryPipelineCacheRepository repository = new InMemoryPipelineCacheRepository();

        PipelineCacheExtension extension =
                new PipelineCacheExtension(
                        new PipelineCachePolicy(
                                true,
                                NoDependencyCachePolicy.DO_NOT_CACHE,
                                null),
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

        // side-compute rich result without expiry
        SideComputer<RichCustomerPayload, CustomerDto> sideComputer =
                SideComputer.<RichCustomerPayload>builder("fetch-customer", "customer-profile")
                        .computer(event -> new RichCustomerPayload(new CustomerDto("John"), null))
                        .addHandler(new RichCustomerPayloadExpiryHandler())
                        .map(RichCustomerPayload::value)
                        .build();

        // first run
        ExecutionContext firstCtx = newExecutionContext(pipeline.getId(), request.getContext());
        ExecutionContextRegistry registry = new ExecutionContextRegistry();
        registry.register(firstCtx);
        SideComputeListener listener = new SideComputeListener(List.of(sideComputer), registry);

        ExecutionResult<FinalOutput> firstResult =
                extension.aroundRun(
                        pipeline,
                        request,
                        firstCtx,
                        () -> {
                            businessExecutions.incrementAndGet();

                            listener.handleEvent(
                                    new OperationCompletedEvent(
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
                            firstCtx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
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

        // second run -> should execute again because no cache was saved
        ExecutionContext secondCtx = newExecutionContext(pipeline.getId(), request.getContext());
        ExecutionContextRegistry secondRegistry = new ExecutionContextRegistry();
        secondRegistry.register(secondCtx);
        SideComputeListener secondListener = new SideComputeListener(List.of(sideComputer), secondRegistry);

        ExecutionResult<FinalOutput> secondResult =
                extension.aroundRun(
                        pipeline,
                        request,
                        secondCtx,
                        () -> {
                            businessExecutions.incrementAndGet();

                            secondListener.handleEvent(
                                    new OperationCompletedEvent(
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
                            secondCtx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
                            secondCtx.getPipelineExecution().setResult(output);
                            return ExecutionResult.success(output, secondCtx.getPipelineExecution());
                        });

        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John", "no-order"));
        assertThat(businessExecutions).hasValue(2);
    }

    private static ExecutionContext newExecutionContext(String pipelineId, Map<String, Object> context) {
        AssemblyRun assemblyRun = new AssemblyRun(UUID.randomUUID(), pipelineId, context);
        ExecutionContext executionContext =
                new ExecutionContext(
                        UUID.randomUUID(),
                        pipelineId,
                        new EventManager(List.of()),
                        new NoOpResourceFactory(),
                        assemblyRun);
        executionContext.getContext().putAll(context);
        return executionContext;
    }

    private record CustomerDto(String name) {}

    private record FinalOutput(String customerName, String orderName) {}

    private record RichCustomerPayload(CustomerDto value, Instant expiresAt) {}

    private static final class RichCustomerPayloadExpiryHandler
            implements SideComputeHandler<RichCustomerPayload> {

        @Override
        public void handle(
                String sideComputeKey,
                OperationCompletedEvent event,
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
            implements SideComputeHandler<TaskHistoryResult<T>> {

        @Override
        public void handle(
                String sideComputeKey,
                OperationCompletedEvent event,
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