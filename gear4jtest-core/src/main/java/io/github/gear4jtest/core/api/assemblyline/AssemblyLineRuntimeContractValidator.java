package io.github.gear4jtest.core.api.assemblyline;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;

/**
 * Validation helpers for assembly line runtime contracts.
 */
public final class AssemblyLineRuntimeContractValidator {
    private AssemblyLineRuntimeContractValidator() {
    }

    public static void validateConfigurationCoherence(AssemblyLineRuntimeContract contract,
                                                      PersistenceConfiguration persistence,
                                                      EventHandlingDefinition eventHandlingDefinition,
                                                      List<RuntimeExtension> defaultExtensions) {
        Objects.requireNonNull(contract, "contract must not be null");

        boolean hasRuntimeConfiguration = persistence != null || eventHandlingDefinition != null
                || (defaultExtensions != null && !defaultExtensions.isEmpty());

        if (contract.getInlinePolicy() == InlinePolicy.ALLOWED_WHEN_CONFIGLESS && hasRuntimeConfiguration) {
            throw new IllegalStateException(
                    "AssemblyLine runtime contract allows inline execution only when configless, but runtime configuration is present");
        }

        if (!contract.allowsInline()) {
            return;
        }

        if (persistence != null) {
            throw new IllegalStateException(
                    "AssemblyLine runtime contract allows inline execution, but persistence is run-scoped and requires NESTED_RUN");
        }

        if (eventHandlingDefinition != null && !contract.requires(RuntimeRequirement.defaultEventHandling())) {
            throw new IllegalStateException(
                    "AssemblyLine runtime contract allows inline execution with event handling, but does not declare the default event-handling requirement");
        }

        if (defaultExtensions == null || defaultExtensions.isEmpty()) {
            return;
        }

        for (RuntimeExtension extension : defaultExtensions) {
            if (extension.requiresNestedRun()) {
                throw new IllegalStateException(
                        "AssemblyLine runtime contract allows inline execution, but runtime extension "
                                + extension.getClass().getName() + " requires NESTED_RUN");
            }

            RuntimeRequirement inlineRequirement = extension.requiredInlineRequirement();
            if (inlineRequirement != null && !contract.requires(inlineRequirement)) {
                throw new IllegalStateException(
                        "AssemblyLine runtime contract allows inline execution with runtime extension "
                                + extension.getClass().getName()
                                + ", but does not declare required runtime requirement " + inlineRequirement);
            }
        }
    }

    public static void validateInlineAllowed(AssemblyLine<?, ?> childAssemblyLine,
                                             AssemblyLineRuntimeContract parentContract) {
        Objects.requireNonNull(childAssemblyLine, "childAssemblyLine must not be null");

        AssemblyLineRuntimeContract childContract = childAssemblyLine.getConfiguration().getRuntimeContract();
        if (!childContract.allowsInline()) {
            throw new IllegalStateException(
                    "AssemblyLine '" + childAssemblyLine.getId() + ":" + childAssemblyLine.getVersion()
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
            throw new IllegalStateException("AssemblyLine '" + childAssemblyLine.getId() + ":"
                    + childAssemblyLine.getVersion()
                    + "' cannot be executed inline because mandatory runtime requirements are missing from parent: "
                    + missing);
        }
    }
}
