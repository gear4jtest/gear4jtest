package io.github.gear4jtest.core.engine;

import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;

final class OptionalEventHandlingDefinition {
    private OptionalEventHandlingDefinition() {
    }

    static EventHandlingDefinition from(AssemblyLine<?, ?> pipeline) {
        return Optional.ofNullable(pipeline.getConfiguration())
                .map(AssemblyLine.Configuration::getEventHandlingDefinition)
                .orElse(null);
    }
}
