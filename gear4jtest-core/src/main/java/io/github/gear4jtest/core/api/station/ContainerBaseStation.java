package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.behavior.BranchCondition;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

/**
 * A typed group of named branches executed sequentially or through a supplied
 * executor.
 *
 * <p>
 * Parallel executors remain caller-owned. The await timeout bounds Gear4J's
 * wait after branch submission; it requests interruption of pending futures but
 * cannot forcibly terminate user code that ignores interruption.
 * </p>
 */
public class ContainerBaseStation<IN, OUT> extends AbstractStation<IN, OUT> {
    private final List<Branch<IN>> pipelines;
    private final ContainerResultsFunction<OUT> resultsFunc;
    private final boolean parallel;
    private final ExecutorService executorService;
    private final FlowConfig flowConfig;
    private final Duration awaitTimeout;

    protected ContainerBaseStation(String id,
                                   List<Branch<IN>> pipelines,
                                   ContainerResultsFunction<OUT> resultsFunc,
                                   boolean parallel,
                                   ExecutorService executorService,
                                   FlowConfig flowConfig,
                                   Duration awaitTimeout,
                                   boolean unary) {
        super(requireContainerId(id), StationKind.CONTAINER, null, null, null, unary, null, null);
        this.pipelines = pipelines == null || pipelines.isEmpty() ? List.of() : List.copyOf(pipelines);
        validateUniqueBranchIds(this.pipelines);
        validateExecutionConfiguration(this.pipelines, parallel, executorService, awaitTimeout);
        this.resultsFunc = resultsFunc;
        this.parallel = parallel;
        this.executorService = executorService;
        this.flowConfig = flowConfig;
        this.awaitTimeout = awaitTimeout;
    }

