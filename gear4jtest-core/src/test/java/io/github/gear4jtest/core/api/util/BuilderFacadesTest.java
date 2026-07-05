package io.github.gear4jtest.core.api.util;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineExecutionMode;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuilderFacadesTest {
    @Test
    void errorBuilders_shouldCreateTypedSignalErrorDefinitions() {
        assertThat(Errors.<String>ignore(IllegalArgumentException.class).build().getSignalType())
                .isEqualTo(SignalType.IGNORE);
        assertThat(Errors.<String>fatal(IllegalArgumentException.class).build().getSignalType())
                .isEqualTo(SignalType.FATAL);
        assertThat(Errors.<String>stop(IllegalArgumentException.class).build().getSignalType())
                .isEqualTo(SignalType.STOP);
    }

    @Test
    void stationBuilders_shouldExposeCommonStationFactories() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        WorkStation<String, String> branch = Stations
                .<String, String, IdentityOperator>processingOperation("identity", IdentityOperator.class)
                .build();
        try {
            // When / Then
            assertThat(Stations.<String, String, IdentityOperator>processingOperation("work", IdentityOperator.class)
                    .build().getId()).isEqualTo("work");
            assertThat(Stations.<String, IdentityOperator>unaryProcessingOperation("unary", IdentityOperator.class)
                    .build().isUnary()).isTrue();
            assertThat(Stations.iterate("items").accumulator(Stations.toList()).build()
                    .getId()).isEqualTo("items");
            var named = Stations.branch("named", branch);
            assertThat(Stations.container("facade-container", String.class).withBranch(named)
                    .returns(results -> results.get(named)).getAssemblyLines()).hasSize(1);
            var parallel = Stations.branch("parallel", branch);
            assertThat(Stations.container("facade-parallel-container", String.class, executor).withBranch(parallel)
                    .returns(results -> results.get(parallel)).getExecutorService()).isSameAs(executor);
            assertThat(Stations.ifElseContainer("facade-if-else-container", String.class).build().getAssemblyLines())
                    .isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void signalAndAssemblyLineFactories_shouldBuildExpectedDefinitions() {
        // Given
        WorkStation<String, String> station = Stations
                .<String, String, IdentityOperator>processingOperation("identity", IdentityOperator.class)
                .build();
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                .then(station)
                .build();

        // When
        SignalStation<String> fatal = Stations.fatalSignal(String.class).id("fatal").build();
        SignalStation<Map<String, Integer>> mapFatal = Stations
                .fatalSignal(new Stations.MapType<>(String.class, Integer.class))
                .id("map-fatal")
                .build();

        // Then
        assertThat(fatal.getSignalType()).isEqualTo(SignalType.FATAL);
        assertThat(mapFatal.getSignalType()).isEqualTo(SignalType.FATAL);
        assertThat(AssemblyLines.inlineAssemblyLine("inline", child).getExecutionMode())
                .isEqualTo(AssemblyLineExecutionMode.INLINE);
        assertThat(AssemblyLines.nestedAssemblyLine("nested", child).getExecutionMode())
                .isEqualTo(AssemblyLineExecutionMode.NESTED_RUN);
        assertThat(AssemblyLines.<String, String>assemblyLineCall("call").directTarget(child).build().getId())
                .isEqualTo("call");
        assertThat(AssemblyLines.chain("chain", station).build().getId()).isEqualTo("chain");
    }

    @Test
    void configurationFactories_shouldCreateMutableBuildersAndValidateNullTypeTokens() {
        assertThat(RuntimeContracts.configuration()
                .persistence(PersistenceConfiguration.builder().storeResultObject(false).build())
                .build()
                .getPersistence()
                .isStoreResultObject()).isFalse();
        assertThat(Persistence.persistenceConfiguration().storeResultObject(false).build()
                .isStoreResultObject()).isFalse();
        assertThat(Persistence.persistenceExtension(new NoOpRunManager()).terminalRecordBatchSize(1).build())
                .isNotNull();
        assertThat(Events.eventConfiguration().build()).isNotNull();
        assertThat(Events.eventHandling().build()).isNotNull();
        assertThat(Stations.toList()).isNotNull();
        assertThat(Stations.toSet()).isNotNull();
        assertThatThrownBy(() -> Stations.fatalSignal((Class<String>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz must not be null");
        assertThatThrownBy(() -> Stations.container((Class<String>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz must not be null");
        assertThatThrownBy(() -> Stations.ifElseContainer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz must not be null");
    }

    @Test
    void concurrencyFactories_shouldExposeNamedOperationalDefaults() {
        assertThat(Concurrency.parallelExecutionDefaults()).isNotNull();
        assertThat(Concurrency.processWideWorkerLocks().concurrencyPolicy())
                .isEqualTo(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE);
        assertThat(Concurrency.engineLocalWorkerLocks().concurrencyPolicy())
                .isEqualTo(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
        assertThat(Concurrency.reusedWorkerLocksOnly().concurrencyPolicy())
                .isEqualTo(WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY);
        assertThat(Concurrency.allowParallelWorkerInvocations().concurrencyPolicy())
                .isEqualTo(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS);
        assertThat(Concurrency.failFastWorkerLocks().lockAcquisitionPolicy())
                .isEqualTo(WorkerLockAcquisitionPolicy.FAIL_FAST);
    }

    private static final class NoOpRunManager implements AssemblyRunManager {
        @Override
        public void start(AssemblyRunTrace execution) {
            // no-op
        }

        @Override
        public void end(AssemblyRunTrace finalExecution) {
            // no-op
        }
    }

    private static final class IdentityOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext ctx) {
            return input;
        }
    }
}
