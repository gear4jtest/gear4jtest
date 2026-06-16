package io.github.gear4jtest.external.api;

import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.model.OperationChainObject;

import static java.util.Objects.requireNonNull;

final class AssemblyLineAliasService {
    private final ClassLoaderRegistry classLoaderRegistry;

    AssemblyLineAliasService(ClassLoaderRegistry classLoaderRegistry) {
        this.classLoaderRegistry = requireNonNull(classLoaderRegistry);
    }

    void invalidateLatestRun(String alId) {
        classLoaderRegistry.clearAlias(AssemblyLineIdentifiers.latestAlias(requireNonNull(alId)));
    }

    String resolveLatestRunLoaderId(String alId) {
        return classLoaderRegistry.resolveAlias(AssemblyLineIdentifiers.latestAlias(requireNonNull(alId)));
    }

    void clearLatestAliasIfResolutionChanged(String alId, String resolvedLoaderId) {
        String alias = AssemblyLineIdentifiers.latestAlias(alId);
        String current = classLoaderRegistry.resolveAlias(alias);
        if (current != null && !current.equals(resolvedLoaderId)) {
            classLoaderRegistry.clearAlias(alias);
        }
    }

    void registerLatestAliasIfNeeded(String alId, OperationChainObject obj, String internalLoaderId) {
        if (obj.mode() == ExecutionMode.RUN) {
            classLoaderRegistry.setAlias(AssemblyLineIdentifiers.latestAlias(alId), internalLoaderId);
        }
    }
}
