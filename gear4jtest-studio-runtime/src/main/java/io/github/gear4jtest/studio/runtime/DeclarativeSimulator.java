package io.github.gear4jtest.studio.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.model.ChainDefinition.*;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.xml.expression.GearExpressionContext;
import io.github.gear4jtest.xml.expression.GearExpressionParser;

/**
 * Bounded interpreter. Holds metadata only: no operator executor, compiler or
 * ResourceFactory.
 */
final class DeclarativeSimulator {
    static final String VERSION = "gear4j-declarative-simulation-1";
    private final OperationCatalog catalog;
    private final DataCapturePolicy capture;

    DeclarativeSimulator(OperationCatalog catalog, DataCapturePolicy capture) {
        this.catalog = catalog;
        this.capture = capture;
    }

    SimulationPlan plan(ChainDefinition definition, ScenarioData data) {
        var diagnostics = new ArrayList<>(new DefinitionChecks(catalog).validate(definition, false));
        var substitutions = new ArrayList<SimulationPlan.Substitution>();
        if (diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR))
            return new SimulationPlan(VERSION, catalog.fingerprint(), diagnostics, substitutions);
        if (!definition.inputType().equals("java.lang.String") || !definition.outputType().equals("java.lang.String"))
            error(diagnostics, "SIMULATION_BOUNDARY_UNSUPPORTED", "/",
                  "La simulation accepte une frontière String vers String.");
        if (data == null || data.input().kind() != ScenarioInput.Kind.SYNTHETIC_TEXT || data.simulation() == null) {
            error(diagnostics, "SIMULATION_INPUT_REQUIRED", "/scenario",
                  "Un texte synthétique et un profil de substitution explicite sont requis.");
            return new SimulationPlan(VERSION, catalog.fingerprint(), diagnostics, substitutions);
        }
        var ids = new HashSet<String>();
        for (int i = 0; i < definition.nodes().size(); i++) {
            var node = definition.nodes().get(i);
            String path = "/nodes/" + i;
            if (node instanceof Operation op)
                inspect(op, path, data.simulation(), ids, diagnostics, substitutions);
            else if (node instanceof Choice choice) {
                inspect(choice.whenTrue(), path + "/whenTrue", data.simulation(), ids, diagnostics, substitutions);
                inspect(choice.whenFalse(), path + "/whenFalse", data.simulation(), ids, diagnostics, substitutions);
            }
        }
        if (!ids.containsAll(data.simulation().mocks().keySet()))
            error(diagnostics, "UNUSED_MOCK", "/simulation/mocks",
                  "Le profil contient un nœud absent de cette révision.");
        return new SimulationPlan(VERSION, catalog.fingerprint(), diagnostics, substitutions);
    }

    private void inspect(Operation op,
                         String path,
                         SimulationProfile profile,
                         Set<String> ids,
                         List<Diagnostic> diagnostics,
                         List<SimulationPlan.Substitution> substitutions) {
        ids.add(op.id());
        var mock = profile.mocks().get(op.id());
        if (mock == null) {
            error(diagnostics, "MISSING_MOCK", path,
                  "Substitution explicite requise, même sur un chemin non sélectionné.");
            return;
        }
        if (!op.operationId().equals(mock.capability()))
            error(diagnostics, "MOCK_CAPABILITY_MISMATCH", path, "Le nœud ne désigne plus la capability du mock.");
        var descriptor = catalog.find(op.operationId()).descriptor();
        if (!supported(descriptor.input()) || !supported(descriptor.output()))
            error(diagnostics, "SIMULATION_CONTRACT_UNSUPPORTED", path,
                  "Contrat scalaire complet requis ; aucun objet Java arbitraire n’est créé.");
        try {
            if (mock.behavior() == SimulationProfile.Behavior.RETURN
                    && !accepts(descriptor.output(), value(mock)))
                error(diagnostics, "MOCK_OUTPUT_MISMATCH", path,
                      "La valeur substituée ne respecte pas le contrat de sortie.");
            if (mock.behavior() == SimulationProfile.Behavior.PASSTHROUGH
                    && !descriptor.input().javaType().equals(descriptor.output().javaType()))
                error(diagnostics, "MOCK_PASSTHROUGH_MISMATCH", path,
                      "Le passage inchangé nécessite le même type scalaire IN/OUT.");
        } catch (RuntimeException invalid) {
            error(diagnostics, "INVALID_MOCK_VALUE", path, "Valeur scalaire invalide ou hors limites.");
        }
        substitutions.add(new SimulationPlan.Substitution(op.id(), op.operationId(), path, mock.behavior()));
    }

    private static boolean supported(DataContract contract) {
        return contract.complete() && contract.schemaId() == null && contract.shape() == null
                && contract.javaType() != null && Set.of("java.lang.String", "java.lang.Integer", "java.lang.Long",
                                                         "java.math.BigDecimal", "java.lang.Boolean")
                        .contains(contract.javaType());
    }

    private static boolean accepts(DataContract contract, Object value) {
        return value != null && value.getClass().getName().equals(contract.javaType());
    }

    private static Object value(SimulationProfile.Mock mock) {
        String text = mock.value();
        if (mock.type() != SimulationProfile.Scalar.STRING && text.length() > 128)
            throw new IllegalArgumentException("Scalar exceeds limits");
        return switch (mock.type()) {
            case STRING -> text;
            case INTEGER -> Integer.valueOf(text);
            case LONG -> Long.valueOf(text);
            case DECIMAL -> {
                var decimal = new java.math.BigDecimal(text);
                if (decimal.precision() > 64 || decimal.scale() < -64 || decimal.scale() > 64)
                    throw new IllegalArgumentException("Decimal exceeds limits");
                yield decimal;
            }
            case BOOLEAN -> {
                if (!text.equals("true") && !text.equals("false"))
                    throw new IllegalArgumentException("Invalid boolean");
                yield Boolean.valueOf(text);
            }
        };
    }

    SimulationResult simulate(ChainDefinition definition, ScenarioData data) {
        if (!plan(definition, data).valid())
            throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
        var steps = new ArrayList<SimulationResult.Step>();
        Object output = data.input().value();
        String failure = null;
        for (var node : definition.nodes()) {
            if (node instanceof Operation op) {
                var next = operation(op, output, data.simulation(), steps);
                output = next.value();
                failure = next.failure();
            } else if (node instanceof Choice choice) {
                boolean branch;
                try {
                    branch = GearExpressionParser.parse(choice.condition())
                            .evaluateBoolean(GearExpressionContext.ofInput(output));
                } catch (RuntimeException invalid) {
                    steps.add(new SimulationResult.Step(choice.id(), null, "FAILED", null,
                            captured(DataCapturePolicy.Target.INPUT, output), CapturedValue.none()));
                    failure = "CONDITION_EVALUATION_FAILED";
                    break;
                }
                var selected = branch ? choice.whenTrue() : choice.whenFalse();
                var skipped = branch ? choice.whenFalse() : choice.whenTrue();
                steps.add(new SimulationResult.Step(choice.id(), null, "SELECTED", selected.id(),
                        captured(DataCapturePolicy.Target.INPUT, output), CapturedValue.none()));
                steps.add(new SimulationResult.Step(skipped.id(), skipped.operationId(), "SKIPPED", null,
                        CapturedValue.none(), CapturedValue.none()));
                var next = operation(selected, output, data.simulation(), steps);
                output = next.value();
                failure = next.failure();
            }
            if (failure != null)
                break;
        }
        var visited = new HashSet<String>();
        steps.forEach(s -> visited.add(s.nodeId()));
        for (var node : definition.nodes()) {
            if (!visited.contains(node.id()))
                steps.add(new SimulationResult.Step(node.id(), node instanceof Operation op ? op.operationId() : null,
                        "NOT_EXECUTED", null, CapturedValue.none(), CapturedValue.none()));
            if (node instanceof Choice choice)
                for (var op : List.of(choice.whenTrue(), choice.whenFalse()))
                    if (!visited.contains(op.id()))
                        steps.add(new SimulationResult.Step(op.id(), op.operationId(), "NOT_EXECUTED", null,
                                CapturedValue.none(), CapturedValue.none()));
        }
        if (failure == null && !(output instanceof String))
            failure = "OUTPUT_CONTRACT_MISMATCH";
        String outcome = failure == null ? "SUCCEEDED" : "FAILED";
        return new SimulationResult(VERSION, catalog.fingerprint(), outcome, failure,
                captured(DataCapturePolicy.Target.INPUT, data.input().value()),
                failure == null ? captured(DataCapturePolicy.Target.OUTPUT, output) : CapturedValue.none(), steps,
                ScenarioCheck.evaluate(data.expected(), outcome, failure == null ? output : null));
    }

    private Transition operation(Operation op,
                                 Object input,
                                 SimulationProfile profile,
                                 List<SimulationResult.Step> steps) {
        var mock = profile.mocks().get(op.id());
        var descriptor = catalog.find(op.operationId()).descriptor();
        String failure = !accepts(descriptor.input(), input) ? "INPUT_CONTRACT_MISMATCH"
                : mock.behavior() == SimulationProfile.Behavior.FAIL ? "MOCK_FAILURE" : null;
        Object output = failure != null ? null
                : mock.behavior() == SimulationProfile.Behavior.PASSTHROUGH ? input : value(mock);
        if (failure == null && !accepts(descriptor.output(), output))
            failure = "OUTPUT_CONTRACT_MISMATCH";
        steps.add(new SimulationResult.Step(op.id(), op.operationId(), failure == null ? "SUBSTITUTED" : "FAILED", null,
                captured(DataCapturePolicy.Target.INPUT, input),
                failure == null ? captured(DataCapturePolicy.Target.OUTPUT, output) : CapturedValue.none()));
        return new Transition(output, failure);
    }

    private CapturedValue captured(DataCapturePolicy.Target target, Object value) {
        try {
            var result = capture.capture(target, value == null ? null : value.toString());
            return result == null || result.value() != null && result.value().length() > 1024 ? CapturedValue.none()
                    : result;
        } catch (RuntimeException denied) {
            return CapturedValue.none();
        }
    }

    private record Transition(Object value, String failure) {}

    private static void error(List<Diagnostic> diagnostics, String code, String path, String message) {
        diagnostics.add(Diagnostic.error(code, path, message));
    }
}
