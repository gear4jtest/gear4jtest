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

final class PipelineRunLifecycleInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineRunLifecycleInvoker.class);

    void invokeRunStarted(List<RunLifecycleExtension> lifecycleExtensions,
                          ExecutionContext context,
                          AssemblyRunTrace execution) {
        for (RunLifecycleExtension lifecycleExtension : lifecycleExtensions) {
            invokeRunStartedSafely(lifecycleExtension, context, execution);
        }
    }

    Exception invokeRunCompleted(List<RunLifecycleExtension> lifecycleExtensions,
                                 ExecutionContext context,
                                 AssemblyRunTrace execution) {
        Exception firstCriticalFailure = null;
        for (RunLifecycleExtension lifecycleExtension : lifecycleExtensions) {
            Exception failure = invokeRunCompletedSafely(lifecycleExtension, context, execution);
            if (failure != null) {
                if (firstCriticalFailure == null) {
                    firstCriticalFailure = failure;
                }
                execution.setEndTime(Instant.now());
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setError(failure);
            }
        }
        return firstCriticalFailure;
    }

    private void invokeRunStartedSafely(RunLifecycleExtension lifecycleExtension,
                                        ExecutionContext context,
                                        AssemblyRunTrace execution) {
        try {
            lifecycleExtension.onRunStarted(context, execution);
        } catch (Exception e) {
            if (lifecycleExtension.failureMode() == LifecycleFailureMode.CRITICAL) {
                throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
            }

            LOGGER.error("A RunLifecycleExtension failed during onRunStarted. Ignoring. extension={}",
                         lifecycleExtension.getClass().getName(), e);
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
