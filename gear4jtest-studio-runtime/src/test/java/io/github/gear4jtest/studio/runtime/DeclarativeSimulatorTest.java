package io.github.gear4jtest.studio.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeclarativeSimulatorTest {
    private ChainDefinition.Operation op(String id, String capability) {
        return new ChainDefinition.Operation(id, capability, Map.of());
    }

    private ChainDefinition line(ChainDefinition.Node... nodes) {
        return new ChainDefinition(1, "sample", "java.lang.String", "java.lang.String", List.of(nodes));
    }

    private SimulationProfile.Mock returns(String capability, SimulationProfile.Scalar type, String value) {
        return new SimulationProfile.Mock(capability, SimulationProfile.Behavior.RETURN, type, value);
    }

    private SimulationProfile.Mock passthrough(String capability) {
        return new SimulationProfile.Mock(capability, SimulationProfile.Behavior.PASSTHROUGH, null, null);
    }

    private ScenarioData data(Map<String, SimulationProfile.Mock> mocks) {
        return new ScenarioData(new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, "  hello  ", null),
                new ScenarioExpectation("SUCCEEDED", "HELLO"), new SimulationProfile(1, mocks));
    }

    @Test
    void completeSubstitutionUsesConditionsAndScalarContractsWithoutAnyBusinessCallOrResourceResolution() {
        // Given: any accidental runtime call is observable on these mocks.
        var executor = mock(AssemblyLineExecutor.class);
        var resources = mock(ResourceFactory.class);
        var resource = new ChainDefinition.Operation("resource", "text.resource",
                Map.of("resource", new ParameterValue(ParameterValue.Kind.RESOURCE_REFERENCE, "demo/suffix")));
        var choice = new ChainDefinition.Choice("choice", "branch", "input == '  hello  '",
                op("yes", "production.only"), op("no", "text.trim"));
        var definition = line(resource, choice, op("length", "text.length"), op("format", "number.format"));
        var data = data(Map.of("resource", passthrough("text.resource"), "yes",
                               returns("production.only", SimulationProfile.Scalar.STRING, "hello"),
                               "no", passthrough("text.trim"), "length",
                               returns("text.length", SimulationProfile.Scalar.INTEGER, "5"), "format",
                               returns("number.format", SimulationProfile.Scalar.STRING, "HELLO")));
        try (var runtime = new LocalGear4jRuntime("sim", "build", DemoWiring.catalog(), executor, resources,
                DataCapturePolicy.allowed(), Duration.ofSeconds(1))) {
            // When: TEST capability is deliberately denied for production.only.
            var plan = runtime.planSimulation(definition, data);
            var result = runtime.simulate(definition, data);
            // Then: logical capability documentation is sufficient; no TEST/RUN binding is
            // invoked.
            assertThat(plan.valid()).isTrue();
            assertThat(plan.substitutions()).hasSize(5);
            assertThat(result.outcome()).isEqualTo("SUCCEEDED");
            assertThat(result.output().value()).isEqualTo("HELLO");
            assertThat(result.input().value()).isEqualTo("  hello  ");
            assertThat(result.check().status()).isEqualTo(ScenarioCheck.Status.PASSED);
            assertThat(result.steps()).anySatisfy(s -> {
                assertThat(s.nodeId()).isEqualTo("choice");
                assertThat(s.selectedNodeId()).isEqualTo("yes");
            });
            assertThat(result.steps()).anySatisfy(s -> {
                assertThat(s.nodeId()).isEqualTo("no");
                assertThat(s.status()).isEqualTo("SKIPPED");
            });
            var otherBranch = new ScenarioData(new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, "world", null),
                    data.expected(), data.simulation());
            assertThat(runtime.simulate(definition, otherBranch).steps()).anySatisfy(step -> {
                assertThat(step.nodeId()).isEqualTo("choice");
                assertThat(step.selectedNodeId()).isEqualTo("no");
            });
            verifyNoInteractions(executor, resources);
        }
    }

    @Test
    void incompleteStaleOrIncompatibleProfilesFailClosedEvenOnUnselectedBranches() {
        // Given
        var simulator = new DeclarativeSimulator(DemoWiring.catalog(), DataCapturePolicy.none());
        var definition = line(new ChainDefinition.Choice("choice", "branch", "true", op("yes", "text.trim"),
                op("no", "text.uppercase")));
        // When / Then
        assertThat(simulator.plan(definition, data(Map.of("yes", passthrough("text.trim")))).diagnostics())
                .extracting(Diagnostic::code).contains("MISSING_MOCK");
        var stale = data(Map.of("yes", returns("wrong", SimulationProfile.Scalar.INTEGER, "5"), "no",
                                passthrough("text.uppercase"), "ghost", passthrough("text.trim")));
        assertThat(simulator.plan(definition, stale).diagnostics()).extracting(Diagnostic::code)
                .contains("MOCK_CAPABILITY_MISMATCH", "MOCK_OUTPUT_MISMATCH", "UNUSED_MOCK");
        assertThatThrownBy(() -> simulator.simulate(definition, stale)).hasMessage("INVALID_DEFINITION");
        var external = new ScenarioData(new ScenarioInput(ScenarioInput.Kind.FIXTURE_REFERENCE, "demo/greeting", "1"),
                null, new SimulationProfile(1, Map.of()));
        assertThat(simulator.plan(definition, external).diagnostics()).extracting(Diagnostic::code)
                .contains("SIMULATION_INPUT_REQUIRED");
        assertThat(simulator.plan(definition, new ScenarioData(data(Map.of()).input(), null)).valid()).isFalse();
        assertThat(simulator.plan(line(op("unknown", "unknown")), data(Map.of())).valid()).isFalse();
    }

    @Test
    void mockFailureAndExpressionFailureAreExplicitAndNeverLeakValuesIntoDiagnostics() {
        // Given
        var simulator = new DeclarativeSimulator(DemoWiring.catalog(), DataCapturePolicy.masked(v -> "[masked]"));
        var definition = line(op("one", "text.trim"), op("two", "text.uppercase"));
        var failing = data(Map.of("one",
                                  new SimulationProfile.Mock("text.trim", SimulationProfile.Behavior.FAIL, null, null),
                                  "two", passthrough("text.uppercase")));
        // When / Then
        var result = simulator.simulate(definition, failing);
        assertThat(result.failureCode()).isEqualTo("MOCK_FAILURE");
        assertThat(result.steps().get(1).status()).isEqualTo("NOT_EXECUTED");
        assertThat(result.input().state()).isEqualTo(CapturedValue.State.REDACTED);
        assertThat(result.input().value()).isEqualTo("[masked]");
        assertThat(result.toString()).doesNotContain("hello");
        assertThat(result.withoutCaptures().input().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
        var choice = line(new ChainDefinition.Choice("choice", "branch", "input", op("yes", "text.trim"),
                op("no", "text.trim")));
        var expression = simulator
                .simulate(choice, data(Map.of("yes", passthrough("text.trim"), "no", passthrough("text.trim"))));
        assertThat(expression.failureCode()).isEqualTo("CONDITION_EVALUATION_FAILED");
        assertThat(expression.steps()).filteredOn(s -> s.status().equals("NOT_EXECUTED")).hasSize(2);
    }

    @Test
    void scalarParsingAndCaptureLimitsAreBoundedAndInvalidValuesNeverFallBackToRealOperators() {
        // Given
        var simulator = new DeclarativeSimulator(DemoWiring.catalog(), (target, value) -> {
            throw new IllegalStateException("capture denied");
        });
        var definition = line(op("n", "text.length"), op("f", "number.format"));
        for (var type : SimulationProfile.Scalar.values()) {
            var data = data(Map
                    .of("n", returns("text.length", type, type == SimulationProfile.Scalar.BOOLEAN ? "true" : "5"), "f",
                        returns("number.format", SimulationProfile.Scalar.STRING, "HELLO")));
            assertThat(simulator.plan(definition, data).valid()).isEqualTo(type == SimulationProfile.Scalar.INTEGER);
        }
        for (var entry : Map
                .of(SimulationProfile.Scalar.INTEGER, "bad", SimulationProfile.Scalar.LONG, "999999999999999999999999",
                    SimulationProfile.Scalar.DECIMAL, "1e9999", SimulationProfile.Scalar.BOOLEAN, "TRUE")
                .entrySet()) {
            assertThat(simulator
                    .plan(definition,
                          data(Map.of("n", returns("text.length", entry.getKey(), entry.getValue()), "f",
                                      returns("number.format", SimulationProfile.Scalar.STRING, "HELLO"))))
                    .diagnostics()).extracting(Diagnostic::code).contains("INVALID_MOCK_VALUE");
        }
        assertThat(simulator.plan(definition,
                                  data(Map.of("n", passthrough("text.length"), "f",
                                              returns("number.format", SimulationProfile.Scalar.STRING, "HELLO"))))
                .diagnostics()).extracting(Diagnostic::code).contains("MOCK_PASSTHROUGH_MISMATCH");
        var data = data(Map.of("n", returns("text.length", SimulationProfile.Scalar.INTEGER, "5"), "f",
                               returns("number.format", SimulationProfile.Scalar.STRING, "HELLO")));
        assertThat(simulator.simulate(definition, data).input().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
        var large = new DeclarativeSimulator(DemoWiring.catalog(),
                (t, v) -> new CapturedValue(CapturedValue.State.CAPTURED, "x".repeat(1025)));
        assertThat(large.simulate(definition, data).output().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
    }
}
