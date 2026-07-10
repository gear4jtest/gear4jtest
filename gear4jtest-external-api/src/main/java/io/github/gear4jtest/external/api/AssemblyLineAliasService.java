package io.github.gear4jtest.external.api;

import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;

import static java.util.Objects.requireNonNull;

final class AssemblyLineAliasService {
    private final ClassLoaderRegistry classLoaderRegistry;
    private long latestGeneration;

    AssemblyLineAliasService(ClassLoaderRegistry classLoaderRegistry) {
        this.classLoaderRegistry = requireNonNull(classLoaderRegistry);
    }

    synchronized void invalidateLatestRun(String alId) {
        String requiredAlId = requireNonNull(alId);
        latestGeneration++;
        classLoaderRegistry.clearAlias(AssemblyLineIdentifiers.latestAlias(requiredAlId));
    }

    String resolveLatestRunLoaderId(String alId) {
        return classLoaderRegistry.resolveAlias(AssemblyLineIdentifiers.latestAlias(requireNonNull(alId)));
    }

    synchronized LatestResolution beginLatestResolution(String alId, String resolvedLoaderId) {
        requireNonNull(alId);
        requireNonNull(resolvedLoaderId);
        String alias = AssemblyLineIdentifiers.latestAlias(alId);
        String current = classLoaderRegistry.resolveAlias(alias);
        if (current != null && !current.equals(resolvedLoaderId)) {
            classLoaderRegistry.clearAlias(alias);
        }
        return new LatestResolution(alId, resolvedLoaderId, latestGeneration);
    }

    synchronized void completeLatestResolution(LatestResolution resolution) {
        requireNonNull(resolution);
        if (latestGeneration == resolution.generation()) {
            classLoaderRegistry.setAlias(AssemblyLineIdentifiers.latestAlias(resolution.alId()),
                                         resolution.internalLoaderId());
        }
    }

    record LatestResolution(String alId, String internalLoaderId, long generation) {}
}
