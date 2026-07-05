package io.github.gear4jtest.core.engine.support;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ParameterResolutionContextParameterModel;
import io.github.gear4jtest.core.api.context.StationParameter;
import io.github.gear4jtest.core.api.context.StationParameterModel;
import io.github.gear4jtest.core.api.context.StationParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerParamsInjectorParameterTest {
    @Test
    void of_shouldCreatePersistentParameterWithNullDefaultValue() {
        StationParameter<String> parameter = StationParameter.<String>newBuilder().build();

        assertThat(parameter.getValue()).isNull();

        parameter.injectValue("value");
        assertThat(parameter.getValue()).isEqualTo("value");

        // PERSISTENT => aucune remise à zéro
        parameter.afterExecutionCleanup();
        assertThat(parameter.getValue()).isEqualTo("value");
    }

    @Test
    void ofDefault_shouldInitializeWithDefaultValueAndPersistentLifecycle() {
        StationParameter<Integer> parameter = StationParameter.<Integer>newBuilder().defaultValue(123).build();

        assertThat(parameter.getValue()).isEqualTo(123);

        parameter.injectValue(456);
        assertThat(parameter.getValue()).isEqualTo(456);

        // PERSISTENT => ne revient pas à la valeur par défaut
        parameter.afterExecutionCleanup();
        assertThat(parameter.getValue()).isEqualTo(456);
    }

    @Test
    void builder_perExecution_shouldResetToDefaultAfterExecution() {
        StationParameter<String> parameter = StationParameter.<String>newBuilder().defaultValue("default")
                .lifecyclePolicy(StationParameter.LifecyclePolicy.PER_EXECUTION).build();

        // au départ : valeur par défaut
        assertThat(parameter.getValue()).isEqualTo("default");

        parameter.injectValue("runtime");
        assertThat(parameter.getValue()).isEqualTo("runtime");

        parameter.afterExecutionCleanup();
        // PER_EXECUTION => retour à la valeur par défaut
        assertThat(parameter.getValue()).isEqualTo("default");
    }

    @Test
    void parametersBuilder_shouldExposeImmutableSnapshot() {
        StationParameterModel<Operator<?, ?>, String> model = new ParameterResolutionContextParameterModel<>(
                operator -> null, ctx -> "value");
        StationParameters.Builder builder = StationParameters.newBuilder();

        StationParameters parameters = builder.withParameter(model).build();
        builder.withParameter(model);

        assertThat(parameters.getParameters()).containsExactly(model);
        var parameterModels = parameters.getParameters();

        assertThatThrownBy(() -> parameterModels.add(model))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
