package io.github.gear4jtest.jackson;

import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.exception.PayloadCloneException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonPayloadClonerTest {
    private final JacksonPayloadCloner cloner = new JacksonPayloadCloner(new ObjectMapper().findAndRegisterModules());

    @Test
    void should_return_same_reference_for_known_immutable_payloads() {
        // Given
        String payload = "gear4j";

        // When
        String cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isSameAs(payload);
    }

    @Test
    void should_deep_clone_nested_pojo_graph() {
        // Given
        ComplexPayload original = new ComplexPayload();
        original.setId("root");
        original.getTags().add("alpha");
        original.getTags().add("beta");
        original.getChildren().put("left", new NestedValue("A"));
        original.getChildren().put("right", new NestedValue("B"));

        // When
        ComplexPayload cloned = cloner.clonePayload(original);

        // Then
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getTags()).isNotSameAs(original.getTags());
        assertThat(cloned.getChildren()).isNotSameAs(original.getChildren());
        assertThat(cloned.getChildren().get("left")).isNotSameAs(original.getChildren().get("left"));
        assertThat(cloned.getId()).isEqualTo("root");
        assertThat(cloned.getTags()).containsExactly("alpha", "beta");
        assertThat(cloned.getChildren().get("left").getValue()).isEqualTo("A");

        cloned.getTags().add("gamma");
        cloned.getChildren().get("left").setValue("changed");

        assertThat(original.getTags()).containsExactly("alpha", "beta");
        assertThat(original.getChildren().get("left").getValue()).isEqualTo("A");
    }

    @Test
    void should_deep_clone_top_level_list_with_runtime_element_types() {
        // Given
        List<NestedValue> original = new ArrayList<>();
        original.add(new NestedValue("one"));
        original.add(new NestedValue("two"));

        // When
        List<NestedValue> cloned = cloner.clonePayload(original);

        // Then
        assertThat(cloned).isNotSameAs(original)
                .hasSize(2);
        assertThat(cloned.get(0)).isInstanceOf(NestedValue.class);
        assertThat(cloned.get(0)).isNotSameAs(original.get(0));
        assertThat(cloned.get(0).getValue()).isEqualTo("one");

        cloned.get(0).setValue("mutated");

        assertThat(original.get(0).getValue()).isEqualTo("one");
    }

    @Test
    void should_deep_clone_top_level_map_with_runtime_value_types() {
        // Given
        Map<String, NestedValue> original = new LinkedHashMap<>();
        original.put("a", new NestedValue("one"));
        original.put("b", new NestedValue("two"));

        // When
        Map<String, NestedValue> cloned = cloner.clonePayload(original);

        // Then
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.get("a")).isInstanceOf(NestedValue.class);
        assertThat(cloned.get("a")).isNotSameAs(original.get("a"));
        assertThat(cloned.get("a").getValue()).isEqualTo("one");

        cloned.get("a").setValue("mutated");

        assertThat(original.get("a").getValue()).isEqualTo("one");
    }

    @Test
    void should_return_null_when_payload_is_null() {
        // Given
        Object payload = null;

        // When
        Object cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isNull();
    }

    @Test
    void should_clone_empty_optional() {
        // Given
        Optional<NestedValue> payload = Optional.empty();

        // When
        Optional<NestedValue> cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isEmpty();
    }

    @Test
    void should_clone_optional_of_mutable_value() {
        // Given
        Optional<NestedValue> payload = Optional.of(new NestedValue("alpha"));

        // When
        Optional<NestedValue> cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isPresent();
        assertThat(cloned.get()).isNotSameAs(payload.get());
        assertThat(cloned.get().getValue()).isEqualTo("alpha");

        cloned.get().setValue("mutated");

        assertThat(payload.get().getValue()).isEqualTo("alpha");
    }

    @Test
    void should_return_same_reference_for_optional_int() {
        // Given
        OptionalInt payload = OptionalInt.of(42);

        // When
        OptionalInt cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isSameAs(payload);
    }

    @Test
    void should_return_same_reference_for_optional_long() {
        // Given
        OptionalLong payload = OptionalLong.of(42L);

        // When
        OptionalLong cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isSameAs(payload);
    }

    @Test
    void should_return_same_reference_for_optional_double() {
        // Given
        OptionalDouble payload = OptionalDouble.of(42.5d);

        // When
        OptionalDouble cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isSameAs(payload);
    }

    @Test
    void should_return_same_reference_for_zone_id_subclass() {
        // Given
        ZoneId payload = ZoneId.of("Europe/Paris");

        // When
        ZoneId cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isSameAs(payload);
    }

    @Test
    void should_deep_clone_top_level_set_with_runtime_element_types() {
        // Given
        LinkedHashSet<NestedValue> payload = new LinkedHashSet<>();
        payload.add(new NestedValue("one"));
        payload.add(new NestedValue("two"));

        // When
        @SuppressWarnings("unchecked")
        LinkedHashSet<NestedValue> cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isNotSameAs(payload)
                .hasSize(2);

        List<NestedValue> originalValues = new ArrayList<>(payload);
        List<NestedValue> clonedValues = new ArrayList<>(cloned);

        assertThat(clonedValues.get(0)).isInstanceOf(NestedValue.class);
        assertThat(clonedValues.get(0)).isNotSameAs(originalValues.get(0));
        assertThat(clonedValues.get(0).getValue()).isEqualTo("one");

        clonedValues.get(0).setValue("mutated");

        assertThat(originalValues.get(0).getValue()).isEqualTo("one");
    }

    @Test
    void should_deep_clone_array_of_mutable_values() {
        // Given
        NestedValue[] payload = new NestedValue[] { new NestedValue("one"), new NestedValue("two") };

        // When
        NestedValue[] cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isNotSameAs(payload)
                .hasSize(2);
        assertThat(cloned[0]).isNotSameAs(payload[0]);
        assertThat(cloned[0].getValue()).isEqualTo("one");

        cloned[0].setValue("mutated");

        assertThat(payload[0].getValue()).isEqualTo("one");
    }

    @Test
    void should_deep_clone_non_list_non_set_collection() {
        // Given
        ArrayDeque<NestedValue> payload = new ArrayDeque<>();
        payload.add(new NestedValue("one"));
        payload.add(new NestedValue("two"));

        // When
        @SuppressWarnings("unchecked")
        java.util.Collection<NestedValue> cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isNotSameAs(payload)
                .isInstanceOf(ArrayList.class)
                .hasSize(2);

        NestedValue originalFirst = payload.iterator().next();
        NestedValue clonedFirst = cloned.iterator().next();
        assertThat(clonedFirst).isNotSameAs(originalFirst);
        assertThat(clonedFirst.getValue()).isEqualTo("one");

        clonedFirst.setValue("mutated");

        assertThat(originalFirst.getValue()).isEqualTo("one");
    }

    @Test
    void should_deep_clone_map_with_mutable_keys_and_values() {
        // Given
        NestedKey key = new NestedKey("key-1");
        NestedValue value = new NestedValue("value-1");
        Map<NestedKey, NestedValue> payload = new LinkedHashMap<>();
        payload.put(key, value);

        // When
        Map<NestedKey, NestedValue> cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isNotSameAs(payload)
                .hasSize(1);

        Map.Entry<NestedKey, NestedValue> clonedEntry = cloned.entrySet().iterator().next();
        assertThat(clonedEntry.getKey()).isNotSameAs(key);
        assertThat(clonedEntry.getValue()).isNotSameAs(value);
        assertThat(clonedEntry.getKey().getId()).isEqualTo("key-1");
        assertThat(clonedEntry.getValue().getValue()).isEqualTo("value-1");

        clonedEntry.getKey().setId("mutated-key");
        clonedEntry.getValue().setValue("mutated-value");

        assertThat(key.getId()).isEqualTo("key-1");
        assertThat(value.getValue()).isEqualTo("value-1");
    }

    @Test
    void should_deep_clone_nested_collections_inside_map() {
        // Given
        Map<String, List<NestedValue>> payload = new LinkedHashMap<>();
        List<NestedValue> values = new ArrayList<>();
        values.add(new NestedValue("alpha"));
        values.add(new NestedValue("beta"));
        payload.put("items", values);

        // When
        Map<String, List<NestedValue>> cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isNotSameAs(payload);
        assertThat(cloned.get("items")).isNotSameAs(payload.get("items"));
        assertThat(cloned.get("items").get(0)).isNotSameAs(payload.get("items").get(0));
        assertThat(cloned.get("items").get(0).getValue()).isEqualTo("alpha");

        cloned.get("items").get(0).setValue("mutated");

        assertThat(payload.get("items").get(0).getValue()).isEqualTo("alpha");
    }

    @Test
    void should_not_share_references_between_original_and_clone_at_multiple_levels() {
        // Given
        ComplexPayload payload = new ComplexPayload();
        payload.setId("root");
        payload.getTags().add("alpha");
        payload.getChildren().put("left", new NestedValue("A"));

        // When
        ComplexPayload cloned = cloner.clonePayload(payload);

        // Then
        assertThat(cloned).isNotSameAs(payload);
        assertThat(cloned.getTags()).isNotSameAs(payload.getTags());
        assertThat(cloned.getChildren()).isNotSameAs(payload.getChildren());
        assertThat(cloned.getChildren().get("left")).isNotSameAs(payload.getChildren().get("left"));

        payload.getTags().add("beta");
        payload.getChildren().get("left").setValue("changed-in-original");

        assertThat(cloned.getTags()).containsExactly("alpha");
        assertThat(cloned.getChildren().get("left").getValue()).isEqualTo("A");
    }

    @Test
    void should_throw_payload_clone_exception_when_jackson_cannot_clone_pojo() {
        // Given
        NonRoundTrippablePayload payload = new NonRoundTrippablePayload();
        payload.setValue("boom");

        // When / Then
        assertThatThrownBy(() -> cloner.clonePayload(payload)).isInstanceOf(PayloadCloneException.class)
                .hasMessageContaining(NonRoundTrippablePayload.class.getName());
    }

    @Test
    void should_create_cloner_with_provided_object_mapper() {
        // Given
        ObjectMapper customMapper = new ObjectMapper().findAndRegisterModules();

        // When
        var payloadCloner = JacksonPayloadCloners.with(customMapper);

        // Then
        assertThat(payloadCloner).isInstanceOf(JacksonPayloadCloner.class);
    }

    static final class ComplexPayload {
        private String id;
        private List<String> tags = new ArrayList<>();
        private Map<String, NestedValue> children = new LinkedHashMap<>();

        public ComplexPayload() {
            // Default constructor required by Jackson.
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Map<String, NestedValue> getChildren() {
            return children;
        }

        public void setChildren(Map<String, NestedValue> children) {
            this.children = children;
        }
    }

    static final class NestedValue {
        private String value;

        public NestedValue() {
        }

        public NestedValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static final class NestedKey {
        private String id;

        public NestedKey() {
        }

        public NestedKey(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    static final class NonRoundTrippablePayload {
        private String value;

        public NonRoundTrippablePayload() {
            // Default constructor required by Jackson.
        }

        public String getValue() {
            throw new IllegalStateException("Cannot serialize this payload");
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
