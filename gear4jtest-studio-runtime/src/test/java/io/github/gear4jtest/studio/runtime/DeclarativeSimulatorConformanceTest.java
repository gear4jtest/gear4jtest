package io.github.gear4jtest.studio.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import io.github.gear4jtest.core.api.*;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Differential oracle through the production XML/compiler/engine path. Only
 * observations are normalized: no control-flow decision is reimplemented in the
 * real-runtime side. Fixtures and tracing live exclusively in test code.
 */
class DeclarativeSimulatorConformanceTest {
    private record Case(String name,
                        ChainDefinition definition,
                        ScenarioData data,
                        List<String> order,
                        String output,
                        String failureNode) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record Visit(String nodeId, String input, String output, String status) {}

    private static ChainDefinition.Operation op(String id, String capability) {
        return new ChainDefinition.Operation(id, capability, Map.of());
    }

    private static ChainDefinition.Choice choice(String id,
                                                 String condition,
                                                 ChainDefinition.Operation yes,
                                                 ChainDefinition.Operation no) {
        return new ChainDefinition.Choice(id, id + "_branch", condition, yes, no);
    }

    private static SimulationProfile.Mock mock(String capability) {
        return switch (capability) {
            case "pure.identity" ->
                new SimulationProfile.Mock(capability, SimulationProfile.Behavior.PASSTHROUGH, null, null);
            case "pure.fail" -> new SimulationProfile.Mock(capability, SimulationProfile.Behavior.FAIL, null, null);
            case "pure.constant" -> new SimulationProfile.Mock(capability, SimulationProfile.Behavior.RETURN,
                    SimulationProfile.Scalar.STRING, "changed");
            case "text.length" -> new SimulationProfile.Mock(capability, SimulationProfile.Behavior.RETURN,
                    SimulationProfile.Scalar.INTEGER, "7");
            case "number.format" -> new SimulationProfile.Mock(capability, SimulationProfile.Behavior.RETURN,
                    SimulationProfile.Scalar.STRING, "7");
            default -> throw new IllegalArgumentException(capability);
        };
    }

    private static List<ChainDefinition.Operation> operations(ChainDefinition definition) {
        return definition.nodes().stream().flatMap(node -> node instanceof ChainDefinition.Operation op
                ? Stream.of(op)
                : Stream.of(((ChainDefinition.Choice) node).whenTrue(), ((ChainDefinition.Choice) node).whenFalse()))
                .toList();
    }

