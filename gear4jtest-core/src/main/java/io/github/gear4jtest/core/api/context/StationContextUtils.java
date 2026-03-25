package io.github.gear4jtest.core.api.context;

import java.util.Optional;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import io.github.gear4jtest.core.api.station.StationKind;

public final class StationContextUtils {

    private StationContextUtils() {
        // utility class
    }

    public static boolean isProcessing(StationExecutionContext ctx) {
        return ctx.getKind() == StationKind.PROCESSING;
    }

    public static <T extends Operator<?, ?>> Optional<T> getRawTransformer(StationExecutionContext ctx) {
        return ctx.getCapability(Operator.class).map(raw -> (T) raw);
    }

    @SuppressWarnings("unchecked")
    public static <I, O> Optional<Operator<I, O>> getTypedTransformer(StationExecutionContext ctx) {
        return ctx.getCapability(Operator.class).map(raw -> (Operator<I, O>) raw);
    }

    public static Optional<WorkerParamsInjector.Parameters> getProcessingParameters(StationExecutionContext ctx) {
        return ctx.getCapability(WorkerParamsInjector.Parameters.class);
    }
}
