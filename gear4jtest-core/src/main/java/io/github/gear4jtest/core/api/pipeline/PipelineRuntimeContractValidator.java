package io.github.gear4jtest.core.api.pipeline;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;

/**
 * Validation helpers for pipeline runtime contracts.
 */
public final class PipelineRuntimeContractValidator {
    private PipelineRuntimeContractValidator() {
    }

    public static void validateConfigurationCoherence(PipelineRuntimeContract contract,
                                                      PersistenceConfiguration persistence,
                                                      EventHandlingDefinition eventHandlingDefinition,
                                                      List<RuntimeExtension> defaultExtensions) {
        Objects.requireNonNull(contract, "contract must not be null");

        boolean hasRuntimeConfiguration = persistence != null || eventHandlingDefinition != null
                || (defaultExtensions != null && !defaultExtensions.isEmpty());

        if (contract.getInlinePolicy() == InlinePolicy.ALLOWED_WHEN_CONFIGLESS && hasRuntimeConfiguration) {
            throw new IllegalStateException(
                    "Pipeline runtime contract allows inline execution only when configless, but runtime configuration is present");
        }

        if (!contract.allowsInline()) {
            return;
        }

        if (persistence != null) {
            throw new IllegalStateException(
                    "Pipeline runtime contract allows inline execution, but persistence is run-scoped and requires NESTED_RUN");
        }

        if (eventHandlingDefinition != null && !contract.requires(RuntimeRequirement.defaultEventHandling())) {
            throw new IllegalStateException(
                    "Pipeline runtime contract allows inline execution with event handling, but does not declare the default event-handling requirement");
        }

        if (defaultExtensions == null || defaultExtensions.isEmpty()) {
            return;
        }

        for (RuntimeExtension extension : defaultExtensions) {
            if (extension instanceof RunInterceptorExtension || extension instanceof RunLifecycleExtension) {
                throw new IllegalStateException(
                        "Pipeline runtime contract allows inline execution, but run-scoped extension "
                                + extension.getClass().getName() + " requires NESTED_RUN");
            }
            if (extension instanceof ExecutorWrapperExtension) {
                throw new IllegalStateException(
                        "Pipeline runtime contract allows inline execution, but executor wrapper extension "
                                + extension.getClass().getName() + " requires NESTED_RUN");
            }
            if ((extension instanceof StationWrapperExtension || extension instanceof StationLifecycleExtension)
                    && !contract.requires(RuntimeRequirement.stationExtension(extension.getClass()))) {
                throw new IllegalStateException(
                        "Pipeline runtime contract allows inline execution with station extension "
                                + extension.getClass().getName()
                                + ", but does not declare it as a mandatory runtime requirement");
            }
        }
    }

    public static void validateInlineAllowed(AssemblyLine<?, ?> childPipeline, PipelineRuntimeContract parentContract) {
        Objects.requireNonNull(childPipeline, "childPipeline must not be null");

        PipelineRuntimeContract childContract = childPipeline.getConfiguration().getRuntimeContract();
        if (!childContract.allowsInline()) {
            throw new IllegalStateException("Pipeline '" + childPipeline.getId() + ":" + childPipeline.getVersion()
                    + "' cannot be executed inline. Use NESTED_RUN instead.");
        }

        if (childContract.getInlinePolicy() == InlinePolicy.ALLOWED_WHEN_CONFIGLESS) {
            return;
        }

        Set<RuntimeRequirement> provided = parentContract == null ? Set.of()
                : new HashSet<>(parentContract.getProvidedRequirements());

        List<RuntimeRequirement> missing = childContract.getMandatoryRequirements().stream()
                .filter(requirement -> !provided.contains(requirement))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Pipeline '" + childPipeline.getId() + ":" + childPipeline.getVersion()
                    + "' cannot be executed inline because mandatory runtime requirements are missing from parent: "
                    + missing);
        }
    }
}
