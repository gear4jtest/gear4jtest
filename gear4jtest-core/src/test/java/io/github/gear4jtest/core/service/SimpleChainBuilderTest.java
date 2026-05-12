package io.github.gear4jtest.core.service;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FailurePolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.exception.AssemblyLineException;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.AssemblyRunView;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
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
import org.postgresql.ds.PGSimpleDataSource;

import static io.github.gear4jtest.core.api.util.ElementModelBuilders.chain;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.configuration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.container;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.eventConfiguration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.eventHandling;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.ifElseContainer;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.persistenceConfiguration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// handle factory for step / processor... configuration
public class SimpleChainBuilderTest {
    private static StationLogRecord getRecordByOperationId(List<StationLogRecord> logs, String operationId) {
        return logs.stream().filter(log -> operationId.equals(log.operationId())).findFirst()
                .orElseThrow(() -> new AssertionError("No StationLogRecord found for operationId=" + operationId));
    }

    @Test
    public void test_v2() throws AssemblyLineException {
        // Given
        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processingOperation("step3", Step3.class).parameter(Step3::getParam, "a")
                        .onError(ElementModelBuilders.<String>ignore(Exception.class)
                                .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                                .action(() -> System.out.println("Error occurred!")).build())
                        .skipIf((input, ctx) -> input.equals("a")).transformer((a, ctx) -> new HashMap<>()).build())
                // .then(fatalSignal(typeMap(String.class, String.class))
                // .condition(ctx -> ctx.getItem().containsKey("a"))
                // .build())
                .then(processingOperation("step8", Step8.class).build())
                .then(processingOperation("step9", Step9.class).build())
                .then(ElementModelBuilders.<List<Integer>>iterate("iterator").iterableFunction(Function.identity())
                        .pipeline(chain("sequence", processingOperation("step10", Step10.class).build()).build())
                        .collector(Collectors.toList()).build())
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
        PipelineEngine engine = PipelineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<List<List<String>>> result = engine.execute(assemblyLine, request);
        // ExecutionResult<List<List<String>>> result = assemblyLine.execute("b",
        // context, new TestResourceFactory(),
        // new InMemoryExecutionManager());

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(1).first().isInstanceOf(List.class).asList().contains("");
    }

    @Test
    public void test_v2_event_management() throws AssemblyLineException, InterruptedException {
        // Given
        var testEventListener = new TestEventListener();
        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processingOperation("step3", Step3.class).parameter(Step3::getParam, "a")
                        .onError(ElementModelBuilders.<String>ignore(Exception.class)
                                .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                                .action(() -> System.out.println("Error occurred!")).build())
                        .skipIf((input, ctx) -> input.equals("a")).transformer((a, ctx) -> new HashMap<>()).build())
                // .then(fatalSignal(typeMap(String.class, String.class))
                // .condition(ctx -> ctx.getItem().containsKey("a"))
                // .build())
                .then(processingOperation("step8", Step8.class).build())
                .then(processingOperation("step9", Step9.class).build())
                .then(ElementModelBuilders.<List<Integer>>iterate("iterator").iterableFunction(Function.identity())
                        .pipeline(chain("sequence", processingOperation("step10", Step10.class).build()).build())
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
        PipelineEngine engine = PipelineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<List<List<String>>> result = engine.execute(assemblyLine, request);
        // ExecutionResult<List<List<String>>> result = assemblyLine.execute("b",
        // context, new TestResourceFactory(),
        // new InMemoryExecutionManager());

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(1).first().isInstanceOf(List.class).asList().contains("");

        TimeUnit.MILLISECONDS.sleep(500);
        assertThat(testEventListener.getCounter()).isEqualTo(15);
    }

    @Test
    public void test_v2_with_datasource() {
        // Given
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUser("postgres");
        dataSource.setPassword("postgres");
        dataSource.setUrl("jdbc:postgresql://localhost:5432/gear4jtest");
        dataSource.setDatabaseName("gear4jtest");

        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processingOperation("step3", Step3.class).parameter(Step3::getParam, "a")
                        .onError(ElementModelBuilders.<String>ignore(Exception.class)
                                .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                                .action(() -> System.out.println("Error occurred!")).build())
                        .skipIf((input, ctx) -> input.equals("a")).transformer((a, ctx) -> new HashMap<>()).build())
                // .then(fatalSignal(typeMap(String.class, String.class))
                // .condition(ctx -> ctx.getItem().containsKey("a"))
                // .build())
                .then(processingOperation("step8", Step8.class).build())
                .then(processingOperation("step9", Step9.class).build())
                .then(ElementModelBuilders.<List<Integer>>iterate("iterator").iterableFunction(Function.identity())
                        .pipeline(chain("sequence", processingOperation("step10", Step10.class).build()).build())
                        .collector(Collectors.toList()).build())
                .configuration(configuration().eventHandling(eventHandling()
                        .subscription(EventSubscription.on(Event.class, new TestEventListener()::handleEvent))
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .persistence(persistenceConfiguration().storeResultObject(true).build()).build())
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
        PipelineEngine engine = PipelineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory)
                .with(new PersistenceExtension(new DatabaseExecutionManager(dataSource))).build();

        // When
        ExecutionResult<List<List<String>>> result = engine.execute(assemblyLine, request);
        // ExecutionResult<List<List<String>>> result =
        // assemblyLine.execute("b", context, new TestResourceFactory(), new
        // DatabaseExecutionManager(dataSource));

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(1).first().isInstanceOf(List.class).asList().contains("");

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource);

        var pipelineExecution = repository.findById(result.getExecution().getId());
        assertThat(pipelineExecution).isPresent().get()
                .extracting(AssemblyRunRecord::id, AssemblyRunRecord::pipelineId, AssemblyRunRecord::inputParams,
                            AssemblyRunRecord::context, AssemblyRunRecord::result, AssemblyRunRecord::status)
                .containsExactly(result.getExecution().getId(), "test", context, context, List.of(List.of("")),
                                 ExecutionStatus.SUCCEEDED);

        var pipelineDetails = repository.findViewById(result.getExecution().getId());
        assertThat(pipelineDetails).isPresent().get().extracting(AssemblyRunView::getRootOperations).asList().hasSize(1)
                .first().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(StationLogRecord.class))
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::parentOperationId, StationLogRecord::status, StationLogRecord::context)
                .containsExactly(result.getExecution().getId(), "test:root", null, StationLogStatus.SUCCEEDED,
                                 Map.of());

        List<StationLogRecord> rootLogs = repository.findRootLogsByRunId(result.getExecution().getId());
        var rootSequenceExecutionRecord = getRecordByOperationId(rootLogs, "test:root");

        List<StationLogRecord> rootChildren = repository.findChildLogsByRunId(result.getExecution().getId(),
                                                                              rootSequenceExecutionRecord.id());

        assertThat(rootChildren)
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::parentOperationId, StationLogRecord::status, StationLogRecord::context)
                .containsExactly(tuple(result.getExecution().getId(), "step3", rootSequenceExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()),
                                 tuple(result.getExecution().getId(), "step8", rootSequenceExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()),
                                 tuple(result.getExecution().getId(), "step9", rootSequenceExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()),
                                 tuple(result.getExecution().getId(), "iterator", rootSequenceExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()));

        assertThat(repository.countChildLogsByRunId(result.getExecution().getId(), rootSequenceExecutionRecord.id()))
                .isEqualTo(4);

        var iteratorExecutionRecord = getRecordByOperationId(rootChildren, "iterator");

        List<StationLogRecord> iteratorChildren = repository.findChildLogsByRunId(result.getExecution().getId(),
                                                                                  iteratorExecutionRecord.id());

        assertThat(iteratorChildren)
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::parentOperationId, StationLogRecord::status, StationLogRecord::context)
                .containsExactly(tuple(result.getExecution().getId(), "sequence", iteratorExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()));

        assertThat(repository.countChildLogsByRunId(result.getExecution().getId(), iteratorExecutionRecord.id()))
                .isEqualTo(1);

        var sequenceExecutionRecord = getRecordByOperationId(iteratorChildren, "sequence");

        List<StationLogRecord> sequenceChildren = repository.findChildLogsByRunId(result.getExecution().getId(),
                                                                                  sequenceExecutionRecord.id());

        assertThat(sequenceChildren)
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::parentOperationId, StationLogRecord::status, StationLogRecord::context)
                .containsExactly(tuple(result.getExecution().getId(), "step10", sequenceExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()));

        assertThat(repository.countChildLogsByRunId(result.getExecution().getId(), sequenceExecutionRecord.id()))
                .isEqualTo(1);
    }

    @Test
    public void test_container_two_sublines() {
        // Given
        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(container(String.class)
                        .withSubLine("1",
                                     processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                             .build())
                        .withSubLine("2",
                                     processingOperation("step11", Step11.class).parameter(Step11::getParam, "b")
                                             .build())
                        .returns(Arrays::asList))
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
        PipelineEngine engine = PipelineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);
        // ExecutionResult<List<String>> result = assemblyLine.execute("b", context, new
        // TestResourceFactory(), new
        // InMemoryExecutionManager());

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(2).containsExactly("c", "b");
    }

    @Test
    public void test_container_two_paralleled_sublines() throws AssemblyLineException {
        // Given
        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(container(String.class, Executors.newFixedThreadPool(2))
                        .withSubLine("1",
                                     processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                             .build())
                        .withSubLine("2",
                                     processingOperation("step11", Step11.class).parameter(Step11::getParam, "b")
                                             .build())
                        .returns(Arrays::asList))
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
        PipelineEngine engine = PipelineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);
        // ExecutionResult<List<String>> result = assemblyLine.execute("b", context, new
        // TestResourceFactory(), new
        // InMemoryExecutionManager());

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(2).containsExactly("c", "b");
    }

    @Test
    public void test_container_if_else_container() throws AssemblyLineException {
        // Given
        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(ifElseContainer(String.class)
                        .conditionally(processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                .build(), (input, ctx) -> input.equals("a"))
                        .conditionally(processingOperation("step11", Step11.class).parameter(Step11::getParam, "cd")
                                .build(), (input, ctx) -> input.equals("a"))
                        .elseOp(processingOperation("step11", Step11.class).parameter(Step11::getParam, "b").build()))
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
        PipelineEngine engine = PipelineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("b").context(context).resourceFactory(resourceFactory).build();

        // When
        ExecutionResult<String> result = engine.execute(assemblyLine, request);
        // ExecutionResult<String> result = assemblyLine.execute("b", context, new
        // TestResourceFactory(), new
        // InMemoryExecutionManager());

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isEqualTo("c");
    }

    @Test
    void should_execute_fallback_branch_only_when_primary_failed() {
        // Given
        var sequentialContainer = container(String.class)
                .flowConfig(new FlowConfig(FailurePolicy.IGNORE_AND_CONTINUE, StopPolicy.PROPAGATE_STOP,
                        CancelPolicy.PROPAGATE_CANCEL))
                .withSubLine("1", processingOperation("primary", FailingPrimary.class).build())
                .withSubLine("2", processingOperation("fallback", FallbackStep.class).build(),
                             (input, ctx, siblings) -> siblings.isFailed("1"))
                .returns(Arrays::asList);

        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("container-branch-condition")
                .then(chain("root-sequence", sequentialContainer).build()).build();

        RuntimeExtensionResolver runtimeExtensionResolver = new RuntimeExtensionResolver(null);
        RunnerChainFactory runnerChainFactory = new RunnerChainFactory(StrategyRegistry.defaultRegistry());
        ExecutionContextRegistry executionContextRegistry = new ExecutionContextRegistry();
        ResourceFactory resourceFactory = new TestResourceFactory();

        PipelineEngine engine = PipelineEngine.builder().runnerChainFactory(runnerChainFactory)
                .resourceFactory(resourceFactory).extensionResolver(runtimeExtensionResolver)
                .executionContextRegistry(executionContextRegistry).build();

        var request = RunRequest.builder().input("input").resourceFactory(resourceFactory)
                .with(new PersistenceExtension(new InMemoryExecutionManager())).build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResult()).as("primary failed, fallback must run").containsExactly(null, "fallback-ok");

        List<StationLogRecord> allLogs = InMemoryAssemblyRunRepository.INSTANCE
                .findAllLogsByRunId(result.getExecution().getId());

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
            // Gear4j itself should handle the initialization of its proper beans...
            // BEANS.put(OperationParamsInjector.class, new OperationParamsInjector());
            // BEANS.put(OperationRetriever.class, new OperationRetriever());
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

    public static class TestEventListener {
        public int COUNTER;

        public TestEventListener() {
            COUNTER = 0;
        }

        public void handleEvent(Event e) {
            System.out.println(e.getExecutionId() + " " + e.getName() + " " + e.getId());
            COUNTER++;
        }

        public int getCounter() {
            return COUNTER;
        }
    }
}
