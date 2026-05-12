package io.github.gear4jtest.core.extras.history.fingerprint;

public interface FingerprintStrategy<T> {

    /**
     * Returns a stable and deterministic fingerprint that remains valid across JVM
     * restarts.
     */
    byte[] fingerprint(T value, FingerprintContext ctx);
}
