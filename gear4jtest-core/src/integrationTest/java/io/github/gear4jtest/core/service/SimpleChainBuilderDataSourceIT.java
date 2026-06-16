package io.github.gear4jtest.core.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.AssemblyRunView;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.service.steps.Step10;
import io.github.gear4jtest.core.service.steps.Step3;
import io.github.gear4jtest.core.service.steps.Step8;
import io.github.gear4jtest.core.service.steps.Step9;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.github.gear4jtest.core.api.util.ElementModelBuilders.chain;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.configuration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.eventConfiguration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.eventHandling;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.persistenceConfiguration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Tag("integration")
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
public class SimpleChainBuilderDataSourceIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleChainBuilderDataSourceIT.class);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gear4jtest")
            .withUsername("gear4jtest")
            .withPassword("gear4jtest");

    private static StationLogRecord getRecordByOperationId(List<StationLogRecord> logs, String operationId) {
        return logs.stream()
                .filter(log -> operationId.equals(log.operationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No StationLogRecord found for operationId=" + operationId));
    }

    @Test
    public void pipelineWithJdbcPersistence_shouldPersistRunAndStationLogs() {
        // Given
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setUrl(POSTGRES.getJdbcUrl());

        var assemblyLine = ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processingOperation("step3", Step3.class).parameter(Step3::getParam, "a")
                        .onError(ElementModelBuilders.<String>ignore(Exception.class)
                                .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                                .action(() -> LOGGER.info("Error occurred!")).build())
                        .skipIf((input, ctx) -> input.equals("a")).transformer((a, ctx) -> new HashMap<>()).build())
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
                .with(new PersistenceExtension(
                        new DatabaseExecutionManager(dataSource, Gear4jDatabaseDialect.POSTGRESQL)))
                .build();

        // When
        ExecutionResult<List<List<String>>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(1).first().isInstanceOf(List.class).asList().contains("");

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);

        var pipelineExecution = repository.findById(result.getExecution().getId());
        assertThat(pipelineExecution).isPresent().get()
                .extracting(AssemblyRunRecord::id, AssemblyRunRecord::pipelineId, AssemblyRunRecord::inputParams,
                            AssemblyRunRecord::context, AssemblyRunRecord::result, AssemblyRunRecord::status)
                .containsExactly(result.getExecution().getId(), "test", context, context, List.of(List.of("")),
                                 ExecutionStatus.SUCCEEDED);

        var pipelineDetails = repository.findViewById(result.getExecution().getId(), PageRequest.first(100));
        assertThat(pipelineDetails).isPresent().get().extracting(AssemblyRunView::getRootOperations).asList().hasSize(1)
                .first().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(StationLogRecord.class))
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::parentOperationId, StationLogRecord::status, StationLogRecord::context)
                .containsExactly(result.getExecution().getId(), "test:root", null, StationLogStatus.SUCCEEDED,
                                 Map.of());

        List<StationLogRecord> rootLogs = repository.findRootLogsByRunId(result.getExecution().getId(),
                                                                         PageRequest.first(100));
        var rootSequenceExecutionRecord = getRecordByOperationId(rootLogs, "test:root");

        List<StationLogRecord> rootChildren = repository.findChildLogsByRunId(result.getExecution().getId(),
                                                                              rootSequenceExecutionRecord.id(),
                                                                              PageRequest.first(100));

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
                                                                                  iteratorExecutionRecord.id(),
                                                                                  PageRequest.first(100));

        assertThat(iteratorChildren)
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::parentOperationId, StationLogRecord::status, StationLogRecord::context)
                .containsExactly(tuple(result.getExecution().getId(), "sequence", iteratorExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()));

        assertThat(repository.countChildLogsByRunId(result.getExecution().getId(), iteratorExecutionRecord.id()))
                .isEqualTo(1);

        var sequenceExecutionRecord = getRecordByOperationId(iteratorChildren, "sequence");

        List<StationLogRecord> sequenceChildren = repository.findChildLogsByRunId(result.getExecution().getId(),
                                                                                  sequenceExecutionRecord.id(),
                                                                                  PageRequest.first(100));

        assertThat(sequenceChildren)
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::parentOperationId, StationLogRecord::status, StationLogRecord::context)
                .containsExactly(tuple(result.getExecution().getId(), "step10", sequenceExecutionRecord.id(),
                                       StationLogStatus.SUCCEEDED, Map.of()));

        assertThat(repository.countChildLogsByRunId(result.getExecution().getId(), sequenceExecutionRecord.id()))
                .isEqualTo(1);
    }

    public static class TestResourceFactory implements ResourceFactory {
        static final Map<Class<?>, Object> BEANS;

        static {
            BEANS = new HashMap<>();
            BEANS.put(Step3.class, new Step3());
            BEANS.put(Step8.class, new Step8());
            BEANS.put(Step9.class, new Step9());
            BEANS.put(Step10.class, new Step10());
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
            LOGGER.info(e.getExecutionId() + " " + e.getName() + " " + e.getId());
            COUNTER++;
        }

        public int getCounter() {
            return COUNTER;
        }
    }
}
