package io.github.gear4jtest.core.spi.security;

/**
 * Redacts values before they are persisted or exposed to asynchronous event
 * reactions.
 *
 * <p>
 * Implementations must be thread-safe. Persistence managers use
 * {@link #discardSensitiveValues()} unless an application explicitly supplies a
 * redactor. Use {@link #none()} only when unredacted capture is an intentional
 * trust decision.
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
     * Returns the safe metadata-only policy used by persistence managers by
     * default. Contexts are replaced by empty maps and all other sensitive values
     * are discarded.
     */
    static SensitiveDataRedactor discardSensitiveValues() {
        return DiscardSensitiveValues.INSTANCE;
    }

    /**
     * Returns whether the supplied redactor is Gear4J's built-in no-op redactor.
     */
    static boolean isNone(SensitiveDataRedactor redactor) {
        return redactor == null || redactor == Noop.INSTANCE;
    }

    /**
     * Returns whether the supplied redactor is the built-in metadata-only policy.
     */
    static boolean isDiscardingSensitiveValues(SensitiveDataRedactor redactor) {
        return redactor == DiscardSensitiveValues.INSTANCE;
    }

    enum Noop implements SensitiveDataRedactor {
        INSTANCE;

        @Override
        public Object redact(RedactionTarget target, Object value) {
            return value;
        }
    }

    enum DiscardSensitiveValues implements SensitiveDataRedactor {
        INSTANCE;

        @Override
        public Object redact(RedactionTarget target, Object value) {
            return switch (target) {
                case RUN_CONTEXT, STATION_CONTEXT -> java.util.Map.of();
                case RUN_INPUT, RUN_RESULT, RUN_ERROR_MESSAGE, STATION_ERROR_MESSAGE,
                        STATION_ERROR_HANDLER_MESSAGES, EVENT_INPUT, EVENT_OUTPUT ->
                    null;
            };
        }
    }
}
