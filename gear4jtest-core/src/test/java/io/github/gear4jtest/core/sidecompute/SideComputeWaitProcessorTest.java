package io.github.gear4jtest.core.sidecompute;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.exception.SideComputeExecutionException;
import io.github.gear4jtest.core.exception.SideComputeTimeoutException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SideComputeWaitProcessorTest {
    @Test
    void beforeExecution_shouldWaitAndStoreResolvedValue() {
        // arrange
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);

        SideComputeContext scCtx = new SideComputeContext();
        Map<String, Object> globalMap = new HashMap<>();

        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
        when(execCtx.getContext()).thenReturn(globalMap);

        CompletableFuture<String> future = scCtx.getOrCreateFuture("bigStuff");
        future.complete("resolved-value");

        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("bigStuff").timeout(null) // pas de
                // timeout ->
                // join simple
                .onTimeoutFail().build();

        // act
        processor.beforeExecution("input", opCtx);

        // assert
        assertThat(globalMap).containsEntry(SideComputeKeys.valueKey("bigStuff"), "resolved-value");
    }

    @Test
    void beforeExecution_shouldThrowOnTimeoutWhenConfiguredToFail() {
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);

        SideComputeContext scCtx = new SideComputeContext();
        Map<String, Object> globalMap = new HashMap<>();

        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
        when(execCtx.getContext()).thenReturn(globalMap);

        // future non complétée -> on forcera le timeout
        scCtx.getOrCreateFuture("slow-key");

        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("slow-key").timeout(Duration.ofMillis(50))
                .onTimeoutFail().build();

        assertThatThrownBy(() -> processor.beforeExecution("input", opCtx))
                .isInstanceOf(SideComputeTimeoutException.class).hasMessageContaining("slow-key");
    }

    @Test
    void beforeExecution_shouldUseFallbackOnTimeoutWhenConfigured() {
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);

        SideComputeContext scCtx = new SideComputeContext();
        Map<String, Object> globalMap = new HashMap<>();

        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
        when(execCtx.getContext()).thenReturn(globalMap);

        // future non complétée -> timeout
        scCtx.getOrCreateFuture("slow-key");

        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("slow-key").timeout(Duration.ofMillis(50))
                .onTimeoutUseFallback(() -> "fallback-value").build();

        processor.beforeExecution("input", opCtx);

        assertThat(globalMap).containsEntry(SideComputeKeys.valueKey("slow-key"), "fallback-value");

        // Et la future doit être complétée avec le fallback, pour les consommateurs
        // potentiels
        CompletableFuture<String> f = scCtx.getOrCreateFuture("slow-key");
        assertThat(f).isCompletedWithValue("fallback-value");
    }

    @Test
    void beforeExecution_shouldIgnoreTimeoutWhenConfiguredToIgnore() {
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);

        SideComputeContext scCtx = new SideComputeContext();
        Map<String, Object> globalMap = new HashMap<>();

        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
        when(execCtx.getContext()).thenReturn(globalMap);

        scCtx.getOrCreateFuture("slow-key");

        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("slow-key").timeout(Duration.ofMillis(50))
                .onTimeoutIgnore().build();

        processor.beforeExecution("input", opCtx);

        assertThat(globalMap).doesNotContainKey(SideComputeKeys.valueKey("slow-key"));
    }

    @Test
    void beforeExecution_shouldUseDefaultSafetyTimeoutWhenNoTimeoutIsConfigured() {
        // Given
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);

        SideComputeContext scCtx = new SideComputeContext();
        Map<String, Object> globalMap = new HashMap<>();

        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
        when(execCtx.getContext()).thenReturn(globalMap);

        scCtx.getOrCreateFuture("never-completes");

        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("never-completes")
                .timeout(null)
                .safetyTimeout(Duration.ofMillis(50))
                .onTimeoutFail()
                .build();

        // When / Then
        assertThatThrownBy(() -> processor.beforeExecution("input", opCtx))
                .isInstanceOf(SideComputeTimeoutException.class)
                .hasMessageContaining("never-completes")
                .hasMessageContaining("PT0.05S");
    }

    @Test
    void beforeExecution_shouldRestoreInterruptFlagWhenInterruptedWhileWaiting() {
        // Given
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);

        SideComputeContext scCtx = new SideComputeContext();
        Map<String, Object> globalMap = new HashMap<>();

        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
        when(execCtx.getContext()).thenReturn(globalMap);

        scCtx.getOrCreateFuture("interrupted-key");
        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("interrupted-key")
                .timeout(Duration.ofSeconds(1))
                .build();

        try {
            Thread.currentThread().interrupt();

            // When / Then
            assertThatThrownBy(() -> processor.beforeExecution("input", opCtx))
                    .isInstanceOf(SideComputeExecutionException.class)
                    .hasRootCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted())
                    .as("the interrupted flag is restored before propagating the failure")
                    .isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void beforeExecution_shouldRejectNullResolvedValue() {
        // Given
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);
        SideComputeContext sideComputeContext = new SideComputeContext();
        Map<String, Object> globalMap = new HashMap<>();
        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(sideComputeContext);
        when(execCtx.getContext()).thenReturn(globalMap);
        sideComputeContext.getOrCreateFuture("null-result").complete(null);
        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("null-result").build();

        // When / Then
        assertThatThrownBy(() -> processor.beforeExecution("input", opCtx))
                .isInstanceOf(SideComputeExecutionException.class)
                .hasRootCauseMessage("Side compute 'null-result' returned null; null results are not supported");
        assertThat(globalMap).doesNotContainKey(SideComputeKeys.valueKey("null-result"));
    }

    @Test
    void beforeExecution_shouldRejectNullFallbackValue() {
        // Given
        ExecutionContext execCtx = mock(ExecutionContext.class);
        StationExecutionContext opCtx = mock(StationExecutionContext.class);
        SideComputeContext sideComputeContext = new SideComputeContext();
        when(opCtx.getGlobalContext()).thenReturn(execCtx);
        when(execCtx.getSideComputeContext()).thenReturn(sideComputeContext);
        when(execCtx.getContext()).thenReturn(new HashMap<>());
        SideComputeWaitProcessor processor = SideComputeWaitProcessor.builder("null-fallback")
                .timeout(Duration.ofMillis(10))
                .onTimeoutUseFallback(() -> null)
                .build();

        // When / Then
        assertThatThrownBy(() -> processor.beforeExecution("input", opCtx))
                .isInstanceOf(SideComputeExecutionException.class)
                .hasRootCauseMessage("Side compute 'null-fallback' fallback returned null; null results are not supported");
        assertThat(sideComputeContext.getOrCreateFuture("null-fallback")).isCompletedExceptionally();
    }

    @Test
    void builder_shouldRejectInvalidTimeoutConfiguration() {
        assertThatThrownBy(() -> SideComputeWaitProcessor.builder("timeout").timeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be strictly positive");
        assertThatThrownBy(() -> SideComputeWaitProcessor.builder("timeout").timeout(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be strictly positive");
        assertThatThrownBy(() -> SideComputeWaitProcessor.builder("timeout").safetyTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("safetyTimeout must be strictly positive");
        assertThatThrownBy(() -> SideComputeWaitProcessor.builder("timeout").onTimeoutUseFallback(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fallback must not be null");
    }

}
