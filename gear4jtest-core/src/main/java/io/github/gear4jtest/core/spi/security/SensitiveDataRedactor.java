package io.github.gear4jtest.core.spi.security;

/**
 * Redacts values before they are persisted or exposed to asynchronous event
 * reactions.
 *
 * <p>
 * Implementations must be thread-safe. The default policy is intentionally a
 * no-op for backwards compatibility; applications persisting sensitive payloads
 * should provide an explicit policy.
 * </p>
 */
@FunctionalInterface
public interface SensitiveDataRedactor {
    Object redact(RedactionTarget target, Object value);

    static SensitiveDataRedactor none() {
        return (target, value) -> value;
    }
}
