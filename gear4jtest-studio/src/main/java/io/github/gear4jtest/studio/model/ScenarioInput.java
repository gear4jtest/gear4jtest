package io.github.gear4jtest.studio.model;

/**
 * Exact synthetic text or an authorized immutable fixture reference; never a
 * secret resource.
 */
public record ScenarioInput(Kind kind, String value, String fixtureVersion) {
    public enum Kind {
        SYNTHETIC_TEXT, FIXTURE_REFERENCE
    }

    public ScenarioInput {
        if (kind == null || value == null || value.length() > 8192
                || kind == Kind.SYNTHETIC_TEXT && fixtureVersion != null
                || kind == Kind.FIXTURE_REFERENCE && (!value.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
                        || fixtureVersion == null || !fixtureVersion.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
            throw new IllegalArgumentException("Invalid scenario input");
    }

    @Override
    public String toString() {
        return "ScenarioInput[kind=" + kind + "]";
    }
}
