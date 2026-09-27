package io.github.gear4jtest.studio.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.model.CapturedValue;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.model.TestSubmission;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LocalStudioTest {
    private static final String ACTOR = "demo-editor";

    private LocalGear4jRuntime runtime(DataCapturePolicy capture) {
        var resources = DemoWiring.plainResources();
        return DemoWiring.runtime("java", DemoWiring.plainExecutor(resources), resources, capture);
    }

    private ChainDefinition line(ChainDefinition.Node... nodes) {
        return new ChainDefinition(1, "sample", "java.lang.String", "java.lang.String", List.of(nodes));
    }

    private ChainDefinition.Operation op(String id, String operation, String name, ParameterValue value) {
        return new ChainDefinition.Operation(id, operation, name == null ? Map.of() : Map.of(name, value));
    }

    private ParameterValue text(String value) {
        return new ParameterValue(ParameterValue.Kind.TEXT, value);
    }

    @Test
    void catalogDraftValidationAndRealRunKeepExactProvenance() {
        // Given
        try (var runtime = runtime(DataCapturePolicy.allowed())) {
            var service = DemoWiring.service(runtime);
            var draft = service.create(ACTOR, DemoWiring.sample());
            // When
            var report = service.validate(ACTOR, draft.definition());
            var requestId = UUID.randomUUID();
            var submission = service.test(ACTOR, draft.draftId(), 1, requestId, "  electric  ");
            // Then
            assertThat(report.diagnostics()).isEmpty();
            assertThat(submission.state()).isEqualTo(TestSubmission.State.COMPLETED);
            var run = submission.run();
            assertThat(run.outcome()).isEqualTo("SUCCEEDED");
            assertThat(run.output().value()).isEqualTo("ELECTRIC");
            assertThat(run.runId()).isNotNull();
            assertThat(run.revisionId()).isEqualTo(draft.revisionId());
            assertThat(run.draftId()).isEqualTo(draft.draftId());
            assertThat(run.mode()).isEqualTo("TEST");
            assertThat(run.catalogFingerprint()).isEqualTo(runtime.context().catalogFingerprint());
            assertThat(run.artifactHash()).hasSize(64);
            assertThat(run.steps()).extracting(s -> s.nodeId()).contains("trim", "uppercase");
            var next = service.save(ACTOR, draft.draftId(), 1,
                                    line(op("prefix", "text.prefix", "prefix", text("new:"))));
            assertThat(service.testRequest(ACTOR, requestId).run().revisionId()).isEqualTo(draft.revisionId())
                    .isNotEqualTo(next.revisionId());
            assertThat(service.test(ACTOR, draft.draftId(), 1, requestId, "  electric  ")).isSameAs(submission);
            assertThatThrownBy(() -> service.test(ACTOR, draft.draftId(), 1, requestId, "different"))
                    .isInstanceOf(StudioProblem.class).hasMessage("CONFLICT");
        }
    }

    @Test
    void invalidDraftCanBeSavedButNotExecutedAndRevisionWritesAreConditional() {
        try (var runtime = runtime(DataCapturePolicy.none())) {
            // Given
            var service = DemoWiring.service(runtime);
            var invalid = line(op("missing", "unknown", null, null));
            // When
            var draft = service.create(ACTOR, invalid);
            var report = service.validate(ACTOR, invalid);
            // Then
            assertThat(report.valid()).isFalse();
            assertThat(report.diagnostics()).anySatisfy(d -> {
                assertThat(d.code()).isEqualTo("UNKNOWN_OPERATION");
                assertThat(d.path()).isEqualTo("/nodes/0/operationId");
            });
            assertThat(service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "x").state())
                    .isEqualTo(TestSubmission.State.PREPARATION_FAILED);
            service.save(ACTOR, draft.draftId(), 1, DemoWiring.sample());
            assertThatThrownBy(() -> service.save(ACTOR, draft.draftId(), 1, DemoWiring.sample()))
                    .hasMessage("CONFLICT");
            assertThat(service.revisions(ACTOR, draft.draftId())).hasSize(2);
        }
    }

    @Test
    void validationDoesNotInstantiateOperatorsOrExecuteThem() {
        var calls = new AtomicInteger();
        var real = DemoWiring.plainResources();
        ResourceFactory counting = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                calls.incrementAndGet();
                return real.getResource(type);
            }
        };
        try (var runtime = DemoWiring.runtime("counted", DemoWiring.plainExecutor(counting), counting,
                                              DataCapturePolicy.none())) {
            assertThat(runtime.validate(DemoWiring.sample()).valid()).isTrue();
            assertThat(calls).hasValue(0);
            var service = DemoWiring.service(runtime);
            var draft = service.create(ACTOR, DemoWiring.sample());
            service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "x");
            assertThat(calls.get()).isPositive();
        }
    }

    @Test
    void gelChoicesAndStringParametersRoundTripSemanticallyAndRunBothBranches() {
        try (var runtime = runtime(DataCapturePolicy.allowed())) {
            // Given
            var definition = line(new ChainDefinition.Choice("choice", "positive", "input == \"electric\"",
                    op("yes", "text.prefix", "prefix", text("<YES>\t\n\r\"&")),
                    op("no", "text.uppercase", null, null)));
            String xml = runtime.exportDefinition(definition, "application/xml");
            // When
            var imported = runtime
                    .importDefinition(xml.replace("<operations>", "<operations><!-- normalization discards this -->"),
                                      "application/xml");
            // Then
            assertThat(imported).isEqualTo(definition);
            assertThat(runtime.exportDefinition(imported, "application/xml")).isEqualTo(xml);
            var service = DemoWiring.service(runtime);
            var draft = service.create(ACTOR, imported);
            assertThat(service.validate(ACTOR, imported).diagnostics()).isEmpty();
            var yes = service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "electric");
            assertThat(yes.state()).isEqualTo(TestSubmission.State.COMPLETED);
            assertThat(yes.run().output().value()).isEqualTo("<YES>\t\n\r\"&electric");
            assertThat(service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "other").run().output().value())
                    .isEqualTo("OTHER");
        }
    }

    @Test
    void xmlImportRejectsUnknownFeaturesRawJavaAndDoctypeWithoutSilentLoss() {
        var adapter = new StudioXmlAdapter(DemoWiring.catalog());
        String xml = adapter.write(DemoWiring.sample());
        for (String unsafe : List
                .of(xml.replace("<operations>", "<operations><unsupported/>"),
                    xml.replace("<operations>", "<operations unexpected=\"ignored\">"),
                    xml.replace("<assemblyLine ",
                                "<!DOCTYPE assemblyLine [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><assemblyLine "),
                    xml.replace("text.trim", "java.lang.Runtime"))) {
            assertThatThrownBy(() -> adapter.read(unsafe)).isInstanceOf(StudioProblem.class)
                    .hasMessage("UNSUPPORTED_FORMAT");
        }
        var choice = line(new ChainDefinition.Choice("choice", "yes", "true",
                op("yes_op", "text.uppercase", null, null), op("no_op", "text.trim", null, null)));
        assertThatThrownBy(() -> adapter.read(adapter.write(choice).replace("language=\"gel\"", "language=\"java\"")))
                .hasMessage("UNSUPPORTED_FORMAT");
        var configured = line(op("prefix", "text.prefix", "prefix", text("hello")));
        assertThatThrownBy(() -> adapter.read(adapter.write(configured).replace("::getPrefix", "::getClass")))
                .hasMessage("UNSUPPORTED_FORMAT");
    }

    @Test
    void userPermissionAndRuntimeCapabilityAreIndependentAndEnforcedServerSide() {
        try (var runtime = runtime(DataCapturePolicy.none())) {
            var service = DemoWiring.service(runtime);
            assertThatThrownBy(() -> service.create("demo-viewer", DemoWiring.sample()))
                    .isInstanceOf(SecurityException.class);
            var draft = service.create(ACTOR, line(op("production", "production.only", null, null)));
            assertThat(service.validate(ACTOR, draft.definition()).diagnostics()).extracting(d -> d.code())
                    .contains("TEST_DENIED");
            assertThat(service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "x").state())
                    .isEqualTo(TestSubmission.State.PREPARATION_FAILED);
            assertThatThrownBy(() -> service.draft("demo-viewer", draft.draftId())).hasMessage("NOT_FOUND");
        }
    }

    @Test
    void resourceReferencesAreAuthorizedAndForbidRuntimeCaptureEvenWithAllowedPolicy() {
        try (var runtime = runtime(DataCapturePolicy.allowed())) {
            var service = DemoWiring.service(runtime);
            assertThatThrownBy(() -> service
                    .create(ACTOR, line(op("resource", "text.resource", "resource", text("literal-secret")))))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> service
                    .create(ACTOR,
                            line(op("resource", "text.resource", "resource",
                                    new ParameterValue(ParameterValue.Kind.RESOURCE_REFERENCE, "not/allowed")))))
                    .isInstanceOf(SecurityException.class);
            var definition = line(op("resource", "text.resource", "resource",
                                     new ParameterValue(ParameterValue.Kind.RESOURCE_REFERENCE, "demo/suffix")));
            var draft = service.create(ACTOR, definition);
            var run = service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "sensitive-input").run();
            assertThat(run.outcome()).isEqualTo("SUCCEEDED");
            assertThat(run.input()).isEqualTo(CapturedValue.none());
            assertThat(run.output()).isEqualTo(CapturedValue.none());
            assertThat(runtime.exportDefinition(definition, "application/xml")).contains("demo/suffix")
                    .doesNotContain("synthetic resource");
        }
    }

    @Test
    void capturesDefaultToNoneAndRedactorFailureFailsClosed() {
        for (DataCapturePolicy policy : List.of(DataCapturePolicy.none(), (target, value) -> {
            throw new IllegalStateException("secret-value");
        })) {
            try (var runtime = runtime(policy)) {
                var service = DemoWiring.service(runtime);
                var draft = service.create(ACTOR, DemoWiring.sample());
                var run = service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "secret-value").run();
                assertThat(run.input()).isEqualTo(CapturedValue.none());
                assertThat(run.output()).isEqualTo(CapturedValue.none());
                assertThat(run.toString()).doesNotContain("secret-value");
            }
        }
        try (var runtime = runtime(DataCapturePolicy.masked(value -> "[masked]"))) {
            var service = DemoWiring.service(runtime);
            var draft = service.create(ACTOR, DemoWiring.sample());
            var run = service.test(ACTOR, draft.draftId(), 1, UUID.randomUUID(), "secret-value").run();
            assertThat(run.output().state()).isEqualTo(CapturedValue.State.REDACTED);
            assertThat(run.output().value()).isEqualTo("[masked]");
        }
    }

    @Test
    void plainHostDoesNotRequireSpring() {
        assertThatThrownBy(() -> Class.forName("org.springframework.context.ApplicationContext"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void malformedGelAndMissingParametersProduceLocalizedDiagnostics() {
        try (var runtime = runtime(DataCapturePolicy.none())) {
            var definition = line(new ChainDefinition.Choice("choice", "branch", "input.toString()",
                    op("prefix", "text.prefix", null, null), op("trim", "text.trim", null, null)));
            assertThat(runtime.validate(definition).diagnostics()).extracting(d -> d.path())
                    .contains("/nodes/0/condition", "/nodes/0/whenTrue/parameters/prefix");
        }
    }
}
