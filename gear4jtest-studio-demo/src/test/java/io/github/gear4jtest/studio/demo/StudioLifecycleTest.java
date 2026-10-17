package io.github.gear4jtest.studio.demo;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.runtime.LocalGear4jRuntime;
import io.github.gear4jtest.studio.runtime.OperationCatalog;
import io.github.gear4jtest.studio.runtime.RegisteredOperation;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.studio.spi.StudioPublisher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.gear4jtest.studio.management.Permission.EDIT;
import static io.github.gear4jtest.studio.management.Permission.PROMOTE;
import static io.github.gear4jtest.studio.management.Permission.PUBLISH;
import static io.github.gear4jtest.studio.management.Permission.READ;
import static io.github.gear4jtest.studio.model.StudioPublication.Mode.*;
import static io.github.gear4jtest.studio.model.StudioPublication.State.*;
import static org.assertj.core.api.Assertions.*;

class StudioLifecycleTest {
    @TempDir
    Path directory;

    private DataSource source() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + directory.resolve("lifecycle"));
        return source;
    }

    private LocalGear4jRuntime runtime() {
        var resources = DemoWiring.plainResources();
        return DemoWiring.runtime("lifecycle", DemoWiring.plainExecutor(resources), resources,
                                  DataCapturePolicy.allowed());
    }

    private StudioManagementService service(DataSource source,
                                            LocalGear4jRuntime runtime,
                                            StudioPublisher publisher,
                                            String actor) {
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        return new StudioManagementService(new JdbcStudioStore(source), runtime,
                () -> new StudioIdentity(actor, Set.of(), false), a -> true, publisher);
    }

    private DraftState draft(StudioManagementService service) {
        var domain = service.createDomain("catalog", "Catalogue", "").resource();
        var chain = service.createChain(domain.id(), "text", "Text", "", "text_preparation").resource();
        return service.createDraft(chain.id(), "Work", DemoWiring.sample());
    }

    private StudioPublication publication(VersionState state, StudioPublication.Mode mode) {
        return state.publications().stream().filter(p -> p.mode() == mode).findFirst().orElseThrow();
    }

    @Test
    void exactVersionsPublishToTestPromoteToRunAndSurviveAFullReopen() throws Exception {
        // Given
        var source = source();
        UUID firstId;
        UUID chainId;
        String sourceXml;
        String latestLoader;
        try (var runtime = runtime(); var host = new DemoPublicationHost(source, DemoWiring.catalog())) {
            var service = service(source, runtime, host.publisher(), "alice");
            var draft = draft(service);
            var first = service.createVersion(draft.draft().id(), 1, "1.0.0");
            firstId = first.version().id();
            chainId = first.version().chainId();
            sourceXml = first.version().source();
            assertThatThrownBy(() -> service.promoteRun(firstId)).hasMessage("TEST_PUBLICATION_REQUIRED");
            // When
            var published = service.publishTest(firstId);
            // Then
            assertThat(publication(published, TEST).state()).isEqualTo(CONFIRMED);
            assertThat(host.objects().find("text_preparation", "1.0.0", ExecutionMode.RUN)).isEmpty();
            assertThat(service.publishTest(firstId)).isEqualTo(published);
            var run = service.testVersion(firstId, UUID.randomUUID(), " electric ").run();
            assertThat(run.output().value()).isEqualTo("ELECTRIC");
            assertThat(run.versionId()).isEqualTo(firstId);
            assertThat(run.artifactHash()).isEqualTo(first.version().artifactHash());
            var promoted = service.promoteRun(firstId);
            assertThat(publication(promoted, RUN).state()).isEqualTo(CONFIRMED);
            assertThat(publication(promoted, RUN).publishedBy()).isEqualTo("alice");
            assertThat(host.objects().find("text_preparation", "1.0.0", ExecutionMode.RUN).orElseThrow().contentHash())
                    .isEqualTo(first.version().artifactHash());
            host.manager().getOperationChain("text_preparation", ExecutionMode.RUN);
            String oldAlias = host.manager().resolveLatestRunLoaderId("text_preparation");
            var changed = new ChainDefinition(1, "text_preparation", "java.lang.String", "java.lang.String",
                    List.of(new ChainDefinition.Operation("trim", "text.trim", Map.of())));
            service.saveDraft(draft.draft().id(), 1, changed);
            var second = service.createVersion(draft.draft().id(), 2, "2.0.0");
            assertThat(service.compareVersions(firstId, second.version().id()).diff().changes()).isNotEmpty();
            service.publishTest(second.version().id());
            service.promoteRun(second.version().id());
            assertThat(host.manager().resolveLatestRunLoaderId("text_preparation")).isNull();
            host.manager().getOperationChain("text_preparation", ExecutionMode.RUN);
            assertThat(oldAlias).isNotNull();
            assertThat(host.manager().resolveLatestRunLoaderId("text_preparation")).isNotNull().isNotEqualTo(oldAlias);
            assertThat(service.testVersion(firstId, UUID.randomUUID(), " electric ").run().output().value())
                    .isEqualTo("ELECTRIC");
            assertThat(service.testVersion(second.version().id(), UUID.randomUUID(), " electric ").run().output()
                    .value()).isEqualTo("electric");
            latestLoader = host.manager().resolveLatestRunLoaderId("text_preparation");
            var page = service.versions(chainId, null, 1);
            assertThat(page.items()).singleElement()
                    .satisfies(v -> assertThat(v.version().id()).isEqualTo(second.version().id()));
            assertThat(service.versions(chainId, page.nextCursor(), 1).items()).singleElement()
                    .satisfies(v -> assertThat(v.version().id()).isEqualTo(firstId));
            assertThat(service.audit(chainId, null, 100).items())
                    .filteredOn(a -> a.action() == AuditEvent.Action.PUBLICATION_CONFIRMED)
                    .hasSize(2).allSatisfy(a -> assertThat(a.versionId()).isNotNull());
        }
        // A new manager and store, with no in-memory receipts, read the same artifact.
        try (var runtime = runtime(); var host = new DemoPublicationHost(source, DemoWiring.catalog())) {
            var service = service(source, runtime, host.publisher(), "alice");
            assertThat(service.version(firstId).version().source()).isEqualTo(sourceXml);
            assertThat(publication(service.refreshVersion(firstId), RUN).state()).isEqualTo(CONFIRMED);
            assertThat(service.versions(chainId, null, 10).items()).hasSize(2);
            var line = host.manager().getOperationChain("text_preparation", ExecutionMode.RUN)
                    .getAssemblyLineDefinition();
            assertThat(line.getId()).isEqualTo("text_preparation");
            // External publication labels are distinct from the DSL definition's default
            // version.
            assertThat(host.objects().findLatestRun("text_preparation").orElseThrow().version()).isEqualTo("2.0.0");
            assertThat(host.manager().resolveLatestRunLoaderId("text_preparation")).isEqualTo(latestLoader);
        }
    }

    @Test
    void sourceAndInvalidDraftsCannotFreezeAndRightsForPublishPromoteAndTestStaySeparate() {
        // Given
        var source = source();
        try (var runtime = runtime(); var host = new DemoPublicationHost(source, DemoWiring.catalog())) {
            var alice = service(source, runtime, host.publisher(), "alice");
            var bob = service(source, runtime, host.publisher(), "bob");
            var initial = draft(alice);
            var chainId = initial.draft().chainId();
            alice.setAccess(chainId, 1, new AccessPolicy(true,
                    List.of(new AccessGrant(AccessGrant.SubjectType.USER, "bob", Set.of(READ, PROMOTE)))));
            var ownDraft = initial.draft().id();
            alice.setAccess(ownDraft, 1, new AccessPolicy(false,
                    List.of(new AccessGrant(AccessGrant.SubjectType.USER, "bob", Set.of(READ, EDIT, PUBLISH)))));
            // When / Then: draft PUBLISH never grants chain PUBLISH.
            assertThatThrownBy(() -> bob.createVersion(ownDraft, 1, "forged")).hasMessage("FORBIDDEN");
            var version = alice.createVersion(ownDraft, 1, "1");
            var id = version.version().id();
            assertThatThrownBy(() -> bob.publishTest(id)).hasMessage("FORBIDDEN");
            assertThatThrownBy(() -> bob.testVersion(id, UUID.randomUUID(), "x")).hasMessage("FORBIDDEN");
            alice.publishTest(id);
            assertThat(publication(bob.promoteRun(id), RUN).publishedBy()).isEqualTo("bob");
            assertThatThrownBy(() -> alice.createVersion(ownDraft, 1, "1")).hasMessage("CONFLICT");
            assertThatThrownBy(() -> alice.createVersion(ownDraft, 1, "latest")).hasMessage("INVALID_REQUEST");
            var sourceRevision = alice.saveSource(ownDraft, 2,
                                                  new DraftRevision.Source("application/xml", version.version().source()
                                                          .replace("</assemblyLine>", "</assemblyL")));
            assertThatThrownBy(() -> alice.createVersion(ownDraft, sourceRevision.revision().number(), "broken"))
                    .hasMessage("SOURCE_REQUIRES_ANALYSIS");
            var invalid = alice.saveDraft(ownDraft, sourceRevision.draft().version(), new ChainDefinition(1,
                    "text_preparation", "java.lang.String", "java.lang.String", List.of()));
            assertThatThrownBy(() -> alice.createVersion(ownDraft, invalid.revision().number(), "empty"))
                    .hasMessage("INVALID_DEFINITION");
            alice.setAccess(chainId, 2, new AccessPolicy(true, List.of()));
            assertThatThrownBy(() -> bob.version(id)).hasMessage("NOT_FOUND");
            assertThat(alice.versions(chainId, null, 100).items()).hasSize(1);
        }
    }

    @Test
    void interruptedPublicationIsReconciledWithoutRepeatingTheRuntimeWrite() {
        // Given
        var source = source();
        try (var runtime = runtime(); var host = new DemoPublicationHost(source, DemoWiring.catalog())) {
            var forwarding = new FaultPublisher(host.publisher());
            var service = service(source, runtime, forwarding, "alice");
            var draft = draft(service);
            var id = service.createVersion(draft.draft().id(), 1, "1").version().id();
            forwarding.readUnavailable = true;
            // When: artifact commits but both the response and observation are lost.
            assertThat(publication(service.publishTest(id), TEST).state()).isEqualTo(UNKNOWN);
            forwarding.readUnavailable = false;
            var reopened = service(source, runtime, forwarding, "alice");
            assertThat(publication(reopened.refreshVersion(id), TEST).state()).isEqualTo(CONFIRMED);
            assertThat(publication(reopened.publishTest(id), TEST).state()).isEqualTo(CONFIRMED);
            assertThat(forwarding.calls).isEqualTo(1);
            // A process dying after intent is recorded leaves REQUESTED until explicit
            // refresh.
            var id2 = service.createVersion(draft.draft().id(), 1, "2").version().id();
            new JdbcStudioStore(source).write(tx -> {
                tx.savePublication(new StudioPublication(id2, TEST, REQUESTED, "alice", Instant.now(), null, null, null,
                        null));
                return null;
            });
            assertThat(publication(reopened.refreshVersion(id2), TEST).state()).isEqualTo(UNKNOWN);
            assertThat(publication(reopened.publishTest(id2), TEST).state()).isEqualTo(CONFIRMED);
            assertThat(forwarding.calls).isEqualTo(2);
        }
    }

    @Test
    void runPolicyIsExplicitAndCannotBeInferredFromTestPermission() {
        // Given: Studio authoring allows TEST; the publication host denies RUN.
        var source = source();
        var catalog = new OperationCatalog(List.of(
                                                   RegisteredOperation.inferred("text.trim", "Trim", "", "",
                                                                                TrimOperation.class, true, false,
                                                                                Map.of(), Map.of()),
                                                   RegisteredOperation.inferred("text.uppercase", "Upper", "", "",
                                                                                UppercaseOperation.class, true, false,
                                                                                Map.of(), Map.of())));
        try (var runtime = runtime(); var host = new DemoPublicationHost(source, catalog)) {
            var service = service(source, runtime, host.publisher(), "alice");
            var draft = draft(service);
            var id = service.createVersion(draft.draft().id(), 1, "1").version().id();
            // When / Then
            assertThat(publication(service.publishTest(id), TEST).state()).isEqualTo(CONFIRMED);
            assertThat(publication(service.promoteRun(id), RUN).state()).isEqualTo(REJECTED);
            assertThat(host.objects().find("text_preparation", "1", ExecutionMode.RUN)).isEmpty();
            assertThat(service.audit(draft.draft().chainId(), null, 100).items())
                    .anyMatch(event -> event.action() == AuditEvent.Action.PROMOTION_REJECTED
                            && id.equals(event.versionId()));
        }
    }

    @Test
    void aPreexistingDifferentArtifactCannotBeOverwrittenOrPromotedAsThisVersion() throws Exception {
        // Given
        var source = source();
        try (var runtime = runtime(); var host = new DemoPublicationHost(source, DemoWiring.catalog())) {
            var service = service(source, runtime, host.publisher(), "alice");
            var draft = draft(service);
            var first = service.createVersion(draft.draft().id(), 1, "1");
            service.publishTest(first.version().id()); // Provision the host's configuration.
            var second = service.createVersion(draft.draft().id(), 1, "2");
            String otherXml = second.version().source().replace("text.uppercase", "text.trim");
            assertThat(otherXml).isNotEqualTo(second.version().source());
            host.manager().registerAssemblyLine("text_preparation", "2", ExecutionMode.TEST,
                                                otherXml.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                                "application/xml", List.of(), "outside-studio");
            // When / Then
            var result = publication(service.publishTest(second.version().id()), TEST);
            assertThat(result.state()).isEqualTo(REJECTED);
            assertThat(result.errorCode()).isEqualTo("PUBLICATION_CONFLICT");
            assertThatThrownBy(() -> service.promoteRun(second.version().id())).hasMessage("TEST_PUBLICATION_REQUIRED");
            assertThat(host.objects().find("text_preparation", "2", ExecutionMode.TEST).orElseThrow().createdBy())
                    .isEqualTo("outside-studio");
            assertThat(host.objects().find("text_preparation", "2", ExecutionMode.RUN)).isEmpty();
        }
    }

    private static final class FaultPublisher implements StudioPublisher {
        private final StudioPublisher delegate;
        private int calls;
        private boolean readUnavailable;

        private FaultPublisher(StudioPublisher delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<String> capabilities() {
            return delegate.capabilities();
        }

        @Override
        public void publishTest(StudioVersion version, String actor) {
            calls++;
            delegate.publishTest(version, actor);
            throw new IllegalStateException("synthetic lost response");
        }

        @Override
        public void promoteToRun(StudioVersion version, String actor) {
            delegate.promoteToRun(version, actor);
        }

        @Override
        public Optional<PublishedArtifact> find(StudioVersion version, StudioPublication.Mode mode) {
            if (readUnavailable)
                throw new IllegalStateException("synthetic read outage");
            return delegate.find(version, mode);
        }
    }
}
