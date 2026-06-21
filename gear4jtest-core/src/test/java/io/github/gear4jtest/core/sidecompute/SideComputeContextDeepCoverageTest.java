package io.github.gear4jtest.core.sidecompute;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SideComputeContextDeepCoverageTest {
    @Test
    void getOrCreateFuture_shouldReuseFutureForSameKeyAndKeepDifferentKeysIndependent() {
        // Given
        SideComputeContext context = new SideComputeContext();

        // When
        CompletableFuture<String> first = context.getOrCreateFuture("prices");
        CompletableFuture<String> same = context.getOrCreateFuture("prices");
        CompletableFuture<Integer> other = context.getOrCreateFuture("stock");

        // Then
        assertThat(same).isSameAs(first);
        assertThat(other).isNotSameAs(first);
    }

    @Test
    void cancelPendingFutures_shouldOnlyCancelUnresolvedFutures() {
        // Given
        SideComputeContext context = new SideComputeContext();
        CompletableFuture<String> completed = context.getOrCreateFuture("completed");
        CompletableFuture<String> pending = context.getOrCreateFuture("pending");
        completed.complete("ok");

        // When
        context.cancelPendingFutures();

        // Then
        assertThat(completed.isDone()).isTrue();
        assertThat(completed.join()).isEqualTo("ok");
        assertThat(pending.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(pending::join)
                .isInstanceOf(CancellationException.class)
                .hasMessage("Pipeline execution ended before side-compute completion");
    }
}
