package io.github.gear4jtest.spring;

import io.github.gear4jtest.core.engine.PipelineEngine;

/**
 * Spring customization hook applied just before the {@link PipelineEngine} is
 * built.
 *
 * <p>
 * This avoids replacing the whole base configuration when an application only
 * needs to tweak one or two builder options.
 */
@FunctionalInterface
public interface Gear4jPipelineEngineBuilderCustomizer {

    void customize(PipelineEngine.Builder builder);
}
