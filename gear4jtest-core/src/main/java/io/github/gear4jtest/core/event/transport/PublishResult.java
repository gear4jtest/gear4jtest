package io.github.gear4jtest.core.event.transport;

/**
 * Result of an external transport publication attempt.
 */
public record PublishResult(boolean accepted, String transportMessageId, String detail) {

    public static PublishResult accepted(String transportMessageId) {
        return new PublishResult(true, transportMessageId, null);
    }

    public static PublishResult rejected(String detail) {
        return new PublishResult(false, null, detail);
    }
}
