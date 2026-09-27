package io.github.gear4jtest.studio.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Format-neutral declarative subset. Invalid definitions can be saved as
 * drafts.
 */
public record ChainDefinition(int schemaVersion, String id, String inputType, String outputType, List<Node> nodes) {
    public ChainDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(inputType);
        Objects.requireNonNull(outputType);
        nodes = List.copyOf(nodes);
    }

    public sealed interface Node permits Operation, Choice {
        String id();
    }

    public record Operation(String id, String operationId, Map<String, ParameterValue> parameters) implements Node {
        public Operation {
            Objects.requireNonNull(id);
            Objects.requireNonNull(operationId);
            parameters = Map.copyOf(parameters);
        }
    }

    public record Choice(String id, String branchId, String condition, Operation whenTrue, Operation whenFalse)
            implements Node {
        public Choice {
            Objects.requireNonNull(id);
            Objects.requireNonNull(branchId);
            Objects.requireNonNull(condition);
            Objects.requireNonNull(whenTrue);
            Objects.requireNonNull(whenFalse);
        }
    }
}
