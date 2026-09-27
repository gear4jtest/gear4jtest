package io.github.gear4jtest.studio.model;

import java.util.Objects;

public record CapturedValue(State state, String value) {
    public CapturedValue {
        Objects.requireNonNull(state);
        if (state == State.NOT_CAPTURED && value != null)
            throw new IllegalArgumentException("Invalid capture");
    }

    public enum State {
        NOT_CAPTURED, REDACTED, CAPTURED
    }

    public static CapturedValue none() {
        return new CapturedValue(State.NOT_CAPTURED, null);
    }
}
