package io.github.gear4jtest.core.api.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class ContextPropagationPolicyTest {
    @Test
    void inheritAllShallow_shouldCopyMapButShareValues() {
        List<String> value = new ArrayList<>(List.of("parent"));
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("shared", value);

        Map<String, Object> child = ContextPropagationPolicy.inheritAllShallow().propagate(parent);

        assertThat(child).isNotSameAs(parent);
        assertThat(child).containsEntry("shared", value);
    }

    @Test
    void none_shouldPropagateNoValues() {
        Map<String, Object> child = ContextPropagationPolicy.none().propagate(Map.of("key", "value"));

        assertThat(child).isEmpty();
    }

    @Test
    void includeKeys_shouldPropagateOnlySelectedKeysInSelectionOrder() {
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("a", "A");
        parent.put("b", "B");
        parent.put("c", "C");

        Map<String, Object> child = ContextPropagationPolicy.includeKeys("c", "a").propagate(parent);

        assertThat(child).containsExactly(Map.entry("c", "C"), Map.entry("a", "A"));
    }

    @Test
    void copyValues_shouldCopyAndFilterValues() {
        List<String> mutable = new ArrayList<>(List.of("parent"));
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("mutable", mutable);
        parent.put("secret", "ignored");

        Map<String, Object> child = ContextPropagationPolicy.copyValues(
                                                                        key -> key.equals("mutable"),
                                                                        (key, value) -> value instanceof List<?> list
                                                                                ? new ArrayList<>(list) : value)
                .propagate(parent);

        assertThat(child).containsOnlyKeys("mutable");
        assertThat(child.get("mutable"))
                .asInstanceOf(list(String.class))
                .containsExactlyElementsOf(mutable)
                .isNotSameAs(mutable);
    }

    @Test
    void copyValues_shouldOmitKeysWhenCopierReturnsNull() {
        Map<String, Object> child = ContextPropagationPolicy.copyValues((key, value) -> null)
                .propagate(Map.of("key", "value"));

        assertThat(child).isEmpty();
    }
}
