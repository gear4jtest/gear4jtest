package io.github.gear4jtest.core.api.behavior;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseErrorTest {
    @Test
    void fields_shouldBePrivateAndFinal() {
        for (var field : BaseError.class.getDeclaredFields()) {
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        }
    }

    @Test
    void builder_shouldCreateImmutableErrorConfiguration() {
        Condition<String> condition = (input, ctx) -> true;
        Runnable action = () -> {
        };

        BaseError.SafeError<String> error = new BaseError.SafeError.Builder<String>(
                SignalType.FATAL, IllegalArgumentException.class)
                .condition(condition)
                .action(action)
                .build();

        assertThat(error.getSignalType()).isEqualTo(SignalType.FATAL);
        assertThat(error.getThrowableType()).isEqualTo(IllegalArgumentException.class);
        assertThat(error.getCondition()).isSameAs(condition);
        assertThat(error.getAction()).isSameAs(action);
    }
}
