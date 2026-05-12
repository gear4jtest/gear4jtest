package io.github.gear4jtest.core.sidecompute;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SideComputeContextTest {
    @Test
    void getOrCreateFuture_shouldReturnSameInstanceForSameKey() {
        SideComputeContext ctx = new SideComputeContext();

        CompletableFuture<String> f1 = ctx.getOrCreateFuture("key");
        CompletableFuture<String> f2 = ctx.getOrCreateFuture("key");

        assertThat(f1).isSameAs(f2);
    }

    @Test
    void getOrCreateFuture_shouldReturnDifferentInstancesForDifferentKeys() {
        SideComputeContext ctx = new SideComputeContext();

        CompletableFuture<String> f1 = ctx.getOrCreateFuture("k1");
        CompletableFuture<String> f2 = ctx.getOrCreateFuture("k2");

        assertThat(f1).isNotSameAs(f2);
    }

    @Test
    void futureShouldPropagateResult() throws ExecutionException, InterruptedException {
        SideComputeContext ctx = new SideComputeContext();

        CompletableFuture<String> future = ctx.getOrCreateFuture("key");
        future.complete("result");

        assertThat(future.get()).isEqualTo("result");
    }

    @Test
    void futureShouldPropagateException() {
        SideComputeContext ctx = new SideComputeContext();

        CompletableFuture<String> future = ctx.getOrCreateFuture("key");
        RuntimeException ex = new RuntimeException("boom");
        future.completeExceptionally(ex);

        assertThat(future).isCompletedExceptionally();
    }
}
