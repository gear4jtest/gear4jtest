package io.github.gear4jtest.studio.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.management.AccessGrant;
import io.github.gear4jtest.studio.management.AuditEvent;
import io.github.gear4jtest.studio.management.Permission;
import io.github.gear4jtest.studio.management.ResourceKind;
import io.github.gear4jtest.studio.management.StudioChain;
import io.github.gear4jtest.studio.management.StudioDomain;
import io.github.gear4jtest.studio.management.StudioDraft;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.DraftRevision;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlStudioStoreIT {
    @Container
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationsAtomicWritesAclHistoryAndOptimisticConflictsWorkOnPostgreSql() {
        // Given
        var source = new PGSimpleDataSource();
        source.setURL(DATABASE.getJdbcUrl());
        source.setUser(DATABASE.getUsername());
        source.setPassword(DATABASE.getPassword());
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.POSTGRESQL);
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.POSTGRESQL);
        var store = new JdbcStudioStore(source);
        var time = Instant.ofEpochMilli(1234);
        var domain = new StudioDomain(UUID.randomUUID(), "catalog", "Catalogue", "", "alice", 1, false, time, time);
        var chain = new StudioChain(UUID.randomUUID(), domain.id(), "vehicles", "Véhicules", "", "alice", 1, true,
                false, time, time);
        var draft = new StudioDraft(UUID.randomUUID(), chain.id(), "Draft", "alice", 1, false, false, 1,
                DraftRevision.Origin.STUDIO, null, time, time);
        var revision = new DraftRevision(draft.id(), UUID.randomUUID(), 1, "alice", time, DraftRevision.Origin.STUDIO,
                new ChainDefinition(1, "sample", "java.lang.String", "java.lang.String", List.of()));
        var grants = List.of(new AccessGrant(AccessGrant.SubjectType.GROUP, "reviewers", Set.of(Permission.READ)));
        // When
        store.write(tx -> {
            tx.insert(domain);
            tx.lockDomain(domain.id());
            tx.insert(chain);
            tx.insert(draft);
            tx.appendRevision(revision);
            tx.replaceGrants(draft.id(), grants);
            tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), domain.id(), chain.id(), draft.id(), ResourceKind.DRAFT,
                    draft.id(), 1, "alice", AuditEvent.Action.DRAFT_CREATED, time, revision.revisionId(), null, null,
                    null));
            return null;
        });
        // Then: new connection, repeatable-read snapshot and dialect-independent seek
        // queries.
        new JdbcStudioStore(source).read(tx -> {
            assertThat(tx.resource(draft.id())).isEqualTo(draft);
            assertThat(tx.revisions(draft.id(), 0, 10)).containsExactly(revision);
            assertThat(tx.grants(draft.id())).containsExactlyElementsOf(grants);
            assertThat(tx.audit(domain.id(), chain.id(), draft.id(), 0, 10)).hasSize(1);
            assertThat(tx.chains(domain.id(), null, 10)).containsExactly(chain);
            return null;
        });
        var changed = new StudioDomain(domain.id(), domain.key(), "Changed", "", "alice", 2, false, time, time);
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.update(changed, 1);
            return null;
        });
        assertThatThrownBy(() -> store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.update(changed, 1);
            return null;
        })).hasMessage("CONFLICT");
        assertThatThrownBy(() -> store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.replaceGrants(draft.id(), List.of());
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        store.read(tx -> {
            assertThat(tx.grants(draft.id())).containsExactlyElementsOf(grants);
            return null;
        });
    }
}
