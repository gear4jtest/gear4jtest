package io.github.gear4jtest.experimental.cache.history.fingerprint;

import java.security.MessageDigest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class JsonSha256FingerprintStrategy<T> implements FingerprintStrategy<T> {
    private final ObjectMapper mapper;

    public JsonSha256FingerprintStrategy() {
        this.mapper = new ObjectMapper();
        // canonicalize maps
        this.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public JsonSha256FingerprintStrategy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public byte[] fingerprint(T value, FingerprintContext ctx) {
        try {
            byte[] json = mapper.writeValueAsBytes(value);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(json);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute JSON SHA-256 fingerprint", e);
        }
    }
}
