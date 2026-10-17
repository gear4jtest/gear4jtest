package io.github.gear4jtest.studio.model;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DefinitionDiffTest {
    private ChainDefinition line(ChainDefinition.Node... nodes) {
        return new ChainDefinition(1, "line", "java.lang.String", "java.lang.String", List.of(nodes));
    }

    private ChainDefinition.Operation op(String id, String value) {
        return new ChainDefinition.Operation(id, "prefix",
                Map.of("prefix", new ParameterValue(ParameterValue.Kind.TEXT, value)));
    }

    @Test
    void comparesOrderParametersConditionsAndRemovedOperationsByStableIdentity() {
        var first = op("first", "A");
        var second = op("second", "B");
        var change = DefinitionDiff.between(line(first, second), line(second, op("first", "C")));
        assertThat(change.ambiguousIdentity()).isFalse();
        assertThat(change.changes()).filteredOn(c -> c.kind() == DefinitionDiff.Kind.MOVED).hasSize(2);
        assertThat(change.changes()).filteredOn(c -> c.path().equals("/nodes/1/parameters/prefix/value"))
                .singleElement().satisfies(c -> {
                    assertThat(c.before()).isEqualTo("A");
                    assertThat(c.after()).isEqualTo("C");
                });
        assertThat(DefinitionDiff.between(line(first, second), line(first)).changes())
                .anyMatch(c -> c.kind() == DefinitionDiff.Kind.REMOVED && c.nodeId().equals("second"));
        var before = new ChainDefinition.Choice("choice", "yes", "true", first, second);
        var after = new ChainDefinition.Choice("choice", "yes", "false", first, second);
        assertThat(DefinitionDiff.between(line(before), line(after)).changes()).singleElement()
                .satisfies(c -> assertThat(c.path()).isEqualTo("/nodes/0/condition"));
    }

    @Test
    void invalidDuplicateIdentitiesRemainComparableWithoutPretendingAnExactMatch() {
        var duplicate = line(op("same", "a"), op("same", "b"));
        assertThat(DefinitionDiff.between(duplicate, duplicate).ambiguousIdentity()).isTrue();
        assertThat(DefinitionDiff.between(duplicate, duplicate).changes()).isEmpty();
    }
}
