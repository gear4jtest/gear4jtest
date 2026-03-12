package io.github.gear4jtest.core.extras.pipelinecache;

import java.util.Objects;

import io.github.gear4jtest.core.extras.history.fingerprint.ContextFingerprintStrategy;
import io.github.gear4jtest.core.extras.history.fingerprint.FingerprintContext;
import io.github.gear4jtest.core.extras.history.fingerprint.FingerprintStrategy;
import io.github.gear4jtest.core.model.ExecutionContext;

public final class PipelineCacheKeyFactory {

  private final FingerprintStrategy<Object> inputFingerprintStrategy;
  private final ContextFingerprintStrategy contextFingerprintStrategy;

  public PipelineCacheKeyFactory(
      FingerprintStrategy<Object> inputFingerprintStrategy,
      ContextFingerprintStrategy contextFingerprintStrategy) {
    this.inputFingerprintStrategy =
        Objects.requireNonNull(inputFingerprintStrategy, "inputFingerprintStrategy");
    this.contextFingerprintStrategy =
        Objects.requireNonNull(contextFingerprintStrategy, "contextFingerprintStrategy");
  }

  public PipelineCacheKey create(
      String pipelineId,
      String pipelineVersion,
      Object input,
      ExecutionContext executionContext) {

    FingerprintContext fingerprintContext = new FingerprintContext(pipelineId, pipelineVersion);

    byte[] inputFingerprint = inputFingerprintStrategy.fingerprint(input, fingerprintContext);
    byte[] contextFingerprint =
        contextFingerprintStrategy.fingerprint(executionContext, fingerprintContext);

    return new PipelineCacheKey(
        pipelineId,
        pipelineVersion,
        inputFingerprint,
        contextFingerprint);
  }
}
