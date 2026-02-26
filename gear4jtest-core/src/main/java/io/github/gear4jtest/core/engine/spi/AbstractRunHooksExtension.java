package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.engine.core.RunRequest;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.ExecutionResult;

/**
 * Base class optionnelle pour écrire des extensions façon "hooks" (onStart/onResult/onException/onEnd)
 * tout en restant compatible avec le modèle middleware.
 */
public abstract class AbstractRunHooksExtension implements RunInterceptorExtension {

    @Override
    public final <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                                          RunRequest request,
                                                          ExecutionContext ctx,
                                                          RunChain<IN, OUT> chain) {
        onStart(pipeline, request, ctx);

        try {
            ExecutionResult<OUT> result = chain.proceed();
            onResult(pipeline, request, ctx, result);
            return result;
        } catch (RuntimeException e) {
            onException(pipeline, request, ctx, e);
            throw e;
        } finally {
            onEnd(pipeline, request, ctx);
        }
    }

    protected void onStart(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx) {
    }

    /**
     * Appelé même si le résultat représente un échec fonctionnel (ExecutionResult.failure)
     */
    protected void onResult(
            AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx, ExecutionResult<?> result) {
    }

    /**
     * Appelé uniquement sur exception non gérée (crash).
     */
    protected void onException(
            AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx, RuntimeException error) {
    }

    protected void onEnd(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx) {
    }
}