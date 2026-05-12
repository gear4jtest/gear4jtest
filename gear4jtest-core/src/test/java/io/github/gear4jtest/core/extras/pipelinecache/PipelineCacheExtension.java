package io.github.gear4jtest.core.extras.pipelinecache;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.extras.history.CacheTrackerPropagatingExecutor;
import io.github.gear4jtest.core.extras.history.CacheTrackerScope;
import io.github.gear4jtest.core.extras.history.DefaultExpirableDependencyTracker;
import io.github.gear4jtest.core.extras.history.ExpirableDependencyTracker;
import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineCacheExtension implements RunInterceptorExtension, ExecutorWrapperExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineCacheExtension.class);

    private final PipelineCachePolicy policy;
    private final PipelineCacheKeyFactory keyFactory;
    private final PipelineCacheRepository repository;

    public PipelineCacheExtension(PipelineCachePolicy policy,
                                  PipelineCacheKeyFactory keyFactory,
                                  PipelineCacheRepository repository) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public int getOrder() {
        return 60;
    }

    @Override
    public <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                                    RunRequest request,
                                                    ExecutionContext ctx,
                                                    RunChain<IN, OUT> chain) {

        if (!policy.enabled()) {
            return chain.proceed();
        }

        PipelineCacheKey key = keyFactory.create(pipeline.getId(), pipeline.getVersion(), request.getInput(), ctx);

        ctx.put(PipelineCacheRuntimeKeys.CACHE_KEY, key);

        Optional<PipelineCacheEntry<OUT>> cached = repository.findValid(key, Instant.now());
        if (cached.isPresent()) {
            OUT output = cached.get().output();

            ctx.put(PipelineCacheRuntimeKeys.CACHE_HIT, Boolean.TRUE);
            ctx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
            ctx.getPipelineExecution().setResult(output);

            LOGGER.debug("Pipeline cache hit. pipelineId={}, version={}, executionId={}", pipeline.getId(),
                         pipeline.getVersion(), ctx.getExecutionId());

            return ExecutionResult.success(output, ctx.getPipelineExecution());
        }

        ExpirableDependencyTracker tracker = new DefaultExpirableDependencyTracker();
        ctx.put(PipelineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER, tracker);

        try (CacheTrackerScope ignored = CacheTrackerScope.open(tracker)) {
            ExecutionResult<OUT> result = chain.proceed();

            if (result.isSuccess()) {
                saveIfEligible(result.getResult(), ctx, key, tracker);
            }

            return result;
        }
    }

    @Override
    public ExecutorService wrapExecutor(ExecutorService delegate, ExecutionContext ctx) {
        if (!policy.enabled()) {
            return delegate;
        }
        if (delegate instanceof CacheTrackerPropagatingExecutor) {
            return delegate;
        }
        return new CacheTrackerPropagatingExecutor(delegate);
    }

    private <OUT> void saveIfEligible(OUT output,
                                      ExecutionContext ctx,
                                      PipelineCacheKey key,
                                      ExpirableDependencyTracker tracker) {

        if (!tracker.isCacheable()) {
            LOGGER.debug("Pipeline cache skipped because some dependencies had no expiry. pipelineId={}, missingKeys={}",
                         ctx.getPipelineId(), tracker.getMissingExpiryKeys());
            return;
        }

        Instant now = Instant.now();
        Optional<Instant> expiresAtOpt = tracker.minExpiry();

        if (expiresAtOpt.isEmpty()) {
            if (policy.noDependencyCachePolicy() == NoDependencyCachePolicy.DO_NOT_CACHE) {
                LOGGER.debug("Pipeline cache skipped because no expirable dependency was recorded. pipelineId={}",
                             ctx.getPipelineId());
                return;
            }
            expiresAtOpt = Optional.of(now.plus(policy.defaultTtl()));
        }

        PipelineCacheEntry<OUT> entry = new PipelineCacheEntry<>(key, output, expiresAtOpt.get(), now);

        repository.save(entry);

        LOGGER.debug("Pipeline cache saved. pipelineId={}, executionId={}, expiresAt={}", ctx.getPipelineId(),
                     ctx.getExecutionId(), expiresAtOpt.get());
    }
}
