package io.github.gear4jtest.core.api.util;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThematicBuilderFacadesTest {
    @Test
    void thematicFacades_shouldExposeFocusedBuilderFamilies() {
        assertThat(Errors.<String>ignore(RuntimeException.class).build().getSignalType()).isEqualTo(SignalType.IGNORE);
        assertThat(RuntimeDefinitions.eventHandling().build()).isNotNull();
        assertThat(Stations.<String, String, IdentityOperator>processingOperation("work", IdentityOperator.class)
                .build()
                .getId()).isEqualTo("work");
        assertThat(Stations.fatalSignal(String.class).build().getSignalType()).isEqualTo(SignalType.FATAL);
        assertThat(AssemblyLines.<String>createAssemblyLine("line").build().getId()).isEqualTo("line");
    }

    private static final class IdentityOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext ctx) {
            return input;
        }
    }
}
