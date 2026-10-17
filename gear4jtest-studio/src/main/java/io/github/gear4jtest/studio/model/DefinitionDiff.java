package io.github.gear4jtest.studio.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Semantic comparison by stable node identity; ambiguous identities fall back
 * to positions.
 */
public record DefinitionDiff(List<Change> changes, boolean ambiguousIdentity) {
    public enum Kind {
        ADDED, REMOVED, MODIFIED, MOVED
    }

    public record Change(Kind kind, String nodeId, String path, String before, String after) {}

    public DefinitionDiff {
        changes = List.copyOf(changes);
    }

    private record Value(String nodeId, String path, String value) {}

    public static DefinitionDiff between(ChainDefinition before, ChainDefinition after) {
        boolean ambiguous = duplicate(before) || duplicate(after);
        var left = flatten(before, ambiguous);
        var right = flatten(after, ambiguous);
        var keys = new TreeSet<>(left.keySet());
        keys.addAll(right.keySet());
        var changes = new ArrayList<Change>();
        for (String key : keys) {
            var a = left.get(key);
            var b = right.get(key);
            if (a != null && b != null && Objects.equals(a.value, b.value))
                continue;
            var value = b == null ? a : b;
            var kind = a == null ? Kind.ADDED : b == null ? Kind.REMOVED
                    : key.endsWith("/@position") ? Kind.MOVED : Kind.MODIFIED;
            changes.add(new Change(kind, value.nodeId, value.path, a == null ? null : a.value,
                    b == null ? null : b.value));
        }
        return new DefinitionDiff(changes, ambiguous);
    }

    private static boolean duplicate(ChainDefinition definition) {
        var ids = new HashSet<String>();
        for (var node : definition.nodes()) {
            if (!ids.add(node.id()))
                return true;
            if (node instanceof ChainDefinition.Choice c
                    && (!ids.add(c.whenTrue().id()) || !ids.add(c.whenFalse().id())))
                return true;
        }
        return false;
    }

    private static Map<String, Value> flatten(ChainDefinition definition, boolean positional) {
        var result = new LinkedHashMap<String, Value>();
        put(result, "/schemaVersion", null, "/schemaVersion", Integer.toString(definition.schemaVersion()));
        put(result, "/id", null, "/id", definition.id());
        put(result, "/inputType", null, "/inputType", definition.inputType());
        put(result, "/outputType", null, "/outputType", definition.outputType());
        for (int i = 0; i < definition.nodes().size(); i++)
            node(result, definition.nodes().get(i), "/nodes/" + i, Integer.toString(i), positional);
        return result;
    }

    private static void node(Map<String, Value> target,
                             ChainDefinition.Node node,
                             String path,
                             String position,
                             boolean positional) {
        String key = positional ? path : "/identity/" + escape(node.id());
        put(target, key + "/@position", node.id(), path, position);
        put(target, key + "/id", node.id(), path + "/id", node.id());
        put(target, key + "/kind", node.id(), path + "/kind",
            node instanceof ChainDefinition.Operation ? "operation" : "choice");
        if (node instanceof ChainDefinition.Operation operation) {
            put(target, key + "/operationId", node.id(), path + "/operationId", operation.operationId());
            operation.parameters().forEach((name, parameter) -> {
                var suffix = "/parameters/" + escape(name);
                put(target, key + suffix + "/kind", node.id(), path + suffix + "/kind", parameter.kind().name());
                put(target, key + suffix + "/value", node.id(), path + suffix + "/value", parameter.value());
            });
        } else {
            var c = (ChainDefinition.Choice) node;
            put(target, key + "/condition", node.id(), path + "/condition", c.condition());
            put(target, key + "/branchId", node.id(), path + "/branchId", c.branchId());
            node(target, c.whenTrue(), path + "/whenTrue", node.id() + "/whenTrue", positional);
            node(target, c.whenFalse(), path + "/whenFalse", node.id() + "/whenFalse", positional);
        }
    }

    private static void put(Map<String, Value> target, String key, String nodeId, String path, String value) {
        target.put(key, new Value(nodeId, path, value));
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
