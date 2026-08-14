package io.github.gear4jtest.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.experimental.cache.history.fingerprint.JsonSha256FingerprintStrategy;

/**
 * Compilation-only check that the experimental cache publishes Jackson as an
 * API dependency because its public constructor exposes {@link ObjectMapper}.
 */
final class ExperimentalCacheScopeProbe {
    private final JsonSha256FingerprintStrategy<Object> strategy =
            new JsonSha256FingerprintStrategy<>(new ObjectMapper());
}
