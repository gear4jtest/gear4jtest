package io.github.gear4jtest.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gear4jtest.core.engine.support.ConcurrencyAwareTransformer;
import io.github.gear4jtest.core.engine.support.WorkerIntrospector;
import io.github.gear4jtest.core.engine.support.WorkerStatefulness;
import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.engine.support.WorkerParamsInjector.Parameter;

class OperatorIntrospectorTest {

    /**
     * Transformer sans déclarer ConcurrencyAwareTransformer
     * et sans champ Parameter => doit être considéré comme stateless.
     */
    static class PlainTransformer {
        // pas de Parameter
    }

    /**
     * Transformer avec un champ Parameter => doit être détecté comme stateful.
     */
    static class StatefulByParameter {
        @SuppressWarnings("unused")
        private final Parameter<String> param = Parameter.<String>newBuilder().build();
    }

    /**
     * ConcurrencyAwareTransformer déclarant explicitement STATEFUL.
     */
    static class ExplicitStatefulTransformer implements ConcurrencyAwareTransformer {
        @Override
        public WorkerStatefulness statefulness() {
            return WorkerStatefulness.STATEFUL;
        }
    }

    /**
     * ConcurrencyAwareTransformer déclarant explicitement STATELESS.
     */
    static class ExplicitStatelessTransformer implements ConcurrencyAwareTransformer {
        @Override
        public WorkerStatefulness statefulness() {
            return WorkerStatefulness.STATELESS;
        }
    }

    /**
     * ConcurrencyAwareTransformer en AUTO, mais avec un champ Parameter => AUTO + Parameter => stateful.
     */
    static class AutoWithParameter implements ConcurrencyAwareTransformer {
        @SuppressWarnings("unused")
        private final Parameter<Integer> param = Parameter.<Integer>newBuilder().build();
    }

    @Test
    void isStateful_shouldReturnFalseForPlainTransformerWithoutParameters() {
        PlainTransformer transformer = new PlainTransformer();

        boolean stateful = WorkerIntrospector.isStateful(transformer);

        assertThat(stateful).isFalse();
    }

    @Test
    void isStateful_shouldReturnTrueWhenParameterFieldIsPresent() {
        StatefulByParameter transformer = new StatefulByParameter();

        boolean stateful = WorkerIntrospector.isStateful(transformer);

        assertThat(stateful).isTrue();
    }

    @Test
    void isStateful_shouldUseExplicitStatefulFromConcurrencyAwareTransformer() {
        ExplicitStatefulTransformer transformer = new ExplicitStatefulTransformer();

        boolean stateful = WorkerIntrospector.isStateful(transformer);

        assertThat(stateful).isTrue();
    }

    @Test
    void isStateful_shouldUseExplicitStatelessFromConcurrencyAwareTransformerEvenIfParameterPresent() {
        // on triche : on met un champ Parameter malgré tout
        class ExplicitStatelessWithParameter extends ExplicitStatelessTransformer {
            @SuppressWarnings("unused")
            private final Parameter<String> param = Parameter.<String>newBuilder().build();
        }

        ExplicitStatelessWithParameter transformer = new ExplicitStatelessWithParameter();

        boolean stateful = WorkerIntrospector.isStateful(transformer);

        // La déclaration explicite doit dominer
        assertThat(stateful).isFalse();
    }

    @Test
    void isStateful_shouldUseAutoAndDetectByParameterPresence() {
        AutoWithParameter transformer = new AutoWithParameter();

        boolean stateful = WorkerIntrospector.isStateful(transformer);

        assertThat(stateful).isTrue();
    }
}
