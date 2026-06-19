package io.github.gear4jtest.core.service;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.pipeline.InlinePolicy;
import io.github.gear4jtest.core.api.pipeline.PipelineRuntimeContract;
import io.github.gear4jtest.core.api.pipeline.RuntimeRequirement;
import io.github.gear4jtest.core.api.station.PipelineCallStation;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.AssemblyRunRepository;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.ElementModelBuilders.chain;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.configuration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.container;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.eventConfiguration;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.ifElseContainer;
import static io.github.gear4jtest.core.api.util.ElementModelBuilders.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Tag("integration")
class FullPipelineRuntimeIT {
    private static final PageRequest ALL = PageRequest.first(100);

    @Test
    void fullPipelineRun_withInMemoryPersistenceAndEvents_shouldExerciseStationTypesAndPersistHierarchy() {
        // Given
        InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();
        AssemblyRunManager manager = InMemoryExecutionManager.builder().repository(repository).build();

        // When / Then
        runFullPipelineScenario(manager, repository);
    }

    @Test
    void fullPipelineRun_withH2Persistence_shouldExerciseJdbcProviderWithoutDocker() {
        // Given
        DataSource dataSource = new DriverManagerBackedDataSource("jdbc:h2:mem:gear4j_full_runtime_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1", "sa", "");
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
        DatabaseAssemblyRunRepository repository = DatabaseAssemblyRunRepository.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();

        // When / Then
        runFullPipelineScenario(manager, repository);
    }

    private static void runFullPipelineScenario(AssemblyRunManager manager, AssemblyRunRepository repository) {
        EventCollector eventCollector = new EventCollector();
        EventHandlingDefinition eventHandling = eventHandlingDefinition(eventCollector);
        ExecutorService parallelExecutor = Executors.newFixedThreadPool(2);
        try {
            AssemblyLine<List<String>, String> inlineChild = inlineChildPipeline(eventHandling);
            AssemblyLine<String, String> nestedChild = nestedChildPipeline(eventHandling);
            AssemblyLine<String, String> pipeline = fullPipeline(eventHandling, parallelExecutor, inlineChild,
                                                                 nestedChild);
            PipelineEngine engine = engine(manager);

            // When
            ExecutionResult<String> result = engine.execute(pipeline, RunRequest.builder().input("go").build());

            // Then
            assertThat(result.isSuccess()).as("pipeline should succeed, error=%s", result.getError()).isTrue();
            assertThat(result.getResult()).isEqualTo("GO|PREFIX|LEFT#+GO|PREFIX|RIGHT#|nested");
            assertPersistedRuns(repository, result.getExecution().getId());
            assertParentStationLogs(repository, result.getExecution().getId());
            assertNestedRunLogs(repository, result.getExecution().getId());
            assertPublishedRepresentativeEvents(eventCollector);
        } finally {
            parallelExecutor.shutdownNow();
            manager.shutdown();
        }
    }

    private static PipelineEngine engine(AssemblyRunManager manager) {
        return PipelineEngine.builder()
                .resourceFactory(new ReflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(List.of(new PersistenceExtension(manager))))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();
    }

