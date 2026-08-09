package io.github.gear4jtest.experimental.cache.history.fingerprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhitelistedContextFingerprintStrategyTest {
    private static final FingerprintContext FINGERPRINT_CONTEXT = new FingerprintContext("pipeline", "1");

    @Test
    void constructor_shouldRejectNullDependenciesAndKeys() {
        FingerprintStrategy<Object> delegate = (value, context) -> new byte[] { 1 };

        assertThatThrownBy(() -> new WhitelistedContextFingerprintStrategy(null, delegate))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("keys");
        assertThatThrownBy(() -> new WhitelistedContextFingerprintStrategy(List.of("tenant"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate");
        assertThatThrownBy(() -> new WhitelistedContextFingerprintStrategy(
                Arrays.asList("tenant", null), delegate))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fingerprint_shouldUseConstructorSnapshotAfterCallerMutation() {
        // Given
        List<String> keys = new ArrayList<>(List.of("tenant"));
        AtomicInteger invocations = new AtomicInteger();
        WhitelistedContextFingerprintStrategy strategy = new WhitelistedContextFingerprintStrategy(keys,
                assertingDelegate(invocations));
        ExecutionContext context = context(Map.of("tenant", "tenant-a", "region", "eu"));

        // When
        keys.clear();
        keys.add("region");
        byte[] fingerprint = strategy.fingerprint(context, FINGERPRINT_CONTEXT);

        // Then
        assertThat(fingerprint).containsExactly((byte) 1);
        assertThat(invocations).hasValue(1);
    }

    @Test
    void fingerprint_shouldRemainStableWhileCallerMutatesOriginalWhitelist() throws Exception {
        // Given
        List<String> keys = new ArrayList<>(List.of("tenant"));
        AtomicInteger invocations = new AtomicInteger();
        WhitelistedContextFingerprintStrategy strategy = new WhitelistedContextFingerprintStrategy(keys,
                assertingDelegate(invocations));
        ExecutionContext context = context(Map.of("tenant", "tenant-a", "region", "eu"));
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch mutationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            var mutation = executor.submit(() -> {
                mutationStarted.countDown();
                while (!stop.get()) {
                    keys.clear();
                    keys.add("region");
                    keys.add("tenant");
                }
            });
            assertThat(mutationStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // When
            for (int invocation = 0; invocation < 10_000; invocation++) {
                assertThat(strategy.fingerprint(context, FINGERPRINT_CONTEXT)).containsExactly((byte) 1);
            }

            // Then
            stop.set(true);
            mutation.get(5, TimeUnit.SECONDS);
            assertThat(invocations).hasValue(10_000);
        } finally {
            stop.set(true);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static FingerprintStrategy<Object> assertingDelegate(AtomicInteger invocations) {
        return (value, context) -> {
            assertThat(value).isEqualTo(Map.of("tenant", "tenant-a"));
            assertThat(context).isSameAs(FINGERPRINT_CONTEXT);
            invocations.incrementAndGet();
            return new byte[] { 1 };
        };
    }

    private static ExecutionContext context(Map<String, Object> values) {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getContext()).thenReturn(values);
        return context;
    }
}
