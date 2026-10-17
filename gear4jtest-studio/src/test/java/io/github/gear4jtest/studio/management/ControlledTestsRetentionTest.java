package io.github.gear4jtest.studio.management;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.studio.model.TestSubmission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ControlledTestsRetentionTest {
    private final StudioIdentity actor = new StudioIdentity("alice", Set.of(), false);
    private final UUID resource = UUID.randomUUID();
    private final UUID content = UUID.randomUUID();

    @Test
    void terminalReceiptsExpireAndLeastRecentlyUsedResultsMakeRoom() {
        // Given
        var clock = new MutableClock();
        var tests = new ControlledTests(clock, new TestReceiptPolicy(2, Duration.ofMinutes(30)));
        var calls = new AtomicInteger();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        java.util.function.Supplier<io.github.gear4jtest.studio.model.ControlledRun> work = () -> {
            calls.incrementAndGet();
            return null;
        };
        tests.execute(actor, resource, content, first, "x", work, () -> {
        });
        tests.execute(actor, resource, content, second, "x", work, () -> {
        });
        // When / Then: replay refreshes recency but never extends terminal TTL.
        tests.execute(actor, resource, content, first, "x", work, () -> {
        });
        assertThat(calls).hasValue(2);
        assertThatThrownBy(() -> tests.execute(actor, resource, content, first, "changed", work, () -> {
        }))
                .hasMessage("CONFLICT");
        tests.execute(actor, resource, content, UUID.randomUUID(), "x", work, () -> {
        });
        assertThatThrownBy(() -> tests.status("alice", second)).hasMessage("NOT_FOUND");
        assertThat(tests.status("alice", first).state()).isEqualTo(TestSubmission.State.COMPLETED);
        clock.now = clock.now.plus(Duration.ofMinutes(30));
        assertThatThrownBy(() -> tests.status("alice", first)).hasMessage("NOT_FOUND");
        for (int i = 0; i < 300; i++)
            tests.execute(actor, resource, content, UUID.randomUUID(), "x", work, () -> {
            });
        assertThat(calls).hasValue(303);
    }

    @Test
    void runningReceiptSurvivesTtlAndSaturationAndKeepsIdempotence() throws Exception {
        // Given
        var clock = new MutableClock();
        var tests = new ControlledTests(clock, new TestReceiptPolicy(1, Duration.ofMinutes(30)));
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var request = UUID.randomUUID();
        var pool = Executors.newSingleThreadExecutor();
        try {
            var pending = pool.submit(() -> tests.execute(actor, resource, content, request, "x", () -> {
                started.countDown();
                await(finish);
                return null;
            }, () -> {
            }));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            // When
            clock.now = clock.now.plus(Duration.ofDays(1));
            // Then
            assertThat(tests.status("alice", request).state()).isEqualTo(TestSubmission.State.RUNNING);
            assertThat(tests.execute(actor, resource, content, request, "x", () -> {
                throw new AssertionError();
            },
                                     () -> {
                                     })
                    .state()).isEqualTo(TestSubmission.State.RUNNING);
            assertThatThrownBy(() -> tests.execute(actor, resource, content, UUID.randomUUID(), "x", () -> null,
                                                   () -> {
                                                   }))
                    .hasMessage("CAPACITY_EXCEEDED");
            finish.countDown();
            assertThat(pending.get(5, TimeUnit.SECONDS).state()).isEqualTo(TestSubmission.State.COMPLETED);
            tests.execute(actor, resource, content, UUID.randomUUID(), "x", () -> null, () -> {
            });
            assertThatThrownBy(() -> tests.status("alice", request)).hasMessage("NOT_FOUND");
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void storageFailureAfterExecutionIsUncertainAndDoesNotReexecuteWhileReceiptRemains() {
        // Given
        var tests = new ControlledTests();
        var request = UUID.randomUUID();
        var calls = new AtomicInteger();
        java.util.function.Supplier<io.github.gear4jtest.studio.model.ControlledRun> execution = () -> {
            calls.incrementAndGet();
            throw new io.github.gear4jtest.studio.service.StudioProblem(
                    io.github.gear4jtest.studio.service.StudioProblem.Code.STORAGE_UNAVAILABLE);
        };
        // When / Then
        assertThat(tests.execute(actor, resource, content, request, "x", execution, () -> {
        }).state())
                .isEqualTo(TestSubmission.State.OUTCOME_UNKNOWN);
        tests.execute(actor, resource, content, request, "x", execution, () -> {
        });
        assertThat(calls).hasValue(1);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-04T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
