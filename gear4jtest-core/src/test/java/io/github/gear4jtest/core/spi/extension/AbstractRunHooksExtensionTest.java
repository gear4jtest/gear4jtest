package io.github.gear4jtest.core.spi.extension;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractRunHooksExtensionTest {
    @Test
    void aroundRun_shouldCallSuccessHooksAroundDelegate() {
        // Given
        RecordingRunHooksExtension extension = new RecordingRunHooksExtension();
        ExecutionResult<String> result = ExecutionResult.success("ok", null);

        // When
        ExecutionResult<String> actual = extension.aroundRun(null, RunRequest.builder().build(), null, () -> result);

        // Then
        assertThat(actual).isSameAs(result);
        assertThat(extension.events).containsExactly("start", "result:ok", "end");
    }

    @Test
    void aroundRun_shouldCallExceptionAndEndHooksWhenDelegateFails() {
        // Given
        RecordingRunHooksExtension extension = new RecordingRunHooksExtension();
        IllegalStateException failure = new IllegalStateException("boom");

        // When / Then
        assertThatThrownBy(() -> extension.<Object, Object>aroundRun(null, RunRequest.builder().build(), null, () -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(extension.events).containsExactly("start", "exception:boom", "end");
    }

    private static final class RecordingRunHooksExtension extends AbstractRunHooksExtension {
        private final List<String> events = new ArrayList<>();

        @Override
        protected void onStart(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx) {
            events.add("start");
        }

        @Override
        protected void onResult(AssemblyLine<?, ?> pipeline,
                                RunRequest request,
                                ExecutionContext ctx,
                                ExecutionResult<?> result) {
            events.add("result:" + result.getResult());
        }

        @Override
        protected void onException(AssemblyLine<?, ?> pipeline,
                                   RunRequest request,
                                   ExecutionContext ctx,
                                   RuntimeException error) {
            events.add("exception:" + error.getMessage());
        }

        @Override
        protected void onEnd(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx) {
            events.add("end");
        }
    }
}
