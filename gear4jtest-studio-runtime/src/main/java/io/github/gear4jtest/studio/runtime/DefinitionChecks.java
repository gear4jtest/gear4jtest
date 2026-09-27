package io.github.gear4jtest.studio.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ChainDefinition.Choice;
import io.github.gear4jtest.studio.model.ChainDefinition.Operation;
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
        if (definition.schemaVersion() != 1 || !definition.inputType().equals("java.lang.String")
                || !definition.outputType().equals("java.lang.String"))
            diagnostics.add(Diagnostic.error("UNSUPPORTED_CONTRACT", "/",
                                             "P0 attend une définition v1 avec entrée et sortie String."));
        if (definition.nodes().isEmpty())
            diagnostics.add(Diagnostic.error("EMPTY_CHAIN", "/nodes", "Ajoutez une opération."));
        Class<?> previous = String.class;
        for (int i = 0; i < definition.nodes().size(); i++) {
            var node = definition.nodes().get(i);
            String path = "/nodes/" + i;
            if (node instanceof Operation operation)
                previous = operation(operation, previous, path, ids, diagnostics);
            else if (node instanceof Choice choice) {
                id(choice.id(), path + "/id", ids, diagnostics);
                id(choice.branchId(), path + "/branchId", ids, diagnostics);
                try {
                    GearExpressionParser.parse(choice.condition());
                } catch (RuntimeException e) {
                    diagnostics.add(Diagnostic.error("INVALID_EXPRESSION", path + "/condition",
                                                     "Expression GEL invalide."));
                }
                compatible(previous, String.class, path, diagnostics);
                compatible(operation(choice.whenTrue(), String.class, path + "/whenTrue", ids, diagnostics),
                           String.class, path + "/whenTrue", diagnostics);
                compatible(operation(choice.whenFalse(), String.class, path + "/whenFalse", ids, diagnostics),
                           String.class, path + "/whenFalse", diagnostics);
                previous = String.class;
            }
        }
        compatible(previous, String.class, "/outputType", diagnostics);
        return diagnostics;
    }

    private Class<?> operation(Operation operation,
                               Class<?> previous,
                               String path,
                               Set<String> ids,
                               List<Diagnostic> diagnostics) {
        id(operation.id(), path + "/id", ids, diagnostics);
        var binding = catalog.find(operation.operationId());
        if (binding == null) {
            diagnostics.add(Diagnostic.error("UNKNOWN_OPERATION", path + "/operationId",
                                             "Opération absente du catalogue."));
            return null;
        }
        if (!binding.descriptor().testAllowed())
            diagnostics.add(Diagnostic.error("TEST_DENIED", path, "Opération non autorisée en test."));
        compatible(previous, binding.inputType(), path, diagnostics);
        if (binding.descriptor().input().schemaId() != null || binding.descriptor().output().schemaId() != null) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, "UNKNOWN_SCHEMA", path,
                    "P0 ne démontre pas la compatibilité des schémas métier."));
        }
        binding.descriptor().parameters().forEach((name, parameter) -> {
            var value = operation.parameters().get(name);
            if (parameter.required() && (value == null || value.value().isBlank()))
                diagnostics.add(Diagnostic.error("REQUIRED_PARAMETER", path + "/parameters/" + name,
                                                 "Paramètre obligatoire manquant."));
        });
        return binding.outputType();
    }

    private void id(String value, String path, Set<String> ids, List<Diagnostic> diagnostics) {
        if (!value.matches("[a-zA-Z_][a-zA-Z0-9_]{0,63}") || !ids.add(value))
            diagnostics.add(Diagnostic.error("INVALID_ID", path, "Identifiant Java simple et unique requis dans P0."));
    }

    private void compatible(Class<?> output, Class<?> input, String path, List<Diagnostic> diagnostics) {
        if (output == null || input == null)
            diagnostics
                    .add(new Diagnostic(Diagnostic.Severity.WARNING, "UNKNOWN", path, "Compatibilité non démontrée."));
        else if (!input.isAssignableFrom(output))
            diagnostics.add(Diagnostic.error("INCOMPATIBLE", path, "Contrats Java incompatibles."));
    }
}
