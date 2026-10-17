package io.github.gear4jtest.studio.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ControlledRun;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.OperationDescriptor;
import io.github.gear4jtest.studio.model.RuntimeContext;
import io.github.gear4jtest.studio.model.TestSubmission;
import io.github.gear4jtest.studio.model.ValidationReport;
import io.github.gear4jtest.studio.spi.StudioRuntime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InMemoryStudioServiceTest {
    private static final ChainDefinition DEFINITION = new ChainDefinition(1, "draft", "java.lang.String",
            "java.lang.String", List.of());

    @Test
    void concurrentDuplicateRequestHasOneExecutionAndReturnsTheRunningReceipt() throws Exception {
        // Given
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var runtime = new FakeRuntime() {
            @Override
            public ControlledRun execute(DraftRevision revision, String input, UUID id, String actor) {
                calls.incrementAndGet();
                started.countDown();
                try {
                    if (!finish.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                throw new IllegalStateException("Unknown outcome after possible effects");
            }
        };
        var service = new InMemoryStudioService(runtime, (actor, action) -> {
        });
        var draft = service.create("alice", DEFINITION);
        var id = UUID.randomUUID();
        var pool = Executors.newSingleThreadExecutor();
        try {
            // When
            var first = pool.submit(() -> service.test("alice", draft.draftId(), 1, id, "input"));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var duplicate = service.test("alice", draft.draftId(), 1, id, "input");
            // Then
            assertThat(duplicate.state()).isEqualTo(TestSubmission.State.RUNNING);
            assertThat(calls).hasValue(1);
            assertThatThrownBy(() -> service.test("alice", draft.draftId(), 1, id, "different")).hasMessage("CONFLICT");
            finish.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).state()).isEqualTo(TestSubmission.State.OUTCOME_UNKNOWN);
            assertThat(service.test("alice", draft.draftId(), 1, id, "input").state())
                    .isEqualTo(TestSubmission.State.OUTCOME_UNKNOWN);
            assertThat(calls).hasValue(1);
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void callerCannotReadAnotherOwnersDraftOrReceipt() {
        var service = new InMemoryStudioService(new FakeRuntime(), (actor, action) -> {
        });
        var draft = service.create("alice", DEFINITION);
        var id = UUID.randomUUID();
        service.test("alice", draft.draftId(), 1, id, "x");
        assertThatThrownBy(() -> service.draft("bob", draft.draftId())).hasMessage("NOT_FOUND");
        assertThatThrownBy(() -> service.testRequest("bob", id)).hasMessage("NOT_FOUND");
    }

    @Test
    void limitsBoundRetainedDraftsAndRevisionSnapshotsStayImmutable() {
        var service = new InMemoryStudioService(new FakeRuntime(), (actor, action) -> {
        });
        var draft = service.create("alice", DEFINITION);
        for (int i = 1; i < 64; i++)
            service.save("alice", draft.draftId(), i, DEFINITION);
        assertThatThrownBy(() -> service.save("alice", draft.draftId(), 64, DEFINITION))
                .hasMessage("CAPACITY_EXCEEDED");
        assertThat(draft.number()).isEqualTo(1);
        assertThatThrownBy(() -> service.revisions("alice", draft.draftId()).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static class FakeRuntime implements StudioRuntime {
        @Override
        public RuntimeContext context() {
            return new RuntimeContext("local", "test", "fingerprint", Set.of());
        }

        @Override
        public List<OperationDescriptor> catalog() {
            return List.of();
        }

        @Override
        public void assertSafeToStore(ChainDefinition definition) {
        }

        @Override
        public ValidationReport validate(ChainDefinition definition) {
            return new ValidationReport("fingerprint", List.of());
        }

        @Override
        public String exportDefinition(ChainDefinition definition, String mediaType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChainDefinition importDefinition(String source, String mediaType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ControlledRun execute(DraftRevision revision, String input, UUID id, String actor) {
            throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
        }
    }
}
