package io.github.gear4jtest.core.api.station;

import java.util.Objects;

import io.github.gear4jtest.core.api.behavior.BranchCondition;
import io.github.gear4jtest.core.api.behavior.Condition;

/**
 * Typed named branch declaration for multi-branch containers.
 *
 * <p>
 * The station carried by the branch defines the output type. Aggregators can
 * then retrieve the matching value from {@link ContainerResults} with
 * {@link ContainerResults#get(ContainerBranch)} without repeating the output
 * class or depending on positional {@code Object...} indexes.
 */
public final class ContainerBranch<IN, OUT> {
    private final String id;
    private final AbstractStation<IN, OUT> station;
    private final Condition<IN> condition;
    private final BranchCondition<IN> siblingCondition;

    private ContainerBranch(String id,
                            AbstractStation<IN, OUT> station,
                            Condition<IN> condition,
                            BranchCondition<IN> siblingCondition) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("branch id is required");
        }
        this.id = id;
        this.station = Objects.requireNonNull(station, "branch station is required");
        this.condition = condition;
        this.siblingCondition = siblingCondition;
    }

    public static <IN, OUT> ContainerBranch<IN, OUT> of(String id, AbstractStation<IN, OUT> station) {
        return new ContainerBranch<>(id, station, null, null);
    }

    public String id() {
        return id;
    }

    public AbstractStation<IN, OUT> station() {
        return station;
    }

    public Condition<IN> condition() {
        return condition;
    }

    public BranchCondition<IN> siblingCondition() {
        return siblingCondition;
    }

    public ContainerBranch<IN, OUT> when(Condition<IN> condition) {
        return new ContainerBranch<>(id, station, condition, siblingCondition);
    }

    public ContainerBranch<IN, OUT> whenSiblings(BranchCondition<IN> siblingCondition) {
        return new ContainerBranch<>(id, station, condition, siblingCondition);
    }

    public ContainerBranch<IN, OUT> withConditions(Condition<IN> condition,
                                                   BranchCondition<IN> siblingCondition) {
        return new ContainerBranch<>(id, station, condition, siblingCondition);
    }
}
