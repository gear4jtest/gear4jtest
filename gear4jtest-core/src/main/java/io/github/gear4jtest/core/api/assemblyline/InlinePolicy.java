package io.github.gear4jtest.core.api.assemblyline;

/**
 * Declares whether a pipeline may be executed as an inline sub-graph.
 */
public enum InlinePolicy {
    /**
     * Inline execution is never allowed. Use a nested run.
     */
    ALWAYS_FORBIDDEN,

    /**
     * Inline execution is allowed only for a pipeline without runtime
     * configuration.
     */
    ALLOWED_WHEN_CONFIGLESS,

    /**
     * Inline execution is allowed only if the parent runtime declares all required
     * capabilities.
     */
    ALLOWED_WHEN_REQUIREMENTS_SATISFIED;

    public boolean allowsInline() {
        return this != ALWAYS_FORBIDDEN;
    }
}
