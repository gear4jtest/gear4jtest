package io.github.gear4jtest.core.service;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Errors;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.service.CoreRuntimeTestSupport.TestEventListener;
import io.github.gear4jtest.core.service.steps.Step10;
import io.github.gear4jtest.core.service.steps.Step3;
import io.github.gear4jtest.core.service.steps.Step8;
import io.github.gear4jtest.core.service.steps.Step9;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.AssemblyLines.chain;
import static io.github.gear4jtest.core.api.util.Events.eventConfiguration;
import static io.github.gear4jtest.core.api.util.Events.eventHandling;
import static io.github.gear4jtest.core.api.util.RuntimeContracts.configuration;
import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineDslRuntimeTest {
    @Test
    void pipelineWithSkipIteratorAndEventSubscription_shouldComplete() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("test")
                .then(processingOperation("step3", Step3.class).parameter(Step3::getParam, "a")
                        .onError(Errors.<String>ignore(Exception.class)
                                .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                                .action(() -> {
                                }).build())
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

        ResourceFactory resourceFactory = CoreRuntimeTestSupport.testResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
        var request = RunRequest.builder()
                .input("b")
                .context(CoreRuntimeTestSupport.contextWithA())
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
                                .action(() -> {
                                }).build())
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

        ResourceFactory resourceFactory = CoreRuntimeTestSupport.testResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
        var request = RunRequest.builder()
                .input("b")
                .context(CoreRuntimeTestSupport.contextWithA())
                .resourceFactory(resourceFactory)
                .build();

        // When
        ExecutionResult<List<List<String>>> result = engine.execute(assemblyLine, request);

        // Then
        assertThat(result).isNotNull().extracting(ExecutionResult::getResult).isInstanceOf(List.class).asList()
                .hasSize(1).first().isInstanceOf(List.class).asList().contains("");

        CoreRuntimeTestSupport.assertParameterEventsPublished(testEventListener);
    }
}
