package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class UnaryIfElseContainerStation<A> extends ContainerBaseStation<A, A> {
    private final String elseBranchId;
    private final AbstractStation<A, A> elseOp;

    private UnaryIfElseContainerStation(List<Branch<A>> branches,
                                        FlowConfig flowConfig,
                                        String elseBranchId,
                                        AbstractStation<A, A> elseOp) {
        super(branches, null, false, null, flowConfig, null, true);
        this.elseBranchId = elseBranchId;
        this.elseOp = elseOp;
    }

    public String getElseBranchId() {
        return elseBranchId;
    }

    public AbstractStation<A, A> getElseOp() {
        return elseOp;
    }

    public static class Builder<A> {
        private final List<Branch<A>> branches = new ArrayList<>();
        private FlowConfig flowConfig;
        private String elseBranchId;
        private AbstractStation<A, A> elseOp;

        public Builder<A> flowConfig(FlowConfig flowConfig) {
            this.flowConfig = flowConfig;
            return this;
        }

        public Builder<A> conditionally(String id,
                                        AbstractStation<A, A> operationDefinition,
                                        Condition<A> condition) {
            this.branches
                    .add(new Branch.Builder<A>().withId(id).withCondition(condition).withOperation(operationDefinition)
                            .build());
            return this;
        }

        public UnaryIfElseContainerStation<A> elseOp(String id, AbstractStation<A, A> station) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("else branch id is required");
            }

            this.elseBranchId = id;
            this.elseOp = Objects.requireNonNull(station, "else branch station is required");
            return build();
        }

        public UnaryIfElseContainerStation<A> build() {
            ContainerBaseStation.validateUniqueBranchIds(branches);
            validateElseBranchId();
            return new UnaryIfElseContainerStation<>(branches, flowConfig, elseBranchId, elseOp);
        }

        private void validateElseBranchId() {
            if (elseOp == null) {
                return;
            }

            if (elseBranchId == null || elseBranchId.isBlank()) {
                throw new IllegalArgumentException("else branch id is required");
            }

            Set<String> ids = new HashSet<>();
            for (Branch<A> branch : branches) {
                ids.add(branch.getId());
            }
            if (!ids.add(elseBranchId)) {
                throw new IllegalArgumentException("Container contains duplicated branch id '" + elseBranchId + "'");
            }
        }
    }
}
