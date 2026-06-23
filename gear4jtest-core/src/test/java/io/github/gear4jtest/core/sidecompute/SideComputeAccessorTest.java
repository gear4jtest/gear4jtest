package io.github.gear4jtest.core.sidecompute;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SideComputeAccessorTest {
    @Test
    void get_shouldReturnResolvedValueFromContext() {
        ExecutionContext execCtx = mock(ExecutionContext.class);
        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(SideComputeKeys.valueKey("bigStuff"), "value-123");

        when(execCtx.getContext()).thenReturn(ctxMap);

        DefaultSideComputeAccessor accessor = new DefaultSideComputeAccessor(execCtx);

        String value = accessor.get("bigStuff", String.class);

        assertThat(value).isEqualTo("value-123");
    }

    @Test
    void get_shouldThrowIfNoResolvedValue() {
        ExecutionContext execCtx = mock(ExecutionContext.class);
        Map<String, Object> ctxMap = new HashMap<>();
        when(execCtx.getContext()).thenReturn(ctxMap);

        DefaultSideComputeAccessor accessor = new DefaultSideComputeAccessor(execCtx);

        assertThatThrownBy(() -> accessor.get("missing", String.class)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No resolved side compute value for key 'missing'");
    }

    @Test
    void valueKey_shouldRejectReservedOrBlankUserKeys() {
        assertThatThrownBy(() -> SideComputeKeys.valueKey("__sidecompute_value__:already-prefixed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved prefix");
        assertThatThrownBy(() -> SideComputeKeys.valueKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void isPresent_shouldReturnTrueWhenValueExists() {
        ExecutionContext execCtx = mock(ExecutionContext.class);
        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(SideComputeKeys.valueKey("bigStuff"), 42);
        when(execCtx.getContext()).thenReturn(ctxMap);

        DefaultSideComputeAccessor accessor = new DefaultSideComputeAccessor(execCtx);

        assertThat(accessor.isPresent("bigStuff")).isTrue();
        assertThat(accessor.isPresent("other")).isFalse();
    }
}
