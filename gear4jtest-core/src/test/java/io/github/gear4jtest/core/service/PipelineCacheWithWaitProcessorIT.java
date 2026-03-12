package io.github.gear4jtest.core.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.github.gear4jtest.core.engine.core.ExtensionRegistry;
import io.github.gear4jtest.core.engine.core.PipelineEngine;
import io.github.gear4jtest.core.engine.core.RunRequest;
import io.github.gear4jtest.core.engine.core.RunnerStackBuilder;
import io.github.gear4jtest.core.engine.core.StrategyRegistry;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventListener;
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
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheKeyFactory;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCachePolicy;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheRuntimeKeys;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.github.gear4jtest.core.model.EventBus;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.model.ExecutionResult;
import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.WorkStation;
import io.github.gear4jtest.core.model.WorkerParamsInjector;
import io.github.gear4jtest.core.sidecompute.SideComputeHandler;
import io.github.gear4jtest.core.sidecompute.SideComputeListener;
import io.github.gear4jtest.core.sidecompute.SideComputeWaitProcessor;
import io.github.gear4jtest.core.sidecompute.SideComputer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineCacheWithWaitProcessorIT {

    @Test
    void should_wait_side_compute_via_processor_and_cache_pipeline_result() {
        InMemoryPipelineCacheRepository cacheRepository = new InMemoryPipelineCacheRepository();

        PipelineCacheExtension cacheExtension =
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
                        cacheRepository);

        FakeRawTaskHistoryApi rawTaskHistoryApi =
                new FakeRawTaskHistoryApi(
                        Map.of(
                                "customer:42",
                                new TaskHistoryResult<>(
                                        new CustomerDto("John"),
                                        Instant.now().plus(Duration.ofMinutes(15))),
                                "order:42",
                                new TaskHistoryResult<>(
                                        new OrderDto("ORD-42"),
                                        Instant.now().plus(Duration.ofMinutes(10)))));

        TaskHistoryApi trackingTaskHistoryApi = new TrackingTaskHistoryApi(rawTaskHistoryApi);

        AtomicInteger triggerExecutions = new AtomicInteger();
        AtomicInteger joinExecutions = new AtomicInteger();

        TriggerSideComputeOperator triggerOperator = new TriggerSideComputeOperator(triggerExecutions);
        JoinUsingContextOperator joinOperator =
                new JoinUsingContextOperator(joinExecutions, trackingTaskHistoryApi);

        ResourceFactory resourceFactory = new TestResourceFactory(triggerOperator, joinOperator);

        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();

        SideComputer<TaskHistoryResult<CustomerDto>, CustomerDto> sideComputer =
                SideComputer.<TaskHistoryResult<CustomerDto>>builder(
                                "trigger-customer-fetch",
                                "customer-profile")
                        .computer(event -> trackingTaskHistoryApi.get("customer:" + event.getOutput(), CustomerDto.class))
                        .addHandler(new TaskHistoryExpirySideComputeHandler<>())
                        .map(TaskHistoryResult::value)
                        .build();

        SideComputeListener sideComputeListener =
                new SideComputeListener(List.of(sideComputer), executionContextRegistry);

        EventBus synchronousBus = new SynchronousEventBus(List.of(sideComputeListener));

        WorkStation<String, String> triggerStation =
                ElementModelBuilders
                        .<String, String, TriggerSideComputeOperator>processingOperation(
                                "trigger-customer-fetch",
                                TriggerSideComputeOperator.class)
                        .build();

        WorkStation<String, FinalOutput> joinStation =
                ElementModelBuilders
                        .<String, FinalOutput, JoinUsingContextOperator>processingOperation(
                                "join-sidecompute-and-taskhistory",
                                JoinUsingContextOperator.class)
                        .addProcessor(SideComputeWaitProcessor.builder("customer-profile").build())
                        .parameter(JoinUsingContextOperator::getCustomerParam, (Function<WorkerParamsInjector.InterpretationContext<String>, CustomerDto>) iCtx -> iCtx.getSideCompute().get("customer-profile", CustomerDto.class))
                        .build();

        AssemblyLine<String, FinalOutput> pipeline =
                ElementModelBuilders.<String>createAssemblyLine("customer-enrichment")
                        .version("1.0.0")
                        .configuration(
                                AssemblyLine.Configuration.builder()
                                        .eventHandling(
                                                EventHandlingDefinition.builder()
                                                        .bus(synchronousBus)
                                                        .build())
                                        .build())
                        .then(triggerStation)
                        .then(joinStation)
                        .build();

        PipelineEngine pipelineEngine =
                PipelineEngine.builder()
                        .stackBuilder(
                                new RunnerStackBuilder(StrategyRegistry.defaultRegistry()))
                        .resourceFactory(resourceFactory)
                        .globalExtensions(new ExtensionRegistry(List.of()))
                        .executionContextRegistry(executionContextRegistry)
                        .build();

        RunRequest request =
                RunRequest.builder()
                        .input("42")
                        .context(Map.of("tenantId", "tenant-a"))
                        .with(cacheExtension)
                        .build();

        // 1er run
        var currentTime = System.currentTimeMillis();
        ExecutionResult<FinalOutput> firstResult = pipelineEngine.execute(pipeline, request);
        System.out.println(System.currentTimeMillis() - currentTime);

        assertThat(firstResult.isSuccess()).isTrue();
        assertThat(firstResult.getResult()).isEqualTo(new FinalOutput("John", "ORD-42"));
        assertThat(triggerExecutions).hasValue(1);
        assertThat(joinExecutions).hasValue(1);
        assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);

        // 2e run => cache pipeline
        currentTime = System.currentTimeMillis();
        ExecutionResult<FinalOutput> secondResult = pipelineEngine.execute(pipeline, request);
        System.out.println(System.currentTimeMillis() - currentTime);

        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John", "ORD-42"));

        // aucune réexécution métier
        assertThat(triggerExecutions).hasValue(1);
        assertThat(joinExecutions).hasValue(1);

        // aucune requête taskHistory supplémentaire
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

    /**
     * Ici, l'opérateur NE WAIT PAS lui-même.
     * Il suppose que SideComputeWaitProcessor a déjà fait le travail.
     * Il lit juste la donnée side-compute disponible dans le contexte.
     */
    static final class JoinUsingContextOperator implements Operator<String, FinalOutput> {
        private final AtomicInteger executions;
        private final TaskHistoryApi taskHistoryApi;

        private WorkerParamsInjector.Parameter<CustomerDto> customer = WorkerParamsInjector.Parameter.<CustomerDto>newBuilder().build();

        JoinUsingContextOperator(AtomicInteger executions, TaskHistoryApi taskHistoryApi) {
            this.executions = executions;
            this.taskHistoryApi = taskHistoryApi;
        }

        @Override
        public FinalOutput transform(String input, StationExecutionContext operationExecution) {
            executions.incrementAndGet();

            // À adapter selon le point exact où ton SideComputeWaitProcessor range la donnée.
            // Idée attendue :
            // - soit dans ExecutionContext.context
            // - soit dans un param injecté
            // - soit dans une capability/context technique
            //
            // Exemple si le processor range la valeur dans le context global :
//            CustomerDto customer =
//                    (CustomerDto) operationExecution.getGlobalContext()
//                            .getContext()
//                            .get("customer-profile");

            TaskHistoryResult<OrderDto> order =
                    taskHistoryApi.get("order:" + input, OrderDto.class);

            return new FinalOutput(customer.getValue().name(), order.value().orderCode());
        }

        public WorkerParamsInjector.Parameter<CustomerDto> getCustomerParam() {
            return customer;
        }
    }

    static final class TaskHistoryExpirySideComputeHandler<T>
            implements SideComputeHandler<TaskHistoryResult<T>> {

        @Override
        public void handle(
                String sideComputeKey,
                OperationCompletedEvent event,
                TaskHistoryResult<T> value,
                io.github.gear4jtest.core.model.ExecutionContext executionContext) {

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

        TestResourceFactory(
                TriggerSideComputeOperator triggerOperator,
                JoinUsingContextOperator joinOperator) {
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

    static final class SynchronousEventBus implements EventBus {
        private final List<EventListener<?>> listeners;

        SynchronousEventBus(List<EventListener<?>> listeners) {
            this.listeners = listeners;
        }

        @Override
        public void run() {}

        @Override
        public void stopBus() {}

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void acceptEvent(Event event) {
            for (EventListener listener : listeners) {
                if (listener.isAcceptable(event)) {
                    listener.handleEvent(event);
                }
            }
        }
    }

    record CustomerDto(String name) {}
    record OrderDto(String orderCode) {}
    record FinalOutput(String customerName, String orderCode) {}
}