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
        EventDispatcher.EventDispatcherStats drainedStats = snapshotAfterPreviouslySubmittedTasks(dispatcher);
        assertThat(drainedStats.completedTasks()).isEqualTo(2);
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
        EventDispatcher.EventDispatcherStats recoveredStats = snapshotAfterPreviouslySubmittedTasks(dispatcher);
        assertThat(recoveredStats.failedTasks()).isEqualTo(1);
        assertThat(recoveredStats.completedTasks()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static EventDispatcher.EventDispatcherStats snapshotAfterPreviouslySubmittedTasks(
                                                                                              EventDispatcher dispatcher)
            throws InterruptedException {
        CountDownLatch barrierStarted = new CountDownLatch(1);
        CountDownLatch releaseBarrier = new CountDownLatch(1);
        assertThat(dispatcher.submit(() -> {
            barrierStarted.countDown();
            await(releaseBarrier);
        })).isTrue();
        assertThat(barrierStarted.await(2, TimeUnit.SECONDS)).isTrue();
        try {
            return dispatcher.snapshotStats();
        } finally {
            releaseBarrier.countDown();
        }
    }
}
