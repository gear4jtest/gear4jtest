package io.github.gear4jtest.core.extras.history.fingerprint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.model.ExecutionContext;

public final class WhitelistedContextFingerprintStrategy implements ContextFingerprintStrategy {

    private final List<String> keys;
    private final FingerprintStrategy<Object> delegate;

    public WhitelistedContextFingerprintStrategy(List<String> keys, FingerprintStrategy<Object> delegate) {
        this.keys = keys;
        this.delegate = delegate;
    }

    @Override
    public byte[] fingerprint(ExecutionContext ctx, FingerprintContext fctx) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String key : keys) {
            filtered.put(key, ctx.getContext().get(key));
        }
        return delegate.fingerprint(filtered, fctx);
    }
}
