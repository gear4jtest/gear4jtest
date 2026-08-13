package io.github.gear4jtest.core.event;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class EventDispatcherTest {
    @Test
    void submit_shouldRejectWithoutBlockingWhenSharedQueueCapacityIsReached() throws Exception {
        // Given
        EventDispatcher dispatcher = new EventDispatcher(1, 1);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        CountDownLatch queuedTaskCompleted = new CountDownLatch(1);
        assertThat(dispatcher.submit(() -> {
            firstTaskStarted.countDown();
            await(releaseFirstTask);
        })).isTrue();
        assertThat(firstTaskStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(dispatcher.submit(queuedTaskCompleted::countDown)).isTrue();

        // When
        boolean accepted = dispatcher.submit(() -> {
            throw new AssertionError("rejected task must never execute");
        });

        // Then
        assertThat(accepted).isFalse();
        EventDispatcher.EventDispatcherStats saturatedStats = dispatcher.snapshotStats();
        assertThat(saturatedStats.submittedTasks()).isEqualTo(2);
        assertThat(saturatedStats.rejectedTasks()).isEqualTo(1);
        assertThat(saturatedStats.queuedTasks()).isEqualTo(1);
        assertThat(saturatedStats.remainingCapacity()).isZero();

        releaseFirstTask.countDown();
        assertThat(queuedTaskCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        awaitCompletedTasks(dispatcher, 2);
        assertThat(dispatcher.snapshotStats().completedTasks()).isEqualTo(2);
    }

    @Test
    void dispatchLoop_shouldContinueAfterAnUnexpectedTaskFailure() throws Exception {
        // Given
        EventDispatcher dispatcher = new EventDispatcher(1, 2);
        CountDownLatch followingTaskCompleted = new CountDownLatch(1);

        // When
        assertThat(dispatcher.submit(() -> {
            throw new IllegalStateException("expected-test-failure");
        })).isTrue();
        assertThat(dispatcher.submit(followingTaskCompleted::countDown)).isTrue();

        // Then
        assertThat(followingTaskCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        awaitFailedTasks(dispatcher, 1);
        awaitCompletedTasks(dispatcher, 1);
        assertThat(dispatcher.snapshotStats().failedTasks()).isEqualTo(1);
        assertThat(dispatcher.snapshotStats().completedTasks()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitCompletedTasks(EventDispatcher dispatcher, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (dispatcher.snapshotStats().completedTasks() < expected && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
    }

    private static void awaitFailedTasks(EventDispatcher dispatcher, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (dispatcher.snapshotStats().failedTasks() < expected && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
    }
}
