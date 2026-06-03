package io.github.gear4jtest.core.extras.history.fingerprint;

import io.github.gear4jtest.core.api.context.ExecutionContext;

public interface ContextFingerprintStrategy {
    byte[] fingerprint(ExecutionContext ctx, FingerprintContext fctx);
}
