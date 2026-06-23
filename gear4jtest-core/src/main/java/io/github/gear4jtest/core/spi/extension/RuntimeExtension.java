package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.assemblyline.RuntimeRequirement;

/**
 * Base contract for runtime extensions.
 *
 * <p>
 * Extensions are resolved once for a run and then dispatched to narrower SPI
 * contracts such as {@link RunInterceptorExtension},
 * {@link StationWrapperExtension}, {@link StationLifecycleExtension} or
 * {@link ExecutorWrapperExtension}. Keep extension implementations focused on
 * one concern.
 * </p>
 *
 * <p>
 * Inline pipeline validation is also expressed through this contract. Standard
 * Gear4J SPI types get their default behavior here so an extension can
 * implement several SPI interfaces without inheriting conflicting default
 * methods. Custom extension types can still override
 * {@link #requiresNestedRun()} or {@link #requiredInlineRequirement()}.
 * </p>
 */
public interface RuntimeExtension {
    /**
     * Returns the extension order.
     *
     * <p>
     * Lower values are applied first and are generally outermost when extensions
     * wrap runtime behavior. Typical examples: tracing/logging around order
     * {@code 0}, application concerns around {@code 50}, infrastructure/persistence
     * around {@code 100}. When two extensions return the same order, Gear4J uses
     * the implementation class name as a deterministic tie-breaker.
     * </p>
     *
     * @return the extension ordering value
     */
    default int getOrder() {
        return 50;
    }

    /**
     * Returns whether this extension needs an isolated nested assembly line run
     * when present on a runtime that may execute child assembly lines inline.
     */
    default boolean requiresNestedRun() {
        return this instanceof RunInterceptorExtension
                || this instanceof RunLifecycleExtension
                || this instanceof ExecutorWrapperExtension;
    }

    /**
     * Returns the runtime requirement that an inline-compatible child assembly line
     * must declare before this extension can participate in inline execution.
     */
    default RuntimeRequirement requiredInlineRequirement() {
        if (this instanceof StationWrapperExtension || this instanceof StationLifecycleExtension) {
            return RuntimeRequirement.stationExtension(getClass());
        }
        return null;
    }
}
