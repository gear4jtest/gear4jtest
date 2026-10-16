package io.github.gear4jtest.studio.demo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.spi.StudioAuthorization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StudioDemoBehaviorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void demoOperatorsApplyParametersAndRejectUnavailableResources() {
        // Given
        var prefix = new PrefixOperation();
        var suffix = new ResourceSuffixOperation(Map.of("available", " [safe]"));

        // When / Then
        assertThat(prefix.transform("input", null)).isEqualTo("input");
        prefix.getPrefix().injectValue("prefix:");
        assertThat(prefix.transform("input", null)).isEqualTo("prefix:input");
        suffix.getResource().injectValue("available");
        assertThat(suffix.transform("input", null)).isEqualTo("input [safe]");
        suffix.getResource().injectValue("not-available");
        assertThatThrownBy(() -> suffix.transform("input", null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Resource unavailable");
        assertThat(new TrimOperation().transform("  abc\n", null)).isEqualTo("abc");
        assertThat(new UppercaseOperation().transform("istanbul", null)).isEqualTo("ISTANBUL");
        assertThatThrownBy(() -> new ProductionOnlyOperation().transform("input", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Must never execute");
    }

    @Test
    void demoFactoryUsesFreshInstancesAndKnownSyntheticResources() {
        // Given
        var resources = DemoWiring.plainResources();

        // When / Then
        assertThat(resources.getResource(PrefixOperation.class)).isNotNull()
                .isNotSameAs(resources.getResource(PrefixOperation.class));
        assertThat(resources.getResource(TrimOperation.class)).isNotNull();
        assertThat(resources.getResource(UppercaseOperation.class)).isNotNull();
        assertThat(resources.getResource(ProductionOnlyOperation.class)).isNotNull();
        assertThat(resources.getResource(String.class)).isNull();
        var suffix = resources.getResource(ResourceSuffixOperation.class);
        suffix.getResource().injectValue("demo/suffix");
        assertThat(suffix.transform("x", null)).isEqualTo("x [synthetic resource]");
    }

    @Test
    void captureAndAuthorizationDefaultsFailClosed() {
        // Given
        var authorization = DemoWiring.authorization();

        // When / Then
        assertThat(DemoWiring.capturePolicy(null)).isNotNull();
        assertThat(DemoWiring.capturePolicy("none")).isNotNull();
        assertThat(DemoWiring.capturePolicy("masked")).isNotNull();
        assertThat(DemoWiring.capturePolicy("synthetic")).isNotNull();
        assertThatThrownBy(() -> DemoWiring.capturePolicy("unexpected"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown capture policy");
        assertThatCode(() -> authorization.require("demo-editor", StudioAuthorization.Action.EDIT))
                .doesNotThrowAnyException();
        assertThatCode(() -> authorization.require("demo-viewer", StudioAuthorization.Action.READ))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.require("demo-viewer", StudioAuthorization.Action.EDIT))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> authorization.require("stranger", StudioAuthorization.Action.READ))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void requestActorBindingIsScopedAndCannotBeReplacedOrBoundToNull() {
        // Given
        var actors = new RequestActorContext();
        var editor = new StudioIdentity("demo-editor", java.util.Set.of("domain-creators"), false);
        assertThat(actors.currentActor()).isNull();

        // When / Then
        try (var scope = actors.bind(editor)) {
            assertThat(actors.currentActor()).isEqualTo(editor);
            assertThatThrownBy(() -> actors.bind(editor)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Actor already bound");
            assertThat(actors.currentActor()).isEqualTo(editor);
        }
        assertThat(actors.currentActor()).isNull();
        assertThatThrownBy(() -> actors.bind(null)).isInstanceOf(NullPointerException.class);
        assertThat(actors.currentActor()).isNull();
        try (var scope = actors.bind(editor)) {
            assertThat(actors.currentActor()).isEqualTo(editor);
        }
        assertThat(actors.currentActor()).isNull();
    }

    @Test
    void definitionJsonRoundTripsTypedOperationsAndChoices() {
        // Given
        var definition = new ChainDefinition(1, "sample", "java.lang.String", "java.lang.String", List.of(
                                                                                                          new ChainDefinition.Operation(
                                                                                                                  "prefix",
                                                                                                                  "text.prefix",
                                                                                                                  Map.of("prefix",
                                                                                                                         new ParameterValue(
                                                                                                                                 ParameterValue.Kind.TEXT,
                                                                                                                                 "hello"))),
                                                                                                          new ChainDefinition.Choice(
                                                                                                                  "choice",
                                                                                                                  "branch",
                                                                                                                  "true",
                                                                                                                  new ChainDefinition.Operation(
                                                                                                                          "trim",
                                                                                                                          "text.trim",
                                                                                                                          Map.of()),
                                                                                                                  new ChainDefinition.Operation(
                                                                                                                          "upper",
                                                                                                                          "text.uppercase",
                                                                                                                          Map.of()))));

        // When
        var json = mapper.valueToTree(DefinitionJson.write(definition));

        // Then
        assertThat(DefinitionJson.read(json)).isEqualTo(definition);
        assertThat(json.path("nodes").get(0).path("parameters").path("prefix").path("value").asText())
                .isEqualTo("hello");
    }

    @Test
    void definitionJsonRejectsUnknownFieldsMissingTypesAndOversizedNodes() {
        var valid = mapper.valueToTree(DefinitionJson.write(DemoWiring.sample()));
        assertThatThrownBy(() -> DefinitionJson.read(mapper.valueToTree(Map.of("extra", 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.read(mapper.valueToTree(Map.of("schemaVersion", "1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.read(mapper.valueToTree(Map.of("schemaVersion", 1, "nodes", "bad"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.read(mapper.valueToTree(Map.of("schemaVersion", 1, "nodes",
                                                                               java.util.Collections
                                                                                       .nCopies(17, Map.of())))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.read(mapper.valueToTree(Map.of("schemaVersion", 1, "nodes",
                                                                               List.of(Map.of("kind",
                                                                                              "unrecognized"))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.read(mapper.valueToTree(Map.of("schemaVersion", 1, "nodes",
                                                                               List.of(Map.of("kind", "operation", "id",
                                                                                              "x", "operationId",
                                                                                              "text.trim"))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.read(mapper.valueToTree(Map.of("schemaVersion", 1, "nodes",
                                                                               List.of(Map.of("kind", "operation", "id",
                                                                                              "x", "operationId",
                                                                                              "text.trim", "parameters",
                                                                                              Map.of("prefix",
                                                                                                     Map.of("kind",
                                                                                                            "INVALID",
                                                                                                            "value",
                                                                                                            "x"))))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionJson.fields(valid, java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DefinitionJson.text(valid, "id")).isEqualTo("text_preparation");
        assertThatThrownBy(() -> DefinitionJson.text(valid, "nodes"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DefinitionJson.number(valid, "schemaVersion")).isEqualTo(1);
        assertThatThrownBy(() -> DefinitionJson.number(valid, "id"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
