package io.github.gear4jtest.core.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationCancelledEvent;
import io.github.gear4jtest.core.event.StationInterruptedEvent;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationStartedEvent;
import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.github.gear4jtest.core.api.util.Events.eventHandling;
import static io.github.gear4jtest.core.api.util.RuntimeContracts.configuration;
import static io.github.gear4jtest.core.api.util.Stations.branch;
import static io.github.gear4jtest.core.api.util.Stations.container;
import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
class ParallelContainerSyntheticObservabilityTest {
    @Test
    void pendingParallelBranchInterruptedBySiblingFailure_shouldBePersistedAndEmitInterruptedEvent() {
        // Given
        FirstTaskOnlyExecutorService executor = new FirstTaskOnlyExecutorService();
        CopyOnWriteArrayList<StationInterruptedEvent> interruptedEvents = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<StationStartedEvent> startedEvents = new CopyOnWriteArrayList<>();
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("parallel-sibling-interruption-observability")
                .then(container("interruption-container", String.class, executor)
                        .withBranch(branch("failing", processingOperation("fast-fail", FastFailStep.class).build()))
                        .withBranch(branch("pending",
                                           processingOperation("pending-sibling", PendingStep.class).build()))
                        .returns(results -> Arrays.asList(results.get("failing", String.class),
                                                          results.get("pending", String.class))))
                .configuration(configuration().eventHandling(eventHandling()
                        .on(StationInterruptedEvent.class, interruptedEvents::add)
                        .on(StationStartedEvent.class, startedEvents::add)
                        .build()).build())
                .build();
        ResourceFactory resourceFactory = resourceFactory(new FastFailStep(), new PendingStep());
        InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);
        var request = RunRequest.builder().input("input").resourceFactory(resourceFactory)
                .with(new PersistenceExtension(InMemoryExecutionManager.builder()
                        .repository(repository)
                        .build()))
                .build();

        try {
            // When
            ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

            // Then
            assertThat(result).isNotNull();
            List<StationLogRecord> allLogs = repository.findAllLogsByRunId(result.getExecution().getId(),
                                                                           PageRequest.first(50));
            assertThat(allLogs.stream()
                    .map(log -> tuple(log.operationId(), log.status()))
                    .toList())
                    .contains(tuple("fast-fail", StationLogStatus.FAILED),
                              tuple("pending-sibling", StationLogStatus.CANCELLED));

            assertThat(interruptedEvents).hasSize(1);
            StationInterruptedEvent event = interruptedEvents.get(0);
            assertThat(event.getOperationId()).isEqualTo("pending-sibling");
            assertThat(event.getBranchId()).isEqualTo("pending");
            assertThat(event.getReason()).isEqualTo(StationInterruptionReason.SIBLING_FLOW_INTERRUPTED);
            assertThat(event.getInterruptingOperationId()).isEqualTo("fast-fail");
            assertThat(startedEvents.stream()
                    .map(StationStartedEvent::getOperationId)
                    .toList()).doesNotContain("pending-sibling");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timedOutParallelBranches_shouldBePersistedAndEmitCancelledEvents() {
        // Given
        NeverRunningExecutorService executor = new NeverRunningExecutorService();
        CopyOnWriteArrayList<StationCancelledEvent> cancelledEvents = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<StationStartedEvent> startedEvents = new CopyOnWriteArrayList<>();
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("parallel-timeout-observability")
                .then(container("timeout-container", String.class, executor)
                        .withBranch(branch("timeout-a", processingOperation("timeout-a", PendingStep.class).build()))
                        .withBranch(branch("timeout-b", processingOperation("timeout-b", PendingStep.class).build()))
                        .returns(results -> Arrays.asList(results.get("timeout-a", String.class),
                                                          results.get("timeout-b", String.class))))
                .configuration(configuration().eventHandling(eventHandling()
                        .on(StationCancelledEvent.class, cancelledEvents::add)
                        .on(StationStartedEvent.class, startedEvents::add)
                        .build()).build())
                .build();
        ResourceFactory resourceFactory = resourceFactory(new PendingStep());
        InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory,
                                                      ParallelExecutionConfiguration
                                                              .withDefaultAwaitTimeout(Duration.ofMillis(50)));
        var request = RunRequest.builder().input("input").resourceFactory(resourceFactory)
                .with(new PersistenceExtension(InMemoryExecutionManager.builder()
                        .repository(repository)
                        .build()))
                .build();

        try {
            // When
            ExecutionResult<List<String>> result = engine.execute(assemblyLine, request);

            // Then
            assertThat(result).isNotNull();
            List<StationLogRecord> allLogs = repository.findAllLogsByRunId(result.getExecution().getId(),
                                                                           PageRequest.first(50));
            assertThat(allLogs.stream()
                    .map(log -> tuple(log.operationId(), log.status()))
                    .toList())
                    .contains(tuple("timeout-a", StationLogStatus.CANCELLED),
                              tuple("timeout-b", StationLogStatus.CANCELLED));

            assertThat(cancelledEvents).hasSize(2);
            assertThat(cancelledEvents.stream()
                    .map(event -> tuple(event.getOperationId(), event.getBranchId(), event.getReason()))
                    .toList())
                    .contains(tuple("timeout-a", "timeout-a", StationCancellationReason.TIMEOUT),
                              tuple("timeout-b", "timeout-b", StationCancellationReason.TIMEOUT));
            assertThat(startedEvents.stream()
                    .map(StationStartedEvent::getOperationId)
                    .toList()).doesNotContain("timeout-a", "timeout-b");
        } finally {
            executor.shutdownNow();
        }
    }

