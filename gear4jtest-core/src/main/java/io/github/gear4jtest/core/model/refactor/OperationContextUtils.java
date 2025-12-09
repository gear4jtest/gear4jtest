package io.github.gear4jtest.core.model.refactor;

import java.util.Optional;

public final class OperationContextUtils {

    private OperationContextUtils() {
        // utility class
    }

    public static boolean isProcessing(OperationExecutionContext ctx) {
        return ctx.getKind() == OperationKind.PROCESSING;
    }

    public static <T extends Transformer<?, ?>> Optional<T> getRawTransformer(OperationExecutionContext ctx) {
        return ctx.getCapability(Transformer.class).map(raw -> (T) raw);
    }

    @SuppressWarnings("unchecked")
    public static <I, O> Optional<Transformer<I, O>> getTypedTransformer(OperationExecutionContext ctx) {
        return ctx.getCapability(Transformer.class).map(raw -> (Transformer<I, O>) raw);
    }

    public static Optional<OperationParamsInjector.Parameters> getProcessingParameters(OperationExecutionContext ctx) {
        return ctx.getCapability(OperationParamsInjector.Parameters.class);
    }
}
