package io.github.gear4jtest.external.api;

import java.io.IOException;

import io.github.gear4jtest.external.api.exception.ExternalValidationException;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;

import static java.util.Objects.requireNonNull;

final class AssemblyLineLookupService {
    private final OperationChainObjectRepository objectRepository;
    private final GeneratedAssemblyLineLoader loader;
    private final AssemblyLineAliasService aliasService;

    AssemblyLineLookupService(OperationChainObjectRepository objectRepository,
                              GeneratedAssemblyLineLoader loader,
                              AssemblyLineAliasService aliasService) {
        this.objectRepository = requireNonNull(objectRepository);
        this.loader = requireNonNull(loader);
        this.aliasService = requireNonNull(aliasService);
    }

    GeneratedAssemblyLine<?, ?> getOperationChain(String alId, String version, ExecutionMode mode) throws IOException {
        var obj = objectRepository.find(alId, version, mode)
                .orElseThrow(() -> new OperationChainNotFoundException(
                        "Object not found for %s:%s:%s".formatted(alId, version, mode)));
        return loader.loadOrCompile(alId, obj);
    }

    GeneratedAssemblyLine<?, ?> getLatestRun(String alId, ExecutionMode mode) throws IOException {
        if (mode != ExecutionMode.RUN) {
            throw new ExternalValidationException("Latest is only supported for RUN mode");
        }
        var latest = objectRepository.findLatestRun(alId)
                .orElseThrow(() -> new OperationChainNotFoundException("No RUN object found for alId=" + alId));
        var resolution = aliasService.beginLatestResolution(alId, AssemblyLineIdentifiers.toInternalLoaderId(latest));
        GeneratedAssemblyLine<?, ?> generated = loader.loadOrCompile(alId, latest);
        aliasService.completeLatestResolution(resolution);
        return generated;
    }
}