    private static EventHandlingDefinition eventHandlingDefinition(EventCollector collector) {
        return ElementModelBuilders.eventHandling()
                .subscription(collector.stationStarted())
                .subscription(collector.stationFinished())
                .subscription(collector.parameterResolved())
                .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .shutdownTimeout(Duration.ofSeconds(3))
                        .build())
                .build();
    }

    private static AssemblyLine<String, String> fullPipeline(EventHandlingDefinition eventHandling,
                                                             ExecutorService parallelExecutor,
                                                             AssemblyLine<List<String>, String> inlineChild,
                                                             AssemblyLine<String, String> nestedChild) {
        return ElementModelBuilders.<String>createAssemblyLine("full-runtime")
                .configuration(configuration()
                        .eventHandling(eventHandling)
                        .runtimeContract(PipelineRuntimeContract.builder()
                                .inlinePolicy(InlinePolicy.ALWAYS_FORBIDDEN)
                                .providedRequirement(RuntimeRequirement.defaultEventHandling())
                                .build())
                        .build())
                .then(processingOperation("prefix", SuffixOperator.class)
                        .parameter(SuffixOperator::getSuffix, "|prefix")
                        .build())
                .then(container(String.class, parallelExecutor)
                        .withSubLine("left", processingOperation("left-suffix", SuffixOperator.class)
                                .parameter(SuffixOperator::getSuffix, "|left")
                                .build())
                        .withSubLine("right", processingOperation("right-suffix", SuffixOperator.class)
                                .parameter(SuffixOperator::getSuffix, "|right")
                                .build())
                        .returns((left, right) -> Arrays.stream(new Object[] { left, right })
                                .map(Object::toString)
                                .sorted()
                                .collect(Collectors.joining("+"))))
                .then(ifElseContainer(String.class)
                        .conditionally("contains-left", processingOperation("uppercase", UppercaseOperator.class)
                                .build(), (input, ctx) -> input.contains("left"))
                        .elseOp("missing-left", processingOperation("lowercase", LowercaseOperator.class).build()))
                .then(new SignalStation.Builder<String>()
                        .id("noop-stop-signal")
                        .type(SignalType.STOP)
                        .condition(signal -> false)
                        .build())
                .then(ElementModelBuilders.<String>iterate("split-iterator")
                        .iterableFunction(input -> List.of(input.split("\\+")))
                        .pipeline(chain("each-item",
                                        processingOperation("append-item-marker", SuffixOperator.class)
                                                .parameter(SuffixOperator::getSuffix, "#")
                                                .build())
                                .build())
                        .collector(Collectors.toList())
                        .build())
                .then(PipelineCallStation.inline("inline-join", inlineChild))
                .then(PipelineCallStation.nestedRun("nested-call", nestedChild))
                .build();
    }

    private static AssemblyLine<List<String>, String> inlineChildPipeline(EventHandlingDefinition eventHandling) {
        return ElementModelBuilders.<List<String>>createAssemblyLine("inline-child")
                .configuration(configuration()
                        .eventHandling(eventHandling)
                        .runtimeContract(PipelineRuntimeContract.builder()
                                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                                .mandatoryRequirement(RuntimeRequirement.defaultEventHandling())
                                .build())
                        .build())
                .then(processingOperation("join-items", JoinOperator.class).build())
                .build();
    }

    private static AssemblyLine<String, String> nestedChildPipeline(EventHandlingDefinition eventHandling) {
        return ElementModelBuilders.<String>createAssemblyLine("nested-child")
                .configuration(configuration().eventHandling(eventHandling).build())
                .then(processingOperation("nested-suffix", SuffixOperator.class)
                        .parameter(SuffixOperator::getSuffix, "|nested")
                        .build())
                .build();
    }

    private static void assertPersistedRuns(AssemblyRunRepository repository, UUID parentRunId) {
        assertThat(repository.findById(parentRunId)).get()
                .extracting(AssemblyRunRecord::pipelineId, AssemblyRunRecord::status, AssemblyRunRecord::result)
                .containsExactly("full-runtime", ExecutionStatus.SUCCEEDED,
                                 "GO|PREFIX|LEFT#+GO|PREFIX|RIGHT#|nested");

        assertThat(repository.findByPipelineId("nested-child", ALL))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
                    assertThat(run.parentExecutionId()).isEqualTo(parentRunId);
                    assertThat(run.rootExecutionId()).isEqualTo(parentRunId);
                    assertThat(run.parentStationLogId()).isNotNull();
                    assertThat(run.result()).isEqualTo("GO|PREFIX|LEFT#+GO|PREFIX|RIGHT#|nested");
                });
    }

    private static void assertParentStationLogs(AssemblyRunRepository repository, UUID parentRunId) {
        List<StationLogRecord> logs = repository.findAllLogsByRunId(parentRunId, ALL);
        assertThat(logs)
                .extracting(StationLogRecord::operationId, StationLogRecord::status)
                .contains(tuple("full-runtime:root", StationLogStatus.SUCCEEDED),
                          tuple("prefix", StationLogStatus.SUCCEEDED),
                          tuple("left-suffix", StationLogStatus.SUCCEEDED),
                          tuple("right-suffix", StationLogStatus.SUCCEEDED),
                          tuple("uppercase", StationLogStatus.SUCCEEDED),
                          tuple("noop-stop-signal", StationLogStatus.SUCCEEDED),
                          tuple("split-iterator", StationLogStatus.SUCCEEDED),
                          tuple("append-item-marker", StationLogStatus.SUCCEEDED),
                          tuple("inline-join", StationLogStatus.SUCCEEDED),
                          tuple("join-items", StationLogStatus.SUCCEEDED),
                          tuple("nested-call", StationLogStatus.SUCCEEDED));

        StationLogRecord nestedCall = findOperation(logs, "nested-call");
        assertThat(nestedCall.context()).containsKeys("pipeline.call.mode", "pipeline.call.childExecutionId");
        assertThat(nestedCall.context()).containsEntry("pipeline.call.mode", "NESTED_RUN");
    }

    private static void assertNestedRunLogs(AssemblyRunRepository repository, UUID parentRunId) {
        AssemblyRunRecord nestedRun = repository.findByPipelineId("nested-child", ALL).get(0);
        assertThat(repository.findAllLogsByRunId(nestedRun.id(), ALL))
                .extracting(StationLogRecord::pipelineExecutionId, StationLogRecord::operationId,
                            StationLogRecord::status)
                .contains(tuple(nestedRun.id(), "nested-suffix", StationLogStatus.SUCCEEDED));
        assertThat(nestedRun.parentExecutionId()).isEqualTo(parentRunId);
    }

    private static void assertPublishedRepresentativeEvents(EventCollector collector) {
        assertThat(collector.events())
                .contains("START:prefix", "FINISH:prefix:SUCCEEDED", "PARAM:prefix:false",
                          "START:split-iterator", "FINISH:inline-join:SUCCEEDED",
                          "START:nested-suffix", "FINISH:nested-suffix:SUCCEEDED");
    }

    private static StationLogRecord findOperation(List<StationLogRecord> logs, String operationId) {
        return logs.stream()
                .filter(log -> operationId.equals(log.operationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing station log " + operationId));
    }

    public static final class SuffixOperator implements Operator<String, String> {
        private final WorkerParamsInjector.Parameter<String> suffix = WorkerParamsInjector.Parameter
                .<String>newBuilder()
                .defaultValue("")
                .build();

        public WorkerParamsInjector.Parameter<String> getSuffix() {
            return suffix;
        }

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + suffix.getValue();
        }
    }

    public static final class UppercaseOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input.toUpperCase();
        }
    }

    public static final class LowercaseOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input.toLowerCase();
        }
    }

    public static final class JoinOperator implements Operator<List<String>, String> {
        @Override
        public String transform(List<String> input, StationExecutionContext operationExecution) {
            return String.join("+", input);
        }
    }

    private static final class ReflectiveResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class EventCollector {
        private final List<String> events = new CopyOnWriteArrayList<>();

        EventSubscription<io.github.gear4jtest.core.event.StationStartedEvent> stationStarted() {
            return EventSubscription.on(io.github.gear4jtest.core.event.StationStartedEvent.class,
                                        event -> events.add("START:" + event.getOperationId()));
        }

        EventSubscription<io.github.gear4jtest.core.event.StationFinishedEvent> stationFinished() {
            return EventSubscription.on(io.github.gear4jtest.core.event.StationFinishedEvent.class,
                                        event -> events
                                                .add("FINISH:" + event.getOperationId() + ":" + event.getStatus()));
        }

        EventSubscription<io.github.gear4jtest.core.event.ParameterResolvedEvent> parameterResolved() {
            return EventSubscription.on(io.github.gear4jtest.core.event.ParameterResolvedEvent.class,
                                        event -> events
                                                .add("PARAM:" + event.getOperationId() + ":" + event.isCacheHit()));
        }

        List<String> events() {
            return events;
        }
    }

    private record DriverManagerBackedDataSource(String url,
                                                 String username,
                                                 String password)
            implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
