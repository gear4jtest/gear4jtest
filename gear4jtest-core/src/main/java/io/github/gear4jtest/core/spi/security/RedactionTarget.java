package io.github.gear4jtest.core.spi.security;

/** Location at which a value is about to leave the live execution runtime. */
public enum RedactionTarget {
    RUN_CONTEXT,
    RUN_INPUT,
    RUN_RESULT,
    RUN_ERROR_MESSAGE,
    STATION_CONTEXT,
    STATION_ERROR_MESSAGE,
    STATION_ERROR_HANDLER_MESSAGES,
    EVENT_INPUT,
    EVENT_OUTPUT
}
