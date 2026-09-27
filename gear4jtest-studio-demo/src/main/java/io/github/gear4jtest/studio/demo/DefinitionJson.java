package io.github.gear4jtest.studio.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ParameterValue;

/**
 * Transport-specific mapping; rejects unknown fields instead of silently
 * discarding them.
 */
public final class DefinitionJson {
    private DefinitionJson() {
    }

    public static ChainDefinition read(JsonNode value) {
        fields(value, Set.of("schemaVersion", "id", "inputType", "outputType", "nodes"));
        if (!value.path("schemaVersion").isIntegralNumber() || !value.path("schemaVersion").canConvertToInt()
                || !value.path("nodes").isArray() || value.path("nodes").size() > 16)
            throw new IllegalArgumentException("Invalid definition");
        var nodes = new ArrayList<ChainDefinition.Node>();
        for (var node : value.path("nodes")) {
            if (text(node, "kind").equals("operation"))
                nodes.add(operation(node));
            else {
                fields(node, Set.of("kind", "id", "branchId", "condition", "whenTrue", "whenFalse"));
                if (!text(node, "kind").equals("choice"))
                    throw new IllegalArgumentException("Unknown node");
                nodes.add(new ChainDefinition.Choice(text(node, "id"), text(node, "branchId"), text(node, "condition"),
                        operation(node.path("whenTrue")), operation(node.path("whenFalse"))));
            }
        }
        return new ChainDefinition(value.path("schemaVersion").intValue(), text(value, "id"), text(value, "inputType"),
                text(value, "outputType"), nodes);
    }

    private static ChainDefinition.Operation operation(JsonNode value) {
        fields(value, Set.of("kind", "id", "operationId", "parameters"));
        if (!text(value, "kind").equals("operation") || !value.path("parameters").isObject()
                || value.path("parameters").size() > 16)
            throw new IllegalArgumentException("Invalid operation");
        var parameters = new LinkedHashMap<String, ParameterValue>();
        value.path("parameters").properties().forEach(entry -> {
            fields(entry.getValue(), Set.of("kind", "value"));
            parameters.put(entry.getKey(), new ParameterValue(
                    ParameterValue.Kind.valueOf(text(entry.getValue(), "kind")), text(entry.getValue(), "value")));
        });
        return new ChainDefinition.Operation(text(value, "id"), text(value, "operationId"), parameters);
    }

    public static Map<String, Object> write(ChainDefinition value) {
        return Map.of("schemaVersion", value.schemaVersion(), "id", value.id(), "inputType", value.inputType(),
                      "outputType", value.outputType(), "nodes",
                      value.nodes().stream().map(DefinitionJson::node).toList());
    }

    private static Map<String, Object> node(ChainDefinition.Node value) {
        if (value instanceof ChainDefinition.Operation op)
            return Map.of("kind", "operation", "id", op.id(), "operationId", op.operationId(), "parameters",
                          op.parameters());
        var choice = (ChainDefinition.Choice) value;
        return Map.of("kind", "choice", "id", choice.id(), "branchId", choice.branchId(), "condition",
                      choice.condition(), "whenTrue", node(choice.whenTrue()), "whenFalse", node(choice.whenFalse()));
    }

    static void fields(JsonNode value, Set<String> allowed) {
        if (!value.isObject() || value.properties().stream().anyMatch(entry -> !allowed.contains(entry.getKey())))
            throw new IllegalArgumentException("Unknown field");
    }

    static String text(JsonNode value, String key) {
        if (!value.path(key).isTextual())
            throw new IllegalArgumentException("Text required");
        return value.path(key).textValue();
    }

    static long number(JsonNode value, String key) {
        if (!value.path(key).isIntegralNumber() || !value.path(key).canConvertToLong())
            throw new IllegalArgumentException("Integer required");
        return value.path(key).longValue();
    }
}