    private static Case example(String name,
                                String input,
                                List<String> order,
                                String output,
                                String failureNode,
                                ChainDefinition.Node... nodes) {
        var definition = new ChainDefinition(1, "conformance", "java.lang.String", "java.lang.String", List.of(nodes));
        var mocks = new java.util.LinkedHashMap<String, SimulationProfile.Mock>();
        operations(definition).forEach(op -> mocks.put(op.id(), mock(op.operationId())));
        return new Case(name, definition,
                new ScenarioData(new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, input, null),
                        null, new SimulationProfile(1, mocks)),
                order, output, failureNode);
    }

    static Stream<Case> definitions() {
        var cases = new ArrayList<Case>();
        for (String input : List.of("", "  hello  ", "é\n\t<&>"))
            cases.add(example("exact identity: " + input, input, List.of("first", "last"), input, null,
                              op("first", "pure.identity"), op("last", "pure.identity")));
        cases.add(example("sequence propagates replacement", "initial", List.of("first", "replace", "last"), "changed",
                          null,
                          op("first", "pure.identity"), op("replace", "pure.constant"), op("last", "pure.identity")));
        cases.add(example("typed intermediate value", "initial", List.of("replace", "length", "format", "last"), "7",
                          null,
                          op("replace", "pure.constant"), op("length", "text.length"), op("format", "number.format"),
                          op("last", "pure.identity")));
        for (String condition : List.of("true", "input == 'initial'", "input != null && input == 'initial'",
                                        "true || input", "!(false)"))
            cases.add(example("true branch: " + condition, "initial", List.of("yes", "last"), "changed", null,
                              choice("select", condition, op("yes", "pure.constant"), op("no", "pure.fail")),
                              op("last", "pure.identity")));
        for (String condition : List.of("false", "input == 'other'", "false && input"))
            cases.add(example("false branch: " + condition, "initial", List.of("no", "last"), "initial", null,
                              choice("select", condition, op("yes", "pure.fail"), op("no", "pure.identity")),
                              op("last", "pure.identity")));
        cases.add(example("conditions consume preceding outputs", "initial",
                          List.of("replace", "yes", "second_yes", "last"), "changed", null,
                          op("replace", "pure.constant"),
                          choice("select", "input == 'changed'", op("yes", "pure.identity"), op("no", "pure.fail")),
                          choice("second", "input == 'changed'", op("second_yes", "pure.identity"),
                                 op("second_no", "pure.fail")),
                          op("last", "pure.identity")));
        cases.add(example("failure stops first station", "initial", List.of("fail"), null, "fail",
                          op("fail", "pure.fail"), op("last", "pure.identity")));
        cases.add(example("failure stops remaining choices", "initial", List.of("first", "fail"), null, "fail",
                          op("first", "pure.constant"), op("fail", "pure.fail"),
                          choice("select", "true", op("yes", "pure.constant"), op("no", "pure.identity")),
                          op("last", "pure.identity")));
        for (boolean branch : List.of(true, false))
            cases.add(example("selected branch fails: " + branch, "initial", List.of(branch ? "yes" : "no"), null,
                              branch ? "yes" : "no",
                              choice("select", String.valueOf(branch),
                                     op("yes", branch ? "pure.fail" : "pure.identity"),
                                     op("no", branch ? "pure.identity" : "pure.fail")),
                              op("last", "pure.constant")));
        cases.add(example("condition evaluation fails before either branch", "initial", List.of("first"), null,
                          "select",
                          op("first", "pure.identity"),
                          choice("select", "input", op("yes", "pure.constant"), op("no", "pure.identity")),
                          op("last", "pure.constant")));
        return cases.stream();
    }

    private static OperationCatalog catalog() {
        var demo = DemoWiring.catalog();
        return new OperationCatalog(List.of(
                                            RegisteredOperation.inferred("pure.identity", "Identity", "Test fixture",
                                                                         "Pure", ConformanceIdentityOperation.class,
                                                                         true, Map.of(), Map.of()),
                                            RegisteredOperation.inferred("pure.constant", "Constant", "Test fixture",
                                                                         "Pure", ConformanceConstantOperation.class,
                                                                         true, Map.of(), Map.of()),
                                            RegisteredOperation.inferred("pure.fail", "Failure", "Test fixture", "Pure",
                                                                         ConformanceFailureOperation.class, true,
                                                                         Map.of(), Map.of()),
                                            demo.find("text.length"), demo.find("number.format")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("definitions")
    void realRuntimeAndIndependentSimulatorAgree(Case example) {
        // Given: pure implementations are provided only to the real Gear4J engine.
        var resources = new ResourceFactory() {
            private final ResourceFactory demo = DemoWiring.plainResources();

            @Override
            public <T> T getResource(Class<T> type) {
                if (type == ConformanceIdentityOperation.class)
                    return type.cast(new ConformanceIdentityOperation());
                if (type == ConformanceConstantOperation.class)
                    return type.cast(new ConformanceConstantOperation());
                if (type == ConformanceFailureOperation.class)
                    return type.cast(new ConformanceFailureOperation());
                return demo.getResource(type);
            }
        };
        var visits = new ArrayList<Visit>();
        var ids = operations(example.definition()).stream().map(ChainDefinition.Operation::id).toList();
        var observer = new StationWrapperExtension() {
            @Override
            public StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext context) {
                return (input, station, stationContext) -> {
                    try {
                        var trace = delegate.run(input, station, stationContext);
                        if (ids.contains(stationContext.getOperationId()))
                            visits.add(new Visit(stationContext.getOperationId(), String.valueOf(input),
                                    trace.getStatus().name().equals("SUCCEEDED") ? String.valueOf(trace.getOutput())
                                            : null,
                                    trace.getStatus().name()));
                        return trace;
                    } catch (RuntimeException failure) {
                        // The outer engine boundary normalizes thrown operator errors.
                        // Observe the attempt and let that boundary run unchanged.
                        if (ids.contains(stationContext.getOperationId()))
                            visits.add(new Visit(stationContext.getOperationId(), String.valueOf(input), null,
                                    "FAILED"));
                        throw failure;
                    }
                };
            }
        };
        var engine = DemoWiring.plainExecutor(resources);
        var observedEngine = new AssemblyLineExecutor() {
            @Override
            public <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> line, RunRequest<IN> request) {
                return engine.execute(line, request.toBuilder().with(observer).build());
            }
        };
        var catalog = catalog();
        var simulator = new DeclarativeSimulator(catalog, DataCapturePolicy.allowed());
        try (var runtime = new LocalGear4jRuntime("conformance", "test", catalog, observedEngine, resources,
                DataCapturePolicy.allowed(), Duration.ofSeconds(10))) {
            // When: identical declaration and exact input, independent execution paths.
            var simulated = simulator.simulate(example.definition(), example.data());
            var revision = new DraftRevision(UUID.randomUUID(), UUID.randomUUID(), 1, "test", Instant.now(),
                    DraftRevision.Origin.STUDIO, example.definition());
            var real = runtime.execute(revision, example.data().input().value(), UUID.randomUUID(), "test");
            // Then: compare semantics, not trace ids/timestamps or presentation labels.
            assertThat(real.runId()).isNotNull();
            assertThat(real.outcome()).isEqualTo(example.failureNode() == null ? "SUCCEEDED" : "FAILED");
            assertThat(simulated.outcome()).isEqualTo(real.outcome());
            assertThat(visits).extracting(Visit::nodeId).containsExactlyElementsOf(example.order());
            var simulatedVisits = simulated.steps().stream()
                    .filter(s -> s.capability() != null
                            && (s.status().equals("SUBSTITUTED") || s.status().equals("FAILED")))
                    .map(s -> new Visit(s.nodeId(), s.input().value(), s.output().value(),
                            s.status().equals("SUBSTITUTED") ? "SUCCEEDED" : "FAILED"))
                    .toList();
            assertThat(simulatedVisits).containsExactlyElementsOf(visits);
            assertThat(simulated.input().value()).isEqualTo(real.input().value())
                    .isEqualTo(example.data().input().value());
            if (example.failureNode() == null) {
                assertThat(real.output().value()).isEqualTo(example.output());
                assertThat(simulated.output().value()).isEqualTo(real.output().value());
            } else {
                assertThat(simulated.output().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
                assertThat(simulated.steps())
                        .anyMatch(s -> s.nodeId().equals(example.failureNode()) && s.status().equals("FAILED"));
                assertThat(real.steps())
                        .anyMatch(s -> s.nodeId().equals(example.failureNode()) && s.status().equals("FAILED"));
            }
            for (var step : simulated.steps()) {
                if (step.status().equals("SELECTED"))
                    assertThat(example.order()).contains(step.selectedNodeId());
                if (step.status().equals("SKIPPED") || step.status().equals("NOT_EXECUTED"))
                    assertThat(visits).extracting(Visit::nodeId).doesNotContain(step.nodeId());
            }
        }
    }
}
