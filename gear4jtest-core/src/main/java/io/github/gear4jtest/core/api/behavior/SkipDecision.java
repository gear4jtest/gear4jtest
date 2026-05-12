package io.github.gear4jtest.core.api.behavior;

public final class SkipDecision {
    private static final SkipDecision DONT_SKIP = new SkipDecision(false, null);
    private final boolean skip;
    private final String reason;

    private SkipDecision(boolean skip, String reason) {
        this.skip = skip;
        this.reason = reason;
    }

    public static SkipDecision dontSkip() {
        return DONT_SKIP;
    }

    public static SkipDecision skip(String reason) {
        return new SkipDecision(true, reason);
    }

    public boolean shouldSkip() {
        return skip;
    }

    public String reason() {
        return reason;
    }
}
