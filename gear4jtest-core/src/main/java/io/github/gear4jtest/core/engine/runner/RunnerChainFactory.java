package io.github.gear4jtest.core.engine.runner;

import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.engine.ResolvedExtensions;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunnerChainFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunnerChainFactory.class);
    private final StrategyRegistry strategyRegistry;
    private final StationErrorPolicyExecutor stationErrorPolicyExecutor = new StationErrorPolicyExecutor();

    public RunnerChainFactory(StrategyRegistry strategyRegistry) {
        this.strategyRegistry = Objects.requireNonNull(strategyRegistry, "strategyRegistry must not be null");
    }

    public StationRunner createRootRunner(AssemblyLine<?, ?> pipeline,
                                          RunRequest request,
                                          ExecutionContext ctx,
                                          ResolvedExtensions extensions) {
        Objects.requireNonNull(request, "request must not be null");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating station runner chain. assemblyLineId={}, stationWrappers={}, stationLifecycles={}, runInterceptors={}, executorWrappers={}",
                         pipeline.getId(), extensions.stationWrappers().size(),
                         extensions.stationLifecycleExtensions().size(), extensions.runInterceptors().size(),
                         extensions.executorWrappers().size());
        }

        LateBoundStationRunner lateBoundRoot = new LateBoundStationRunner();
        StationRunner chain = new TerminalStationRunner(strategyRegistry, lateBoundRoot);

        var wrappers = extensions.stationWrappers();
        for (int i = wrappers.size() - 1; i >= 0; i--) {
            chain = wrappers.get(i).wrapStationRunner(chain, ctx);
        }

        chain = new StationExceptionBoundaryRunner(chain, stationErrorPolicyExecutor);
        chain = new StationLifecycleRunner(chain, extensions.stationLifecycleExtensions());

        StationRunner rootRunner = new ScopeInitializingRunner(chain);
        lateBoundRoot.bind(rootRunner);

        return rootRunner;
    }

    private static final class LateBoundStationRunner implements StationRunner {
        private StationRunner delegate;

        private void bind(StationRunner delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public StationLogTrace run(Object input, AbstractStation<?, ?> station, StationExecutionContext ctx) {
            if (delegate == null) {
                throw new IllegalStateException("Runner chain has not been fully initialized");
            }
            return delegate.run(input, station, ctx);
        }
    }
}
