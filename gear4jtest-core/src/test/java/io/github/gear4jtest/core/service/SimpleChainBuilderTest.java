package io.github.gear4jtest.core.service;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FailurePolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Errors;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.service.steps.Step10;
import io.github.gear4jtest.core.service.steps.Step11;
import io.github.gear4jtest.core.service.steps.Step12;
import io.github.gear4jtest.core.service.steps.Step13;
import io.github.gear4jtest.core.service.steps.Step3;
import io.github.gear4jtest.core.service.steps.Step7;
import io.github.gear4jtest.core.service.steps.Step8;
import io.github.gear4jtest.core.service.steps.Step9;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.AssemblyLines.chain;
import static io.github.gear4jtest.core.api.util.Events.eventConfiguration;
import static io.github.gear4jtest.core.api.util.Events.eventHandling;
import static io.github.gear4jtest.core.api.util.RuntimeContracts.configuration;
import static io.github.gear4jtest.core.api.util.Stations.branch;
import static io.github.gear4jtest.core.api.util.Stations.container;
import static io.github.gear4jtest.core.api.util.Stations.ifElseContainer;
import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class SimpleChainBuilderTest {
    @Test
    void pipelineWithSkipIteratorAndEventSubscription_shouldComplete() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step3", Step3.class).parameter(Step3::getParam, "a")
                        .onError(Errors.<String>ignore(Exception.class)
                                .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                                .action(() -> System.out.println("Error occurred!")).build())
                        .skipIf((input, ctx) -> input.equals("a")).transformer((a, ctx) -> new HashMap<>()).build())
                .then(processingOperation("step8", Step8.class).build())
                .then(processingOperation("step9", Step9.class).build())
                .then(Stations.<List<Integer>>iterate("iterator")
                        .iterableFunction(Function.identity())
                        .sequence(chain("sequence", processingOperation("step10", Step10.class).build()).build())
                        .collector(Collectors.toList())
                        .build())
                .configuration(configuration().eventHandling(eventHandling()
                        .subscription(EventSubscription.on(Event.class, new TestEventListener()::handleEvent))
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        Map<String, Object> context = new HashMap<>() {
            {
                put("a", 45612);
            }
        };

        RuntimeExtensionResolver runtimeExtensionResolver = new RuntimeExtensionResolver(null);
        RunnerChainFactory runnerChainFactory = new RunnerChainFactory(StrategyRegistry.defaultRegistry());
        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();
        ResourceFactory resourceFactory = new TestResourceFactory();
        AssemblyLineEngine engine = AssemblyLineEngine.builder()
                .runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder()
                .input("b")
                .context(context)
                .resourceFactory(resourceFactory)
                .build();

        // When
        ExecutionResult<List<List<String>>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(ExecutionResult::getResult)
                .isInstanceOf(List.class)
                .asList()
                .hasSize(1)
                .first()
                .isInstanceOf(List.class)
                .asList()
                .contains("");
    }

    @Test
    void pipelineWithEventSubscription_shouldPublishParameterEvents() {
        // Given
        var testEventListener = new TestEventListener();
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step3", Step3.class).parameter(Step3::getParam, "a")
                        .onError(Errors.<String>ignore(Exception.class)
                                .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                                .action(() -> System.out.println("Error occurred!")).build())
                        .skipIf((input, ctx) -> input.equals("a")).transformer((a, ctx) -> new HashMap<>()).build())
                .then(processingOperation("step8", Step8.class).build())
                .then(processingOperation("step9", Step9.class).build())
                .then(Stations.<List<Integer>>iterate("iterator").iterableFunction(Function.identity())
                        .sequence(chain("sequence", processingOperation("step10", Step10.class).build()).build())
                        .collector(Collectors.toList()).build())
                .configuration(configuration().eventHandling(eventHandling()
                        .subscription(EventSubscription.on(Event.class, testEventListener::handleEvent))
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        Map<String, Object> context = new HashMap<>() {
            {
                put("a", 45612);
            }
        };

        RuntimeExtensionResolver runtimeExtensionResolver = new RuntimeExtensionResolver(null);
        RunnerChainFactory runnerChainFactory = new RunnerChainFactory(StrategyRegistry.defaultRegistry());
        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();
        ResourceFactory resourceFactory = new TestResourceFactory();
        AssemblyLineEngine engine = AssemblyLineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<List<List<String>>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(1).first().isInstanceOf(List.class).asList().contains("");

        awaitUntilAsserted(() -> assertThat(testEventListener.getCounter()).isEqualTo(15));
    }

    @Test
    void test_container_two_sublines() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(container(String.class)
                        .withBranch(branch("1",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                                   .build()))
                        .withBranch(branch("2",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "b")
                                                   .build()))
                        .returns(results -> List.of(results.get("1", String.class), results.get("2", String.class))))
                .configuration(configuration().eventHandling(eventHandling()
                        .subscription(EventSubscription.on(Event.class, new TestEventListener()::handleEvent))
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        Map<String, Object> context = new HashMap<>() {
            {
                put("a", 45612);
            }
        };

        RuntimeExtensionResolver runtimeExtensionResolver = new RuntimeExtensionResolver(null);
        RunnerChainFactory runnerChainFactory = new RunnerChainFactory(StrategyRegistry.defaultRegistry());
        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();
        ResourceFactory resourceFactory = new TestResourceFactory();
        AssemblyLineEngine engine = AssemblyLineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(2).containsExactly("c", "b");
    }

    @Test
    void test_container_two_paralleled_sublines() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(container(String.class, Executors.newFixedThreadPool(2))
                        .withBranch(branch("1",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                                   .build()))
                        .withBranch(branch("2",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "b")
                                                   .build()))
                        .returns(results -> List.of(results.get("1", String.class), results.get("2", String.class))))
                .configuration(configuration().eventHandling(eventHandling()
                        .subscription(EventSubscription.on(Event.class, new TestEventListener()::handleEvent))
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        Map<String, Object> context = new HashMap<>() {
            {
                put("a", 45612);
            }
        };

        RuntimeExtensionResolver runtimeExtensionResolver = new RuntimeExtensionResolver(null);
        RunnerChainFactory runnerChainFactory = new RunnerChainFactory(StrategyRegistry.defaultRegistry());
        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();
        ResourceFactory resourceFactory = new TestResourceFactory();
        AssemblyLineEngine engine = AssemblyLineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(2).containsExactly("c", "b");
    }

    @Test
    void test_container_if_else_container() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(ifElseContainer(String.class)
                        .conditionally("when-a-c",
                                       processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                               .build(),
                                       (input, ctx) -> input.equals("a"))
                        .conditionally("when-a-cd",
                                       processingOperation("step11", Step11.class).parameter(Step11::getParam, "cd")
                                               .build(),
                                       (input, ctx) -> input.equals("a"))
                        .elseOp("otherwise",
                                processingOperation("step11", Step11.class).parameter(Step11::getParam, "b").build()))
                .configuration(configuration().eventHandling(eventHandling()
                        .subscription(EventSubscription.on(Event.class, new TestEventListener()::handleEvent))
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        Map<String, Object> context = new HashMap<>() {
            {
                put("a", 45612);
            }
        };

        RuntimeExtensionResolver runtimeExtensionResolver = new RuntimeExtensionResolver(null);
        RunnerChainFactory runnerChainFactory = new RunnerChainFactory(StrategyRegistry.defaultRegistry());
        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();
        ResourceFactory resourceFactory = new TestResourceFactory();
        AssemblyLineEngine engine = AssemblyLineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<String> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isEqualTo("c");
    }

    @Test
    void should_execute_fallback_branch_only_when_primary_failed() {
        // Given
        var primary = branch("1", processingOperation("primary", FailingPrimary.class).build());
        var fallback = branch("2", processingOperation("fallback", FallbackStep.class).build());
        var sequentialContainer = container(String.class)
                .flowConfig(new FlowConfig(FailurePolicy.IGNORE_AND_CONTINUE, StopPolicy.PROPAGATE_STOP,
                        CancelPolicy.PROPAGATE_CANCEL))
                .withBranch(primary)
                .withBranch(fallback, (input, ctx, siblings) -> siblings.isFailed("1"))
                .returns(results -> Arrays.asList(results.get(primary), results.get(fallback)));

        var assemblyLine = AssemblyLines.<String>createAssemblyLine("container-branch-condition")
                .then(chain("root-sequence", sequentialContainer).build()).build();

        RuntimeExtensionResolver runtimeExtensionResolver = new RuntimeExtensionResolver(null);
        RunnerChainFactory runnerChainFactory = new RunnerChainFactory(StrategyRegistry.defaultRegistry());
        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();
        ResourceFactory resourceFactory = new TestResourceFactory();

        AssemblyLineEngine engine = AssemblyLineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();
        var request = RunRequest.builder().input("input").resourceFactory(resourceFactory)
                .with(new PersistenceExtension(InMemoryExecutionManager.builder()
                        .repository(repository)
                        .build()))
                .build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResult()).as("primary failed, fallback must run").containsExactly(null, "fallback-ok");

        List<StationLogRecord> allLogs = repository.findAllLogsByRunId(result.getExecution().getId(),
                                                                       PageRequest.first(50));

        assertThat(allLogs).extracting(StationLogRecord::operationId, StationLogRecord::status)
                .contains(tuple("primary", StationLogStatus.FAILED), tuple("fallback", StationLogStatus.SUCCEEDED));
    }

    public static class FailingPrimary implements Operator<String, String> {
        @Override
        public String transform(String input,
                                io.github.gear4jtest.core.api.context.StationExecutionContext operationExecution) {
            throw new IllegalStateException("primary failed");
        }
    }

    public static class FallbackStep implements Operator<String, String> {
        @Override
        public String transform(String input,
                                io.github.gear4jtest.core.api.context.StationExecutionContext operationExecution) {
            return "fallback-ok";
        }
    }

    public static class Whatever<T> implements Serializable {
        private T object;

        public Whatever(T object) {
            this.object = object;
        }

        public T getObject() {
            return object;
        }
    }

    public static class TestResourceFactory implements ResourceFactory {
        static final Map<Class<?>, Object> BEANS;

        static {
            BEANS = new HashMap<>();
            BEANS.put(Step3.class, new Step3());
            BEANS.put(Step7.class, new Step7());
            BEANS.put(Step8.class, new Step8());
            BEANS.put(Step9.class, new Step9());
            BEANS.put(Step10.class, new Step10());
            BEANS.put(Step11.class, new Step11());
            BEANS.put(Step12.class, new Step12());
            BEANS.put(Step13.class, new Step13());
            BEANS.put(FailingPrimary.class, new FailingPrimary());
            BEANS.put(FallbackStep.class, new FallbackStep());
        }

        @Override
        public <T> T getResource(Class<T> clazz) {
            return (T) BEANS.get(clazz);
        }
    }

    private static void awaitUntilAsserted(Runnable assertion) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        AssertionError lastFailure = null;
        while (System.nanoTime() < deadlineNanos) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                lastFailure = e;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        assertion.run();
    }

    public static class TestEventListener {
        private final AtomicInteger counter = new AtomicInteger();

        void handleEvent(Event e) {
            System.out.println(e.getExecutionId() + " " + e.getName() + " " + e.getId());
            counter.incrementAndGet();
        }

        public int getCounter() {
            return counter.get();
        }
    }
}
