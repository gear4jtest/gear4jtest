package io.github.gear4jtest.studio.json;

import java.util.List;
import java.util.Map;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ParameterValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StudioJsonTest {
    @Test
    void explicitNodeKindsRoundTripWithoutJavaClassNames() throws Exception {
        // Given
        var op = new ChainDefinition.Operation("step", "op",
                Map.of("param", new ParameterValue(ParameterValue.Kind.TEXT, "é\n<&>")));
        var definition = new ChainDefinition(1, "sample", "java.lang.String", "java.lang.String",
                List.of(new ChainDefinition.Choice("choice", "yes", "true", op,
                        new ChainDefinition.Operation("other", "op", Map.of()))));
        var mapper = StudioJson.mapper();
        // When
        String json = mapper.writeValueAsString(DefinitionJson.write(definition));
        // Then
        assertThat(DefinitionJson.read(mapper.readTree(json))).isEqualTo(definition);
        assertThat(json).contains("\"kind\":\"choice\"").doesNotContain("@class");
    }

    @Test
    void unknownFieldsDuplicateKeysAndArbitraryNodeKindsAreRejected() throws Exception {
        var mapper = StudioJson.mapper();
        assertThatThrownBy(() -> mapper.readTree("{\"id\":1,\"id\":2}"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> mapper.readTree("{} {}"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> DefinitionJson.read(mapper
                .readTree("{\"schemaVersion\":1,\"id\":\"x\",\"inputType\":\"String\",\"outputType\":\"String\",\"nodes\":[],\"actor\":\"forged\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.text(mapper.createObjectNode(), "absent"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.number(mapper.readTree("{\"n\":1.5}"), "n"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
