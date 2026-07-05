package io.github.gear4jtest.core.engine;

import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.engine.runner.SyntheticStationLifecycleRecorder;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.engine.support.ExecutorDecorator;
import io.github.gear4jtest.core.engine.support.TaskFactory;

final class AssemblyLineRunSupportFactory {
    private AssemblyLineRunSupportFactory() {
    }

    static ExecutionSupport create(ResolvedExtensions resolvedExtensions,
                                   TaskFactory taskFactory,
                                   PayloadCloner payloadCloner) {
        ExecutorDecorator decorator = (rawExec, context) -> {
            ExecutorService wrapped = rawExec;
            for (var wrapperExt : resolvedExtensions.executorWrappers()) {
                wrapped = wrapperExt.wrapExecutor(wrapped, context);
            }
            return wrapped;
        };
        return new ExecutionSupport(decorator, taskFactory, payloadCloner,
                new SyntheticStationLifecycleRecorder(resolvedExtensions.stationLifecycleExtensions()));
    }
}
