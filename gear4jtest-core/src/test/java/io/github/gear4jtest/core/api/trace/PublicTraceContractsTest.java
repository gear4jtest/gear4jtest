package io.github.gear4jtest.core.api.trace;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicTraceContractsTest {
    @Test
    void publicContracts_shouldExposeViewsInsteadOfMutableRuntimeImplementations() throws NoSuchMethodException {
        assertThat(ExecutionResult.class.getMethod("getExecution").getReturnType()).isEqualTo(RunTrace.class);
        assertThat(StationExecutionContext.class.getMethod("getRecord").getReturnType()).isEqualTo(StationTrace.class);
    }

    @Test
    void traceViews_shouldNotDeclareMutationMethods() {
        assertThat(Arrays.stream(RunTrace.class.getMethods()).map(Method::getName))
                .noneMatch(PublicTraceContractsTest::isMutationMethod);
        assertThat(Arrays.stream(StationTrace.class.getMethods()).map(Method::getName))
                .noneMatch(PublicTraceContractsTest::isMutationMethod);
    }

    @Test
    void traceViews_shouldExposeUnmodifiableContextMaps() {
        AssemblyRunTrace mutableRun = new AssemblyRunTrace(UUID.randomUUID(), "line", Map.of());
        mutableRun.setContext(new HashMap<>(Map.of("key", "value")));
        RunTrace runTrace = mutableRun;

        StationLogTrace mutableStation = StationLogTrace.start(UUID.randomUUID(), "station", null);
        mutableStation.mutableContext().put("key", "value");
        StationTrace stationTrace = mutableStation;

        assertThatThrownBy(() -> runTrace.getContext().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stationTrace.getContext().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static boolean isMutationMethod(String name) {
        return name.startsWith("set") || name.startsWith("mark") || name.startsWith("add");
    }
}