    private static ResourceFactory resourceFactory(Object... operators) {
        Map<Class<?>, Object> resources = new HashMap<>();
        for (Object operator : operators) {
            resources.put(operator.getClass(), operator);
        }
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return clazz.cast(resources.get(clazz));
            }
        };
    }

    public static final class FastFailStep implements Operator<String, String> {
        @Override
        public String transform(String input,
                                StationExecutionContext operationExecution) {
            throw new IllegalStateException("fast failure");
        }
    }

    public static final class PendingStep implements Operator<String, String> {
        @Override
        public String transform(String input,
                                StationExecutionContext operationExecution) {
            throw new AssertionError("Pending branch should not have started");
        }
    }

    private static final class FirstTaskOnlyExecutorService extends AbstractExecutorService {
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final AtomicBoolean firstTaskAccepted = new AtomicBoolean();
        private final BlockingQueue<Runnable> queuedTasks = new LinkedBlockingQueue<>();
        private volatile Thread worker;

        @Override
        public void execute(Runnable command) {
            if (shutdown.get()) {
                throw new RejectedExecutionException("executor is shut down");
            }
            if (firstTaskAccepted.compareAndSet(false, true)) {
                worker = new Thread(command, "gear4j-test-first-task-only");
                worker.start();
                return;
            }
            queuedTasks.add(command);
        }

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            List<Runnable> queued = new ArrayList<>();
            queuedTasks.drainTo(queued);
            Thread runningWorker = worker;
            if (runningWorker != null) {
                runningWorker.interrupt();
            }
            return queued;
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            Thread runningWorker = worker;
            return shutdown.get() && (runningWorker == null || !runningWorker.isAlive());
        }

        @Override
        public boolean awaitTermination(long timeout,
                                        TimeUnit unit)
                throws InterruptedException {
            Thread runningWorker = worker;
            if (runningWorker != null) {
                runningWorker.join(unit.toMillis(timeout));
            }
            return isTerminated();
        }
    }

    private static final class NeverRunningExecutorService extends AbstractExecutorService {
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final BlockingQueue<Runnable> queuedTasks = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable command) {
            if (shutdown.get()) {
                throw new RejectedExecutionException("executor is shut down");
            }
            queuedTasks.add(command);
        }

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            List<Runnable> queued = new ArrayList<>();
            queuedTasks.drainTo(queued);
            return queued;
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return shutdown.get();
        }

        @Override
        public boolean awaitTermination(long timeout,
                                        TimeUnit unit) {
            return isTerminated();
        }
    }
}
