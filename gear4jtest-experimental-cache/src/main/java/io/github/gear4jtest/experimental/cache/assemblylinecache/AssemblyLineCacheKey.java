package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.util.Arrays;
import java.util.Objects;

public final class AssemblyLineCacheKey {
    private final String assemblyLineId;
    private final String pipelineVersion;
    private final byte[] inputFingerprint;
    private final byte[] contextFingerprint;

    public AssemblyLineCacheKey(String assemblyLineId,
                                String pipelineVersion,
                                byte[] inputFingerprint,
                                byte[] contextFingerprint) {
        this.assemblyLineId = Objects.requireNonNull(assemblyLineId, "assemblyLineId");
        this.pipelineVersion = Objects.requireNonNull(pipelineVersion, "pipelineVersion");
        this.inputFingerprint = Objects.requireNonNull(inputFingerprint, "inputFingerprint").clone();
        this.contextFingerprint = Objects.requireNonNull(contextFingerprint, "contextFingerprint").clone();
    }

    public String assemblyLineId() {
        return assemblyLineId;
    }

    public String pipelineVersion() {
        return pipelineVersion;
    }

    public byte[] inputFingerprint() {
        return inputFingerprint.clone();
    }

    public byte[] contextFingerprint() {
        return contextFingerprint.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AssemblyLineCacheKey that))
            return false;
        return assemblyLineId.equals(that.assemblyLineId) && pipelineVersion.equals(that.pipelineVersion)
                && Arrays.equals(inputFingerprint, that.inputFingerprint)
                && Arrays.equals(contextFingerprint, that.contextFingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(assemblyLineId, pipelineVersion);
        result = 31 * result + Arrays.hashCode(inputFingerprint);
        result = 31 * result + Arrays.hashCode(contextFingerprint);
        return result;
    }
}
