package io.github.gear4jtest.core.engine;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.engine.support.ConcurrencyAwareTransformer;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerStatefulness;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineEngineWorkerConcurrencyPolicyTest {
    @Test
    void lockReusedWorkerInstanceOnly_shouldNotTrackNonReusedPrototypeWorkers() {
        // Given
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        PrototypeResourceFactory resourceFactory = new PrototypeResourceFactory();
        AssemblyLineEngine engine = engine(resourceFactory, manager,
                                           WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY);
        AssemblyLine<String, String> assemblyLine = AssemblyLines.<String>createAssemblyLine("prototype")
                .then(processingOperation("stateful", ExplicitStatefulOperator.class).build())
                .build();

        // When
        ExecutionResult<String> first = engine.execute(assemblyLine, RunRequest.builder().input("a").build());
        ExecutionResult<String> second = engine.execute(assemblyLine, RunRequest.builder().input("b").build());

        // Then
        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
        assertThat(resourceFactory.created()).isEqualTo(2);
        assertThat(manager.trackedWorkerCount())
                .as("non-reused prototype workers should not be registered for locking in this opt-in mode")
                .isZero();
    }

    @Test
    void lockReusedWorkerInstanceOnly_shouldStillTrackReusedWorkerInstances() {
        // Given
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        PrototypeResourceFactory resourceFactory = new PrototypeResourceFactory();
        AssemblyLineEngine engine = engine(resourceFactory, manager,
                                           WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY);
        AssemblyLine<String, String> assemblyLine = AssemblyLines.<String>createAssemblyLine("reused")
                .then(processingOperation("stateful", ExplicitStatefulOperator.class)
                        .reuseOperatorInstanceWithinRun()
                        .build())
                .build();

        // When
        ExecutionResult<String> result = engine.execute(assemblyLine, RunRequest.builder().input("a").build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(resourceFactory.created()).isEqualTo(1);
        assertThat(resourceFactory.latest()).isNotNull();
        assertThat(manager.trackedWorkerCount())
                .as("reused workers remain protected because they can be shared within the run")
                .isEqualTo(1);
    }

    private static AssemblyLineEngine engine(ResourceFactory resourceFactory,
                                             WorkerConcurrencyManager manager,
                                             WorkerConcurrencyPolicy policy) {
        return AssemblyLineEngine.builder()
                .resourceFactory(resourceFactory)
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry())
                .workerConcurrencyManager(manager)
                .workerConcurrencyPolicy(policy)
                .build();
    }

    private static final class PrototypeResourceFactory implements ResourceFactory {
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicReference<ExplicitStatefulOperator> latest = new AtomicReference<>();

        @Override
        public <T> T getResource(Class<T> clazz) {
            if (clazz == ExplicitStatefulOperator.class) {
                ExplicitStatefulOperator operator = new ExplicitStatefulOperator();
                created.incrementAndGet();
                latest.set(operator);
                return clazz.cast(operator);
            }
            throw new IllegalArgumentException("Unsupported resource type: " + clazz.getName());
        }

        private int created() {
            return created.get();
        }

        private ExplicitStatefulOperator latest() {
            return latest.get();
        }
    }

    public static final class ExplicitStatefulOperator
            implements Operator<String, String>, ConcurrencyAwareTransformer {
        @Override
        public WorkerStatefulness statefulness() {
            return WorkerStatefulness.STATEFUL;
        }

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "-done";
        }
    }
}
