package io.github.gear4jtest.core.spi.extension;

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
 */
public interface RuntimeExtension {
    /**
     * Returns the extension order.
     *
     * <p>
     * Lower values are applied first and are generally outermost when extensions
     * wrap runtime behavior. Typical examples: tracing/logging around order
     * {@code 0}, application concerns around {@code 50}, infrastructure/persistence
     * around {@code 100}.
     * </p>
     *
     * @return the extension ordering value
     */
    default int getOrder() {
        return 50;
    }
}
