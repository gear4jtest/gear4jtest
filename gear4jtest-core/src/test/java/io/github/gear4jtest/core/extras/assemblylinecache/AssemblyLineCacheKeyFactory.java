package io.github.gear4jtest.core.extras.assemblylinecache;

import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.extras.history.fingerprint.ContextFingerprintStrategy;
import io.github.gear4jtest.core.extras.history.fingerprint.FingerprintContext;
import io.github.gear4jtest.core.extras.history.fingerprint.FingerprintStrategy;

public final class AssemblyLineCacheKeyFactory {
    private final FingerprintStrategy<Object> inputFingerprintStrategy;
    private final ContextFingerprintStrategy contextFingerprintStrategy;

    public AssemblyLineCacheKeyFactory(FingerprintStrategy<Object> inputFingerprintStrategy,
                                       ContextFingerprintStrategy contextFingerprintStrategy) {
        this.inputFingerprintStrategy = Objects.requireNonNull(inputFingerprintStrategy, "inputFingerprintStrategy");
        this.contextFingerprintStrategy = Objects.requireNonNull(contextFingerprintStrategy,
                                                                 "contextFingerprintStrategy");
    }

    public AssemblyLineCacheKey create(String assemblyLineId,
                                       String pipelineVersion,
                                       Object input,
                                       ExecutionContext executionContext) {

        FingerprintContext fingerprintContext = new FingerprintContext(assemblyLineId, pipelineVersion);

        byte[] inputFingerprint = inputFingerprintStrategy.fingerprint(input, fingerprintContext);
        byte[] contextFingerprint = contextFingerprintStrategy.fingerprint(executionContext, fingerprintContext);

        return new AssemblyLineCacheKey(assemblyLineId, pipelineVersion, inputFingerprint, contextFingerprint);
    }
}
