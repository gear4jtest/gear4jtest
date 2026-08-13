package io.github.gear4jtest.core.service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FailurePolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.event.StationSkippedEvent;
import io.github.gear4jtest.core.event.StationStartedEvent;
import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.service.CoreRuntimeTestSupport.FailingPrimary;
import io.github.gear4jtest.core.service.CoreRuntimeTestSupport.FallbackStep;
import io.github.gear4jtest.core.service.steps.Step11;
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

class ContainerDslRuntimeTest {
    @Test
    void sequentialContainerWithTwoBranches_shouldAggregateBranchResults() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(container("two-sublines-container", String.class)
                        .withBranch(branch("1",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                                   .build()))
                        .withBranch(branch("2",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "b")
                                                   .build()))
                        .returns(results -> List.of(results.get("1", String.class), results.get("2", String.class))))
                .configuration(configuration().eventHandling(eventHandling()
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        ResourceFactory resourceFactory = CoreRuntimeTestSupport.testResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
        var request = RunRequest.builder()
                .input("b")
                .context(CoreRuntimeTestSupport.contextWithA())
                .resourceFactory(resourceFactory)
                .build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResult()).containsExactly("c", "b");
    }

    @Test
    void parallelContainerWithTwoBranches_shouldAggregateBranchResults() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(container("parallel-two-sublines-container", String.class, Executors.newFixedThreadPool(2))
                        .withBranch(branch("1",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "c")
                                                   .build()))
                        .withBranch(branch("2",
                                           processingOperation("step11", Step11.class).parameter(Step11::getParam, "b")
                                                   .build()))
                        .returns(results -> List.of(results.get("1", String.class), results.get("2", String.class))))
                .configuration(configuration().eventHandling(eventHandling()
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        ResourceFactory resourceFactory = CoreRuntimeTestSupport.testResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
        var request = RunRequest.builder()
                .input("b")
                .context(CoreRuntimeTestSupport.contextWithA())
                .resourceFactory(resourceFactory)
                .build();

        // When
        ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResult()).containsExactly("c", "b");
    }

    @Test
    void ifElseContainer_shouldExecuteFirstMatchingBranchOnly() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step11", Step11.class).parameter(Step11::getParam, "a").build())
                .then(ifElseContainer("test-if-else-container", String.class)
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
                        .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build()).build())
                        .build())
                .build();

        ResourceFactory resourceFactory = CoreRuntimeTestSupport.testResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
        var request = RunRequest.builder()
                .input("b")
                .context(CoreRuntimeTestSupport.contextWithA())
                .resourceFactory(resourceFactory)
                .build();

        // When
        ExecutionResult<String> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isEqualTo("c");
    }

    @Test
    void nonEligibleContainerBranch_shouldBePersistedAndEmitSkippedEvent() {
        // Given
        CopyOnWriteArrayList<StationSkippedEvent> skippedEvents = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<StationStartedEvent> startedEvents = new CopyOnWriteArrayList<>();
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("container-skipped-branch-observability")
                .then(container("observed-container", String.class)
                        .withBranch(branch("not-eligible",
                                           processingOperation("never-run", Step11.class).parameter(Step11::getParam,
                                                                                                    "x")
                                                   .build()),
                                    (input, ctx) -> false)
                        .withBranch(branch("eligible",
                                           processingOperation("eligible-run", Step11.class).parameter(Step11::getParam,
                                                                                                       "ok")
                                                   .build()))
                        .returns(results -> Arrays.asList(results.get("not-eligible", String.class),
                                                          results.get("eligible", String.class))))
                .configuration(configuration().eventHandling(eventHandling()
                        .on(StationSkippedEvent.class, skippedEvents::add)
                        .on(StationStartedEvent.class, startedEvents::add)
                        .build()).build())
                .build();

        ResourceFactory resourceFactory = CoreRuntimeTestSupport.testResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
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
        assertThat(result.getResult()).containsExactly(null, "ok");

        List<StationLogRecord> allLogs = repository.findAllLogsByRunId(result.getExecution().getId(),
                                                                       PageRequest.first(50));
        assertThat(allLogs).extracting(StationLogRecord::operationId, StationLogRecord::status)
                .contains(tuple("never-run", StationLogStatus.SKIPPED),
                          tuple("eligible-run", StationLogStatus.SUCCEEDED));

        assertThat(skippedEvents).hasSize(1);
        StationSkippedEvent event = skippedEvents.get(0);
        assertThat(event.getOperationId()).isEqualTo("never-run");
        assertThat(event.getBranchId()).isEqualTo("not-eligible");
        assertThat(event.getReason()).isEqualTo(StationSkipReason.CONDITION_NOT_SATISFIED);
        assertThat(startedEvents).extracting(StationStartedEvent::getOperationId)
                .doesNotContain("never-run");
    }

    @Test
    void containerBranchCondition_shouldRunFallbackOnlyWhenPrimaryFailed() {
        // Given
        var primary = branch("1", processingOperation("primary", FailingPrimary.class).build());
        var fallback = branch("2", processingOperation("fallback", FallbackStep.class).build());
        var sequentialContainer = container("fallback-container", String.class)
                .flowConfig(new FlowConfig(FailurePolicy.IGNORE_AND_CONTINUE, StopPolicy.PROPAGATE_STOP,
                        CancelPolicy.PROPAGATE_CANCEL))
                .withBranch(primary)
                .withBranch(fallback, (input, ctx, siblings) -> siblings.isFailed("1"))
                .returns(results -> Arrays.asList(results.get(primary), results.get(fallback)));

        var assemblyLine = AssemblyLines.<String>createAssemblyLine("container-branch-condition")
                .then(chain("root-sequence", sequentialContainer).build()).build();

        ResourceFactory resourceFactory = CoreRuntimeTestSupport.testResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
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
}
