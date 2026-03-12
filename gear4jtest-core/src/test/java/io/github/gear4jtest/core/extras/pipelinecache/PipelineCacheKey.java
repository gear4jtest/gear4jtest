package io.github.gear4jtest.core.extras.pipelinecache;

import java.util.Arrays;
import java.util.Objects;

public final class PipelineCacheKey {

  private final String pipelineId;
  private final String pipelineVersion;
  private final byte[] inputFingerprint;
  private final byte[] contextFingerprint;

  public PipelineCacheKey(
      String pipelineId,
      String pipelineVersion,
      byte[] inputFingerprint,
      byte[] contextFingerprint) {
    this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
    this.pipelineVersion = Objects.requireNonNull(pipelineVersion, "pipelineVersion");
    this.inputFingerprint = Objects.requireNonNull(inputFingerprint, "inputFingerprint").clone();
    this.contextFingerprint = Objects.requireNonNull(contextFingerprint, "contextFingerprint").clone();
  }

  public String pipelineId() {
    return pipelineId;
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
    if (this == o) return true;
    if (!(o instanceof PipelineCacheKey that)) return false;
    return pipelineId.equals(that.pipelineId)
        && pipelineVersion.equals(that.pipelineVersion)
        && Arrays.equals(inputFingerprint, that.inputFingerprint)
        && Arrays.equals(contextFingerprint, that.contextFingerprint);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(pipelineId, pipelineVersion);
    result = 31 * result + Arrays.hashCode(inputFingerprint);
    result = 31 * result + Arrays.hashCode(contextFingerprint);
    return result;
  }
}
