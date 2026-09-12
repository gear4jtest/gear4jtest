package io.github.gear4jtest.core.api.context;

import java.util.Optional;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.station.StationKind;
import org.jspecify.annotations.Nullable;

public final class StationContextUtils {
    private StationContextUtils() {
        // utility class
    }

    public static boolean isProcessing(StationExecutionContext ctx) {
        return ctx.getKind() == StationKind.PROCESSING;
    }

    /**
     * Returns the {@link Operator} instance currently bound to the station
     * execution context, if any.
     *
     * <p>
     * The returned operator is exposed with wildcards on purpose: the runtime model
     * does not preserve {@code <IN, OUT>} on the boundary between {@link Operator}
     * instances and the runner chain, so any cast to specific type variables must
     * be confined to a single, deliberate place. Use
     * {@link #applyTransformer(Object, StationExecutionContext)} when you actually
     * need to invoke the transformer with an {@code Object} input.
     * </p>
     */
    public static Optional<Operator<?, ?>> getTransformer(StationExecutionContext ctx) {
        return ctx.getCapability(Operator.class).map(raw -> (Operator<?, ?>) raw);
    }

    /**
     * Invokes the bound {@link Operator} on {@code input}, allowing a successful
     * {@code null} result (including {@code Void} operators).
     *
     * <p>
     * Captures the operator's {@code <IN, OUT>} type variables locally so the
     * necessary unchecked cast on {@code input} stays in this single helper instead
     * of leaking into every caller.
     * </p>
     *
     * @return the operator result, possibly {@code null}
     * @throws IllegalStateException when no operator is bound
     */
    public static @Nullable Object applyTransformer(Object input, StationExecutionContext ctx) {
        Operator<?, ?> operator = getTransformer(ctx)
                .orElseThrow(() -> new IllegalStateException("No operator bound to station execution context"));
        return invokeTransformer(operator, input, ctx);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> O invokeTransformer(Operator<I, O> op, Object input, StationExecutionContext ctx) {
        return op.transform((I) input, ctx);
    }

    public static Optional<StationParameters> getProcessingParameters(StationExecutionContext ctx) {
        return ctx.getCapability(StationParameters.class);
    }
}
