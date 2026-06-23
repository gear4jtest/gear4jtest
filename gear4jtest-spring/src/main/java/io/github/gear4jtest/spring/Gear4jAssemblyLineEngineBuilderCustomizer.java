package io.github.gear4jtest.spring;

import io.github.gear4jtest.core.engine.AssemblyLineEngine;

/**
 * Spring customization hook applied just before the {@link AssemblyLineEngine}
 * is built.
 *
 * <p>
 * This avoids replacing the whole base configuration when an application only
 * needs to tweak one or two builder options.
 * </p>
 */
@FunctionalInterface
public interface Gear4jAssemblyLineEngineBuilderCustomizer {
    void customize(AssemblyLineEngine.Builder builder);
}
