package io.github.gear4jtest.core.api.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime contract attached to a pipeline.
 *
 * <p>The contract is the declarative source of truth used to decide whether another pipeline may
 * execute this pipeline inline. Mechanical validation only checks that the actual runtime
 * configuration is coherent with this declaration.</p>
 */
public final class PipelineRuntimeContract {

    private final InlinePolicy inlinePolicy;
    private final List<RuntimeRequirement> mandatoryRequirements;
    private final List<RuntimeRequirement> providedRequirements;

    private PipelineRuntimeContract(
            InlinePolicy inlinePolicy,
            List<RuntimeRequirement> mandatoryRequirements,
            List<RuntimeRequirement> providedRequirements) {
        this.inlinePolicy = Objects.requireNonNull(inlinePolicy, "inlinePolicy must not be null");
        this.mandatoryRequirements = mandatoryRequirements == null
                ? List.of()
                : List.copyOf(mandatoryRequirements);
        this.providedRequirements = providedRequirements == null
                ? List.of()
                : List.copyOf(providedRequirements);
    }

    public static PipelineRuntimeContract inlineConfigless() {
        return builder().inlinePolicy(InlinePolicy.ALLOWED_WHEN_CONFIGLESS).build();
    }

    public static PipelineRuntimeContract nestedRunOnly() {
        return builder().inlinePolicy(InlinePolicy.ALWAYS_FORBIDDEN).build();
    }

    public static PipelineRuntimeContract inlineWhenRequirementsSatisfied(List<RuntimeRequirement> requirements) {
        return builder()
                .inlinePolicy(InlinePolicy.ALLOWED_WHEN_REQUIREMENTS_SATISFIED)
                .mandatoryRequirements(requirements)
                .build();
    }

    public InlinePolicy getInlinePolicy() {
        return inlinePolicy;
    }

    public List<RuntimeRequirement> getMandatoryRequirements() {
        return mandatoryRequirements;
    }

    public List<RuntimeRequirement> getProvidedRequirements() {
        return providedRequirements;
    }

    public boolean allowsInline() {
        return inlinePolicy.allowsInline();
    }

    public boolean requires(RuntimeRequirement requirement) {
        return mandatoryRequirements.contains(requirement);
    }

    public boolean provides(RuntimeRequirement requirement) {
        return providedRequirements.contains(requirement);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private InlinePolicy inlinePolicy = InlinePolicy.ALLOWED_WHEN_CONFIGLESS;
        private final List<RuntimeRequirement> mandatoryRequirements = new ArrayList<>();
        private final List<RuntimeRequirement> providedRequirements = new ArrayList<>();

        public Builder inlinePolicy(InlinePolicy inlinePolicy) {
            this.inlinePolicy = Objects.requireNonNull(inlinePolicy, "inlinePolicy must not be null");
            return this;
        }

        public Builder mandatoryRequirement(RuntimeRequirement requirement) {
            if (requirement != null) {
                this.mandatoryRequirements.add(requirement);
            }
            return this;
        }

        public Builder mandatoryRequirements(List<RuntimeRequirement> requirements) {
            if (requirements != null) {
                requirements.forEach(this::mandatoryRequirement);
            }
            return this;
        }

        public Builder providedRequirement(RuntimeRequirement requirement) {
            if (requirement != null) {
                this.providedRequirements.add(requirement);
            }
            return this;
        }

        public Builder providedRequirements(List<RuntimeRequirement> requirements) {
            if (requirements != null) {
                requirements.forEach(this::providedRequirement);
            }
            return this;
        }

        public PipelineRuntimeContract build() {
            return new PipelineRuntimeContract(inlinePolicy, mandatoryRequirements, providedRequirements);
        }
    }
}
