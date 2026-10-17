package io.github.gear4jtest.studio.demo;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.studio.spi.StudioPublisher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PublicationAttemptTest {
    @Test
    void lateSuccessfulCompletionCannotOverwriteARejectedNewAttempt() throws Exception {
        // Given: two service instances share the same store and domain lock.
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        var oldCompletion = ThreadLocal.withInitial(() -> false);
        StudioPublisher publisher = new StudioPublisher() {
            @Override
            public Set<String> capabilities() {
                return Set.of("PUBLISH_TEST");
            }

            @Override
            public void promoteToRun(StudioVersion version, String actor) {
                throw new AssertionError();
            }

            @Override
            public void publishTest(StudioVersion version, String actor) {
                if (count.incrementAndGet() == 1) {
                    started.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS))
                            throw new AssertionError("Timed out");
                        oldCompletion.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                } else
                    throw new StudioProblem(StudioProblem.Code.PUBLICATION_REJECTED);
            }

            @Override
            public Optional<PublishedArtifact> find(StudioVersion version, StudioPublication.Mode mode) {
                return !oldCompletion.get() ? Optional.empty() : Optional.of(new PublishedArtifact(
                        version.runtimeAssemblyLineId(), version.label(), mode, version.artifactHash(),
                        version.mediaType(), version.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        "alice", Instant.now()));
            }
        };
        var resources = DemoWiring.plainResources();
        var pool = Executors.newSingleThreadExecutor();
        try (var runtime = DemoWiring.runtime("attempt", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.none())) {
            var first = new StudioManagementService(new JdbcStudioStore(source), runtime,
                    () -> new StudioIdentity("alice", Set.of(), false), a -> true, publisher);
            var second = new StudioManagementService(new JdbcStudioStore(source), runtime,
                    () -> new StudioIdentity("bob", Set.of(), false), a -> true, publisher);
            var domain = first.createDomain("d", "Domain", "").resource();
            var chain = first.createChain(domain.id(), "c", "Chain", "", "text_preparation").resource();
            var draft = first.createDraft(chain.id(), "Draft", DemoWiring.sample());
            var version = first.createVersion(draft.draft().id(), 1, "1").version().id();
            first.setAccess(chain.id(), 1, new AccessPolicy(true, java.util.List.of(new AccessGrant(
                    AccessGrant.SubjectType.USER, "bob", Set.of(Permission.READ, Permission.PUBLISH)))));
            var pending = pool.submit(() -> first.publishTest(version));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            var oldId = second.version(version).publications().get(0).attemptId();
            // When: reconciliation allows an explicit retry while the old call is still in
            // flight.
            assertThat(second.refreshVersion(version).publications().get(0).state())
                    .isEqualTo(StudioPublication.State.UNKNOWN);
            var retried = second.publishTest(version).publications().get(0);
            assertThat(retried.state()).isEqualTo(StudioPublication.State.REJECTED);
            assertThat(retried.attemptId()).isNotEqualTo(oldId);
            assertThat(retried.requestedBy()).isEqualTo("bob");
            release.countDown();
            // Then
            assertThat(pending.get(10, TimeUnit.SECONDS).publications().get(0)).isEqualTo(retried);
            assertThat(first.audit(chain.id(), null, 100).items())
                    .filteredOn(event -> event.action() == AuditEvent.Action.PUBLICATION_REQUESTED)
                    .extracting(AuditEvent::attemptId).containsExactly(oldId, retried.attemptId());
            assertThat(first.audit(chain.id(), null, 100).items())
                    .noneMatch(event -> event.action() == AuditEvent.Action.PUBLICATION_CONFIRMED);
        } finally {
            release.countDown();
            pool.shutdownNow();
            try (var c = source.getConnection(); var s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }
}
