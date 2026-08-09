package io.github.gear4jtest.experimental.cache.history.fingerprint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;

public final class WhitelistedContextFingerprintStrategy implements ContextFingerprintStrategy {
    private final List<String> keys;
    private final FingerprintStrategy<Object> delegate;

    public WhitelistedContextFingerprintStrategy(List<String> keys, FingerprintStrategy<Object> delegate) {
        this.keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        this.delegate = Objects.requireNonNull(delegate, "delegate");
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
