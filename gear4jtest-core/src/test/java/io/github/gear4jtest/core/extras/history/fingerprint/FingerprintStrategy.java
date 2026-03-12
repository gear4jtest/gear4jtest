package io.github.gear4jtest.core.extras.history.fingerprint;

public interface FingerprintStrategy<T> {

    /**
     * Empreinte stable et déterministe (compatible redémarrage JVM).
     */
    byte[] fingerprint(T value, FingerprintContext ctx);
}
