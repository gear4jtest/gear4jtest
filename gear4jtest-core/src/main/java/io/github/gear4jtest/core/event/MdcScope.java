package io.github.gear4jtest.core.event;

import java.util.Map;

import org.slf4j.MDC;

/**
 * Restores the caller MDC after running asynchronous Gear4J event reactions.
 */
final class MdcScope implements AutoCloseable {
    private final Map<String, String> previousContext;

    private MdcScope(Map<String, String> previousContext) {
        this.previousContext = previousContext;
    }

    static MdcScope install(Map<String, String> context) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
        return new MdcScope(previousContext);
    }

    @Override
    public void close() {
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }
}
