package io.github.gear4jtest.core.engine;

import java.time.Instant;
import java.util.List;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AssemblyLineRunLifecycleInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssemblyLineRunLifecycleInvoker.class);

    void invokeRunStarted(List<RunLifecycleExtension> lifecycleExtensions,
                          ExecutionContext context,
                          AssemblyRunTrace execution) {
        Exception firstCriticalFailure = null;
        for (int index = lifecycleExtensions.size() - 1; index >= 0; index--) {
            Exception failure = invokeRunStartedSafely(lifecycleExtensions.get(index), context, execution);
            if (firstCriticalFailure == null && failure != null) {
                firstCriticalFailure = failure;
            } else if (failure != null && failure != firstCriticalFailure) {
                firstCriticalFailure.addSuppressed(failure);
            }
        }
        if (firstCriticalFailure != null) {
            throw firstCriticalFailure instanceof RuntimeException runtimeException ? runtimeException
                    : new RuntimeException(firstCriticalFailure);
        }
    }

    Exception invokeRunCompleted(List<RunLifecycleExtension> lifecycleExtensions,
                                 ExecutionContext context,
                                 AssemblyRunTrace execution) {
        Exception firstCriticalFailure = null;
        Exception originalFailure = execution.getError();
        for (RunLifecycleExtension lifecycleExtension : lifecycleExtensions) {
            Exception failure;
            try {
                failure = invokeRunCompletedSafely(lifecycleExtension, context, execution);
            } catch (Error fatal) {
                Exception priorFailure = firstCriticalFailure != null ? firstCriticalFailure : originalFailure;
                if (priorFailure != null) {
                    fatal.addSuppressed(priorFailure);
                }
                throw fatal;
            }
            if (failure != null) {
                if (firstCriticalFailure == null) {
                    firstCriticalFailure = originalFailure != null ? originalFailure : failure;
                    execution.setEndTime(Instant.now());
                    execution.setStatus(ExecutionStatus.FAILED);
                    execution.setError(firstCriticalFailure);
                }
                if (failure != firstCriticalFailure) {
                    firstCriticalFailure.addSuppressed(failure);
                }
            }
        }
        return firstCriticalFailure;
    }

    private Exception invokeRunStartedSafely(RunLifecycleExtension lifecycleExtension,
                                             ExecutionContext context,
                                             AssemblyRunTrace execution) {
        try {
            lifecycleExtension.onRunStarted(context, execution);
            return null;
        } catch (Exception e) {
            if (lifecycleExtension.failureMode() == LifecycleFailureMode.CRITICAL) {
                LOGGER.error("A critical RunLifecycleExtension failed during onRunStarted. extension={}",
                             lifecycleExtension.getClass().getName(), e);
                return e;
            }

            LOGGER.error("A RunLifecycleExtension failed during onRunStarted. Ignoring. extension={}",
                         lifecycleExtension.getClass().getName(), e);
            return null;
        }
    }

    private Exception invokeRunCompletedSafely(RunLifecycleExtension lifecycleExtension,
                                               ExecutionContext context,
                                               AssemblyRunTrace execution) {
        try {
            lifecycleExtension.onRunCompleted(context, execution);
            return null;
        } catch (Exception e) {
            if (lifecycleExtension.failureMode() == LifecycleFailureMode.CRITICAL) {
                LOGGER.error("A critical RunLifecycleExtension failed during onRunCompleted. extension={}",
                             lifecycleExtension.getClass().getName(), e);
                return e;
            }

            LOGGER.error("A RunLifecycleExtension failed during onRunCompleted. Ignoring. extension={}",
                         lifecycleExtension.getClass().getName(), e);
            return null;
        }
    }
}
