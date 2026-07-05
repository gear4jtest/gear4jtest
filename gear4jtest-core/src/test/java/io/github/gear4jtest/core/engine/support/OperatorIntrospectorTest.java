package io.github.gear4jtest.core.engine.support;

import io.github.gear4jtest.core.api.context.StationParameter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorIntrospectorTest {
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
            private final StationParameter<String> param = StationParameter.<String>newBuilder().build();
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

    /**
     * Plain operators without parameters should be considered stateless.
     */
    static class PlainTransformer {
        // No Parameter field.
    }

    /**
     * Operators with a Parameter field should be detected as stateful.
     */
    static class StatefulByParameter {
        @SuppressWarnings("unused")
        private final StationParameter<String> param = StationParameter.<String>newBuilder().build();
    }

    /**
     * Operator explicitly declaring itself as stateful.
     */
    static class ExplicitStatefulTransformer implements ConcurrencyAwareTransformer {
        @Override
        public WorkerStatefulness statefulness() {
            return WorkerStatefulness.STATEFUL;
        }
    }

    /**
     * Operator explicitly declaring itself as stateless.
     */
    static class ExplicitStatelessTransformer implements ConcurrencyAwareTransformer {
        @Override
        public WorkerStatefulness statefulness() {
            return WorkerStatefulness.STATELESS;
        }
    }

    /**
     * AUTO statefulness with a Parameter field should be detected as stateful.
     */
    static class AutoWithParameter implements ConcurrencyAwareTransformer {
        @SuppressWarnings("unused")
        private final StationParameter<Integer> param = StationParameter.<Integer>newBuilder().build();
    }
}
