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

    /**
     * Returns the built-in no-op redactor. Values are kept as-is.
     */
    static SensitiveDataRedactor none() {
        return Noop.INSTANCE;
    }

    /**
     * Returns whether the supplied redactor is Gear4J's built-in no-op redactor.
     */
    static boolean isNone(SensitiveDataRedactor redactor) {
        return redactor == null || redactor == Noop.INSTANCE;
    }

    enum Noop implements SensitiveDataRedactor {
        INSTANCE;

        @Override
        public Object redact(RedactionTarget target, Object value) {
            return value;
        }
    }
}
