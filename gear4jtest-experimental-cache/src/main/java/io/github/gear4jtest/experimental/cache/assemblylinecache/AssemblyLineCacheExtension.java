package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.spi.extension.ExecutorWrapperExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.experimental.cache.history.CacheTrackerPropagatingExecutor;
import io.github.gear4jtest.experimental.cache.history.CacheTrackerScope;
import io.github.gear4jtest.experimental.cache.history.DefaultExpirableDependencyTracker;
import io.github.gear4jtest.experimental.cache.history.ExpirableDependencyTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssemblyLineCacheExtension implements RunInterceptorExtension, ExecutorWrapperExtension {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssemblyLineCacheExtension.class);
    private final AssemblyLineCachePolicy policy;
    private final AssemblyLineCacheKeyFactory keyFactory;
    private final AssemblyLineCacheRepository repository;

    public AssemblyLineCacheExtension(AssemblyLineCachePolicy policy,
                                      AssemblyLineCacheKeyFactory keyFactory,
                                      AssemblyLineCacheRepository repository) {
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
                                                    RunRequest<IN> request,
                                                    ExecutionContext ctx,
                                                    RunChain<IN, OUT> chain) {

        if (!policy.enabled()) {
            return chain.proceed();
        }

        AssemblyLineCacheKey key = keyFactory.create(pipeline.getId(), pipeline.getVersion(), request.getInput(), ctx);

        ctx.put(AssemblyLineCacheRuntimeKeys.CACHE_KEY, key);

        Optional<AssemblyLineCacheEntry<OUT>> cached = repository.findValid(key, Instant.now());
        if (cached.isPresent()) {
            OUT output = cached.get().output();

            ctx.put(AssemblyLineCacheRuntimeKeys.CACHE_HIT, Boolean.TRUE);
            ctx.getAssemblyLineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
            ctx.getAssemblyLineExecution().setResult(output);

            LOGGER.debug("AssemblyLine cache hit. assemblyLineId={}, version={}, executionId={}", pipeline.getId(),
                         pipeline.getVersion(), ctx.getExecutionId());

            return ExecutionResult.success(output, ctx.getAssemblyLineExecution());
        }

        ExpirableDependencyTracker tracker = new DefaultExpirableDependencyTracker();
        ctx.put(AssemblyLineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER, tracker);

        long loadStartedAt = System.nanoTime();
        try (CacheTrackerScope ignored = CacheTrackerScope.open(tracker)) {
            ExecutionResult<OUT> result = chain.proceed();

            if (result.isSuccess()) {
                saveIfEligible(result.getResult(), ctx, key, tracker);
            }

            return result;
        } finally {
            if (repository instanceof AssemblyLineCacheMetrics metrics) {
                metrics.recordLoadDuration(Duration.ofNanos(System.nanoTime() - loadStartedAt));
            }
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
                                      AssemblyLineCacheKey key,
                                      ExpirableDependencyTracker tracker) {

        if (!tracker.isCacheable()) {
            LOGGER.debug("AssemblyLine cache skipped because some dependencies had no expiry. assemblyLineId={}, missingKeys={}",
                         ctx.getAssemblyLineId(), tracker.getMissingExpiryKeys());
            return;
        }

        Instant now = Instant.now();
        Optional<Instant> expiresAtOpt = tracker.minExpiry();

        if (expiresAtOpt.isEmpty()) {
            if (policy.noDependencyCachePolicy() == NoDependencyCachePolicy.DO_NOT_CACHE) {
                LOGGER.debug("AssemblyLine cache skipped because no expirable dependency was recorded. assemblyLineId={}",
                             ctx.getAssemblyLineId());
                return;
            }
            expiresAtOpt = Optional.of(now.plus(policy.defaultTtl()));
        }

        AssemblyLineCacheEntry<OUT> entry = new AssemblyLineCacheEntry<>(key, output, expiresAtOpt.get(), now);

        repository.save(entry);

        LOGGER.debug("AssemblyLine cache saved. assemblyLineId={}, executionId={}, expiresAt={}",
                     ctx.getAssemblyLineId(),
                     ctx.getExecutionId(), expiresAtOpt.get());
    }
}