    protected static String requireContainerId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("container id is required");
        }
        return id;
    }

    private static void validateExecutionConfiguration(List<? extends Branch<?>> branches,
                                                       boolean parallel,
                                                       ExecutorService executorService,
                                                       Duration awaitTimeout) {
        if (parallel && executorService == null) {
            throw new IllegalArgumentException("parallel container requires an executor service");
        }
        if (awaitTimeout != null && (awaitTimeout.isZero() || awaitTimeout.isNegative())) {
            throw new IllegalArgumentException("container await timeout must be > 0");
        }
        if (!parallel) {
            return;
        }
        for (Branch<?> branch : branches) {
            if (branch.getSiblingCondition() != null) {
                throw new IllegalArgumentException(
                        "Sibling branch conditions are only supported in sequential containers");
            }
        }
    }

    protected static void validateUniqueBranchIds(List<? extends Branch<?>> branches) {
        Set<String> ids = new HashSet<>();

        for (Branch<?> branch : branches) {
            if (branch == null) {
                throw new IllegalArgumentException("Container contains a null branch");
            }

            String branchId = branch.getId();
            if (branchId == null || branchId.isBlank()) {
                throw new IllegalArgumentException("Container contains a branch without explicit id");
            }

            if (!ids.add(branchId)) {
                throw new IllegalArgumentException("Container contains duplicated branch id '" + branchId + "'");
            }
        }
    }

    public List<Branch<IN>> getAssemblyLines() {
        return pipelines;
    }

    public ContainerResultsFunction<OUT> getResultsFunc() {
        return resultsFunc;
    }

    public boolean isParallel() {
        return parallel;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public FlowConfig getFlowConfig() {
        return flowConfig;
    }

    public Duration getAwaitTimeout() {
        return awaitTimeout;
    }

    @FunctionalInterface
    public interface ContainerResultsFunction<OUT> {
        OUT apply(ContainerResults results);
    }

    public static class Builder<IN, OUT> {
        final List<Branch<IN>> branches = new ArrayList<>();
        String id;
        boolean isParallel;
        ExecutorService executorService;
        FlowConfig flowConfig;
        Duration awaitTimeout;

        public Builder() {
        }

        public Builder(ExecutorService executorService) {
            this.isParallel = true;
            this.executorService = executorService;
        }

        public Builder<IN, OUT> id(String id) {
            this.id = id;
            return this;
        }

        public Builder<IN, OUT> flowConfig(FlowConfig flowConfig) {
            this.flowConfig = flowConfig;
            return this;
        }

        public Builder<IN, OUT> awaitTimeout(Duration awaitTimeout) {
            this.awaitTimeout = awaitTimeout;
            return this;
        }

        public <A> Builder<IN, OUT> withBranch(String id, AbstractStation<IN, A> station) {
            return withBranch(ContainerBranch.of(id, station));
        }

        public <A> Builder<IN, OUT> withBranch(String id,
                                               AbstractStation<IN, A> station,
                                               Condition<IN> condition) {
            return withBranch(ContainerBranch.of(id, station), condition);
        }

        public <A> Builder<IN, OUT> withBranch(String id,
                                               AbstractStation<IN, A> station,
                                               BranchCondition<IN> siblingCondition) {
            return withBranch(ContainerBranch.of(id, station), siblingCondition);
        }

        public <A> Builder<IN, OUT> withBranch(String id,
                                               AbstractStation<IN, A> station,
                                               Condition<IN> condition,
                                               BranchCondition<IN> siblingCondition) {
            return withBranch(ContainerBranch.of(id, station), condition, siblingCondition);
        }

        public <A> Builder<IN, OUT> withBranch(ContainerBranch<IN, A> branch) {
            this.branches.add(branch(branch));
            return this;
        }

        public <A> Builder<IN, OUT> withBranch(ContainerBranch<IN, A> branch, Condition<IN> condition) {
            this.branches.add(branch(branch.withConditions(condition, branch.siblingCondition())));
            return this;
        }

        public <A> Builder<IN, OUT> withBranch(ContainerBranch<IN, A> branch, BranchCondition<IN> siblingCondition) {
            this.branches.add(branch(branch.withConditions(branch.condition(), siblingCondition)));
            return this;
        }

        public <A> Builder<IN, OUT> withBranch(ContainerBranch<IN, A> branch,
                                               Condition<IN> condition,
                                               BranchCondition<IN> siblingCondition) {
            this.branches.add(branch(branch.withConditions(condition, siblingCondition)));
            return this;
        }

        private static <IN, A> Branch<IN> branch(ContainerBranch<IN, A> branch) {
            Objects.requireNonNull(branch, "container branch is required");
            return new Branch.Builder<IN>().withId(branch.id()).withCondition(branch.condition())
                    .withSiblingCondition(branch.siblingCondition()).withOperation(branch.station()).build();
        }

        public <C> ContainerBaseStation<IN, C> returns(ContainerResultsFunction<C> func) {
            return ContainerBaseStation.buildStation(id,
                                                     branches,
                                                     isParallel,
                                                     executorService,
                                                     flowConfig,
                                                     awaitTimeout,
                                                     func);
        }

        public ContainerBaseStation<IN, Void> build() {
            return ContainerBaseStation.buildStation(id,
                                                     branches,
                                                     isParallel,
                                                     executorService,
                                                     flowConfig,
                                                     awaitTimeout,
                                                     null);
        }
    }

    static <IN, OUT> ContainerBaseStation<IN, OUT> buildStation(String id,
                                                                List<Branch<IN>> branches,
                                                                boolean isParallel,
                                                                ExecutorService executorService,
                                                                FlowConfig flowConfig,
                                                                Duration awaitTimeout,
                                                                ContainerResultsFunction<OUT> function) {
        validateUniqueBranchIds(branches);
        return new ContainerBaseStation<>(id,
                branches,
                function,
                isParallel,
                executorService,
                flowConfig,
                awaitTimeout,
                false);
    }

    public static class Branch<I> {
        private final String id;
        private final AbstractStation<I, ?> station;
        private final Condition<I> condition;
        private final BranchCondition<I> siblingCondition;

        private Branch(String id,
                       AbstractStation<I, ?> station,
                       Condition<I> condition,
                       BranchCondition<I> siblingCondition) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("branch id is required");
            }
            this.id = id;
            this.station = Objects.requireNonNull(station, "branch station is required");
            this.condition = condition;
            this.siblingCondition = siblingCondition;
        }

        public String getId() {
            return id;
        }

        public AbstractStation<I, ?> getStation() {
            return station;
        }

        public Condition<I> getCondition() {
            return condition;
        }

        public BranchCondition<I> getSiblingCondition() {
            return siblingCondition;
        }

        public static class Builder<I> {
            private String id;
            private AbstractStation<I, ?> station;
            private Condition<I> condition;
            private BranchCondition<I> siblingCondition;

            public Builder<I> withId(String id) {
                this.id = id;
                return this;
            }

            public Builder<I> withOperation(AbstractStation<I, ?> operation) {
                this.station = operation;
                return this;
            }

            public Builder<I> withCondition(Condition<I> condition) {
                this.condition = condition;
                return this;
            }

            public Builder<I> withSiblingCondition(BranchCondition<I> siblingCondition) {
                this.siblingCondition = siblingCondition;
                return this;
            }

            public Branch<I> build() {
                return new Branch<>(id, station, condition, siblingCondition);
            }
        }
    }
}
