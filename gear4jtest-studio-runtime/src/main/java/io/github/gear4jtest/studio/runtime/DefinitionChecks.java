package io.github.gear4jtest.studio.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ChainDefinition.Choice;
import io.github.gear4jtest.studio.model.ChainDefinition.Operation;
import io.github.gear4jtest.studio.model.DataContract;
import io.github.gear4jtest.studio.model.Diagnostic;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.xml.expression.GearExpressionParser;

final class DefinitionChecks {
    private final OperationCatalog catalog;

    DefinitionChecks(OperationCatalog catalog) {
        this.catalog = catalog;
    }

    void safeToStore(ChainDefinition definition) {
        bounded(definition.id(), 64);
        bounded(definition.inputType(), 128);
        bounded(definition.outputType(), 128);
        if (definition.nodes().size() > 16)
            throw new SecurityException("Definition exceeds limits");
        int size = 0;
        for (var node : definition.nodes()) {
            bounded(node.id(), 64);
            if (node instanceof Operation operation)
                size += safeOperation(operation);
            else if (node instanceof Choice choice) {
                bounded(choice.branchId(), 64);
                bounded(choice.condition(), 512);
                size += choice.condition().length() + safeOperation(choice.whenTrue())
                        + safeOperation(choice.whenFalse());
            }
        }
        if (size > 24000)
            throw new SecurityException("Definition exceeds limits");
    }

    private int safeOperation(Operation operation) {
        bounded(operation.id(), 64);
        bounded(operation.operationId(), 128);
        if (operation.parameters().size() > 16)
            throw new SecurityException("Parameter limit");
        var binding = catalog.find(operation.operationId());
        int size = 0;
        for (var entry : operation.parameters().entrySet()) {
            bounded(entry.getKey(), 64);
            bounded(entry.getValue().value(), 4096);
            size += entry.getValue().value().length();
            var parameter = binding == null ? null : binding.descriptor().parameters().get(entry.getKey());
            if (parameter == null || parameter.kind() != entry.getValue().kind())
                throw new SecurityException("Unclassified parameter");
            if (parameter.kind() == ParameterValue.Kind.RESOURCE_REFERENCE
                    && !parameter.allowedReferences().contains(entry.getValue().value()))
                throw new SecurityException("Resource reference denied");
        }
        return size;
    }

    private void bounded(String value, int max) {
        if (value.length() > max)
            throw new SecurityException("Definition exceeds limits");
    }

    List<Diagnostic> validate(ChainDefinition definition) {
        return validate(definition, true);
    }

    List<Diagnostic> validate(ChainDefinition definition, boolean requireTest) {
        var diagnostics = new ArrayList<Diagnostic>();
        try {
            safeToStore(definition);
        } catch (SecurityException e) {
            diagnostics.add(Diagnostic.error("DEFINITION_NOT_ADMITTED", "/",
                                             "Définition hors des limites ou paramètres non autorisés."));
            return diagnostics;
        }
        var ids = new HashSet<String>();
        id(definition.id(), "/id", ids, diagnostics);
        if (definition.schemaVersion() != 1)
            diagnostics.add(Diagnostic.error("UNSUPPORTED_SCHEMA_VERSION", "/schemaVersion",
                                             "Version du modèle non prise en charge."));
        var inputContract = boundary(definition.inputType(), "/inputType", diagnostics);
        var outputContract = boundary(definition.outputType(), "/outputType", diagnostics);
        if (definition.nodes().isEmpty())
            diagnostics.add(Diagnostic.error("EMPTY_CHAIN", "/nodes", "Ajoutez une opération."));
        DataContract previous = inputContract;
        for (int i = 0; i < definition.nodes().size(); i++) {
            var node = definition.nodes().get(i);
            String path = "/nodes/" + i;
            if (node instanceof Operation operation)
                previous = operation(operation, previous, path, ids, diagnostics, requireTest);
            else if (node instanceof Choice choice) {
                id(choice.id(), path + "/id", ids, diagnostics);
                id(choice.branchId(), path + "/branchId", ids, diagnostics);
                try {
                    GearExpressionParser.parse(choice.condition());
                } catch (RuntimeException e) {
                    diagnostics.add(Diagnostic.error("INVALID_EXPRESSION", path + "/condition",
                                                     "Expression GEL invalide."));
                }
                compatible(previous, textContract(), path, diagnostics);
                compatible(operation(choice.whenTrue(), textContract(), path + "/whenTrue", ids, diagnostics,
                                     requireTest),
                           textContract(), path + "/whenTrue", diagnostics);
                compatible(operation(choice.whenFalse(), textContract(), path + "/whenFalse", ids, diagnostics,
                                     requireTest),
                           textContract(), path + "/whenFalse", diagnostics);
                previous = textContract();
            }
        }
        compatible(previous, outputContract, "/outputType", diagnostics);
        return diagnostics;
    }

    private DataContract operation(Operation operation,
                                   DataContract previous,
                                   String path,
                                   Set<String> ids,
                                   List<Diagnostic> diagnostics,
                                   boolean requireTest) {
        id(operation.id(), path + "/id", ids, diagnostics);
        var binding = catalog.find(operation.operationId());
        if (binding == null) {
            diagnostics.add(Diagnostic.error("UNKNOWN_OPERATION", path + "/operationId",
                                             "Opération absente du catalogue."));
            return null;
        }
        if (requireTest && !binding.descriptor().testAllowed())
            diagnostics.add(Diagnostic.error("TEST_DENIED", path, "Opération non autorisée en test."));
        compatible(previous, binding.descriptor().input(), path, diagnostics);
        binding.descriptor().parameters().forEach((name, parameter) -> {
            var value = operation.parameters().get(name);
            if (parameter.required() && (value == null || value.value().isBlank()))
                diagnostics.add(Diagnostic.error("REQUIRED_PARAMETER", path + "/parameters/" + name,
                                                 "Paramètre obligatoire manquant."));
        });
        return binding.descriptor().output();
    }

    private void id(String value, String path, Set<String> ids, List<Diagnostic> diagnostics) {
        if (!value.matches("[a-zA-Z_][a-zA-Z0-9_]{0,63}") || !ids.add(value))
            diagnostics.add(Diagnostic.error("INVALID_ID", path, "Identifiant Java simple et unique requis."));
    }

    private DataContract boundary(String name, String path, List<Diagnostic> diagnostics) {
        if (name.equals("java.lang.String"))
            return textContract();
        var contract = catalog.boundaryContract(name);
        if (contract == null)
            diagnostics.add(Diagnostic.error("UNSUPPORTED_CONTRACT", path,
                                             "Type absent du catalogue explicite de l’application."));
        return contract;
    }

    private static DataContract textContract() {
        return new DataContract("java.lang.String", null, DataContract.Origin.DECLARED, true,
                Set.of("java.lang.String", "java.lang.Object", "java.lang.CharSequence", "java.io.Serializable"), null);
    }

    private void compatible(DataContract output, DataContract input, String path, List<Diagnostic> diagnostics) {
        var compatibility = output == null || input == null ? DataContract.Compatibility.UNKNOWN
                : output.compatibilityWith(input);
        if (compatibility == DataContract.Compatibility.UNKNOWN)
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, "UNKNOWN", path,
                    "Compatibilité non démontrée : type effacé, générique ou schéma insuffisamment décrit."));
        else if (compatibility == DataContract.Compatibility.INCOMPATIBLE)
            diagnostics.add(Diagnostic.error("INCOMPATIBLE", path, "Contrats Java ou structurels incompatibles."));
    }
}
