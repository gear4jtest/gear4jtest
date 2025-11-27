package io.github.gear4jtest.core.model.refactor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.model.refactor.OperationParamsInjector.Parameter;

class OperationParamsInjectorParameterTest {

    @Test
    void of_shouldCreatePersistentParameterWithNullDefaultValue() {
        Parameter<String> parameter = Parameter.of();

        assertThat(parameter.getValue()).isNull();

        parameter.injectValue("value");
        assertThat(parameter.getValue()).isEqualTo("value");

        // PERSISTENT => aucune remise à zéro
        parameter.afterExecutionCleanup();
        assertThat(parameter.getValue()).isEqualTo("value");
    }

    @Test
    void ofDefault_shouldInitializeWithDefaultValueAndPersistentLifecycle() {
        Parameter<Integer> parameter = Parameter.ofDefault(123);

        assertThat(parameter.getValue()).isEqualTo(123);

        parameter.injectValue(456);
        assertThat(parameter.getValue()).isEqualTo(456);

        // PERSISTENT => ne revient pas à la valeur par défaut
        parameter.afterExecutionCleanup();
        assertThat(parameter.getValue()).isEqualTo(456);
    }

    @Test
    void builder_perExecution_shouldResetToDefaultAfterExecution() {
        Parameter<String> parameter = Parameter.<String>builder()
                .defaultValue("default")
                .perExecution()
                .build();

        // au départ : valeur par défaut
        assertThat(parameter.getValue()).isEqualTo("default");

        parameter.injectValue("runtime");
        assertThat(parameter.getValue()).isEqualTo("runtime");

        parameter.afterExecutionCleanup();
        // PER_EXECUTION => retour à la valeur par défaut
        assertThat(parameter.getValue()).isEqualTo("default");
    }
}
