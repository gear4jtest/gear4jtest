package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.config.FlowConfig;

public class Container2Station<IN, OUT, A, B> extends ContainerBaseStation<IN, OUT> {
    private Container2Station() {
        super(java.util.List.of(), null);
    }

    @FunctionalInterface
    public interface Container2DFunction<A, B, C> extends ContainerFunction<C> {
        C applya(A a, B b);

        @Override
        @SuppressWarnings("unchecked")
        default C apply(Object... objects) {
            if (objects == null || objects.length != 2) {
                throw new IllegalArgumentException("Expected exactly two container results");
            }
            return applya((A) objects[0], (B) objects[1]);
        }
    }

    public static class Builder<IN, OUT, A, B> {
        private final List<Branch<IN>> branches = new ArrayList<>();
        private final boolean isParallel;
        private final ExecutorService executorService;
        private final FlowConfig flowConfig;
        private final Duration awaitTimeout;

        public Builder(Container1Station.Builder<IN, OUT, A> parent, Branch<IN> branch) {
            this.branches.addAll(parent.branches);
            this.branches.add(branch);
            this.isParallel = parent.isParallel;
            this.executorService = parent.executorService;
            this.flowConfig = parent.flowConfig;
            this.awaitTimeout = parent.awaitTimeout;
        }

        public <C> ContainerBaseStation<IN, C> returns(Container2DFunction<A, B, C> func) {
            return ContainerBaseStation.buildStation(branches,
                                                     isParallel,
                                                     executorService,
                                                     flowConfig,
                                                     awaitTimeout,
                                                     func);
        }

        public ContainerBaseStation<IN, Void> build() {
            return ContainerBaseStation.buildStation(branches,
                                                     isParallel,
                                                     executorService,
                                                     flowConfig,
                                                     awaitTimeout,
                                                     null);
        }
    }
}
