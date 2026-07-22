package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.PersistenceOperationalStatus;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeStats;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseExecutionManagerTest {
    @Test
    void defaultRedactionPolicy_shouldPersistMetadataWithoutSensitiveValues() {
        // Given
        String secret = "fixture-secret-must-not-leak";
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();
        AssemblyRunTrace trace = new AssemblyRunTrace(UUID.randomUUID(), "line", Map.of("secret", secret));
        trace.setContext(Map.of("secret", secret));
        trace.setResult(secret);
        trace.setErrorMessage(secret);

        try {
            // When
            manager.start(trace);
            manager.end(trace);

            // Then
            ArgumentCaptor<AssemblyRunRecord> started = ArgumentCaptor.forClass(AssemblyRunRecord.class);
            ArgumentCaptor<AssemblyRunRecord> completed = ArgumentCaptor.forClass(AssemblyRunRecord.class);
            verify(repository).save(started.capture());
            verify(repository).update(completed.capture());
            assertMetadataOnly(started.getValue(), secret);
            assertMetadataOnly(completed.getValue(), secret);
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void builder_shouldNotInitializeSchemaUnlessExplicitlyEnabled() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();

        try {
            // Then
            verify(repository, never()).initialize();
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void builder_shouldInitializeSchemaWhenExplicitlyEnabled() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .autoCreateTables(true)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();

        try {
            // Then
            verify(repository).initialize();
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void append_shouldRejectRecordsWhenPerRunBufferIsFull() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch releaseFlushExecutor = new CountDownLatch(1);
        flushExecutor.submit(() -> {
            try {
                releaseFlushExecutor.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(2)
                .maxPendingLogsPerRun(2).flushInterval(Duration.ofDays(1)).build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            // When
            manager.append(stationRecord(runId));
            manager.append(stationRecord(runId));

            StationLogRecord rejectedRecord = stationRecord(runId);

            // Then
            assertThatThrownBy(() -> manager.append(rejectedRecord))
                    .isInstanceOf(ExecutionPersistenceException.class)
                    .hasMessageContaining("buffer is full");
            assertThat(manager.snapshotStats().rejectedAppends()).isEqualTo(1L);
        } finally {
            releaseFlushExecutor.countDown();
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void periodicFlush_shouldPersistRecordsBelowBatchThreshold() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch persisted = new CountDownLatch(1);
        doAnswer(invocation -> {
            persisted.countDown();
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(10)
                .maxPendingLogsPerRun(10).flushInterval(Duration.ofMillis(10)).build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);

        try {
            // When
            manager.append(stationRecord(UUID.randomUUID()));

            // Then
            assertThat(persisted.await(2, TimeUnit.SECONDS)).isTrue();
            awaitCompletedFlushes(manager, 1L);
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void start_shouldAllowIndependentJdbcWritesToProgressConcurrently() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch bothWritesStarted = new CountDownLatch(2);
        CountDownLatch releaseWrites = new CountDownLatch(1);
        AtomicInteger activeWrites = new AtomicInteger();
        AtomicInteger maxConcurrentWrites = new AtomicInteger();
        doAnswer(invocation -> {
            int concurrentWrites = activeWrites.incrementAndGet();
            maxConcurrentWrites.accumulateAndGet(concurrentWrites, Math::max);
            bothWritesStarted.countDown();
            try {
                releaseWrites.await();
            } finally {
                activeWrites.decrementAndGet();
            }
            return null;
        }).when(repository).save(any());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService operationExecutor = Executors.newFixedThreadPool(2);
        DatabaseExecutionManager manager = manager(repository, PersistenceRuntimeConfiguration.defaults(),
                                                   flushExecutor, scheduler);
        AssemblyRunTrace firstTrace = new AssemblyRunTrace(UUID.randomUUID(), "first", Map.of());
        AssemblyRunTrace secondTrace = new AssemblyRunTrace(UUID.randomUUID(), "second", Map.of());

        try {
            // When
            Future<?> firstStart = operationExecutor.submit(() -> manager.start(firstTrace));
            Future<?> secondStart = operationExecutor.submit(() -> manager.start(secondTrace));

            // Then
            assertThat(bothWritesStarted.await(2, TimeUnit.SECONDS))
                    .as("independent JDBC writes must not be serialized by a manager-wide lifecycle lock")
                    .isTrue();
            releaseWrites.countDown();
            firstStart.get(2, TimeUnit.SECONDS);
            secondStart.get(2, TimeUnit.SECONDS);
            assertThat(maxConcurrentWrites).hasValue(2);
        } finally {
            releaseWrites.countDown();
            manager.shutdown(Duration.ofSeconds(1));
            operationExecutor.shutdownNow();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldCloseAdmissionBeforeWaitingForAnInFlightJdbcWrite() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            writeStarted.countDown();
            releaseWrite.await();
            return null;
        }).when(repository).save(any());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService operationExecutor = Executors.newSingleThreadExecutor();
        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
        DatabaseExecutionManager manager = manager(repository, PersistenceRuntimeConfiguration.defaults(),
                                                   flushExecutor, scheduler);
        AssemblyRunTrace admittedTrace = new AssemblyRunTrace(UUID.randomUUID(), "admitted", Map.of());
        AssemblyRunTrace rejectedTrace = new AssemblyRunTrace(UUID.randomUUID(), "rejected", Map.of());

        try {
            Future<?> admittedStart = operationExecutor.submit(() -> manager.start(admittedTrace));
            assertThat(writeStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            Future<PersistenceShutdownReport> shutdown = shutdownExecutor.submit(
                                                                                 () -> manager
                                                                                         .shutdownWithReport(Duration
                                                                                                 .ofSeconds(1)));
            awaitShutdownAdmissionClosure(manager);

            // Then
            assertThat(shutdown.isDone())
                    .as("shutdown must wait for the JDBC operation admitted before closure")
                    .isFalse();
            assertThatThrownBy(() -> manager.start(rejectedTrace))
                    .isInstanceOf(ExecutionPersistenceException.class)
                    .hasMessage("DatabaseExecutionManager is already shut down");

            releaseWrite.countDown();
            admittedStart.get(2, TimeUnit.SECONDS);
            PersistenceShutdownReport report = shutdown.get(2, TimeUnit.SECONDS);
            assertThat(report.successful()).isTrue();
            assertThat(report.initialActiveRuns()).isEqualTo(1);
            assertThat(report.remainingActiveRuns()).isZero();
            verify(repository).save(any());
        } finally {
            releaseWrite.countDown();
            operationExecutor.shutdownNow();
            shutdownExecutor.shutdownNow();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldRespectDeadlineWhileAnAdmittedJdbcWriteIsStillRunning() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            writeStarted.countDown();
            releaseWrite.await();
            return null;
        }).when(repository).save(any());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService operationExecutor = Executors.newSingleThreadExecutor();
        DatabaseExecutionManager manager = manager(repository, PersistenceRuntimeConfiguration.defaults(),
                                                   flushExecutor, scheduler);
        Future<?> admittedStart = operationExecutor.submit(
                                                           () -> manager.start(new AssemblyRunTrace(UUID.randomUUID(),
                                                                   "admitted", Map.of())));

        try {
            assertThat(writeStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            long startedNanos = System.nanoTime();
            PersistenceShutdownReport report = manager.shutdownWithReport(Duration.ofMillis(75));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

            // Then
            assertThat(elapsedMillis).isLessThan(500L);
            assertThat(report.deadlineReached()).isTrue();
            assertThat(report.unfinishedOperations()).isEqualTo(1);
            assertThat(report.successful()).isFalse();
            assertThat(manager.isAlive()).isFalse();
        } finally {
            releaseWrite.countDown();
            admittedStart.get(2, TimeUnit.SECONDS);
            operationExecutor.shutdownNow();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldReturnWhenShutdownJdbcIgnoresInterruption() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            writeStarted.countDown();
            boolean interrupted = false;
            while (releaseWrite.getCount() > 0) {
                try {
                    releaseWrite.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .batchSize(10)
                .maxPendingLogsPerRun(10)
                .flushInterval(Duration.ofDays(1))
                .build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();
        manager.append(stationRecord(runId));

        try {
            // When
            long startedNanos = System.nanoTime();
            Future<PersistenceShutdownReport> shutdown = shutdownExecutor.submit(
                                                                                 () -> manager
                                                                                         .shutdownWithReport(Duration
                                                                                                 .ofMillis(100)));
            assertThat(writeStarted.await(2, TimeUnit.SECONDS)).isTrue();
            PersistenceShutdownReport report = shutdown.get(1, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

            // Then
            assertThat(elapsedMillis).isLessThan(750L);
            assertThat(report.deadlineReached()).isTrue();
            assertThat(report.unfinishedOperations()).isZero();
            assertThat(report.remainingStationLogs()).isEqualTo(1);
            assertThat(report.flushExecutorTerminated()).isFalse();
            assertThat(report.failures()).hasSize(1);
            assertThat(report.failures().get(0).message()).contains("deadline reached");
        } finally {
            releaseWrite.countDown();
            shutdownExecutor.shutdownNow();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldRespectDeadlineWhileAnAsyncFlushHoldsTheBufferLock() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            writeStarted.countDown();
            boolean interrupted = false;
            while (releaseWrite.getCount() > 0) {
                try {
                    releaseWrite.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .batchSize(1)
                .maxPendingLogsPerRun(10)
                .flushInterval(Duration.ofDays(1))
                .build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            manager.append(stationRecord(runId));
            assertThat(writeStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            long startedNanos = System.nanoTime();
            PersistenceShutdownReport report = manager.shutdownWithReport(Duration.ofMillis(100));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

            // Then
            assertThat(elapsedMillis).isLessThan(750L);
            assertThat(report.deadlineReached()).isTrue();
            assertThat(report.remainingStationLogs()).isEqualTo(1);
            assertThat(report.failures()).hasSize(1);
            assertThat(report.failures().get(0).message()).contains("in-flight buffer flush");
        } finally {
            releaseWrite.countDown();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void failedAsyncFlush_shouldRestoreRecordsAndReadinessAfterSuccessfulRetry() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch failedOnce = new CountDownLatch(1);
        CountDownLatch succeededOnce = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                failedOnce.countDown();
                throw new ExecutionPersistenceException("temporary database outage");
            }
            succeededOnce.countDown();
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        when(repository.checkConnectivity(any(Duration.class))).thenReturn(true);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(2)
                .maxPendingLogsPerRun(10).flushInterval(Duration.ofDays(1)).build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            // When
            manager.append(stationRecord(runId));
            manager.append(stationRecord(runId));

            // Then
            assertThat(failedOnce.await(2, TimeUnit.SECONDS)).as("first asynchronous flush should fail").isTrue();
            awaitStats(manager, 1L, 2);
            PersistenceOperationalStatus pending = manager.probeHealth();
            assertThat(pending.ready()).isFalse();
            assertThat(pending.reason()).isEqualTo(PersistenceOperationalStatus.Reason.RECOVERY_PENDING);

            // When
            manager.flush(runId);

            // Then
            assertThat(succeededOnce.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(manager.snapshotStats().bufferedStationLogs()).isZero();
            PersistenceOperationalStatus recovered = manager.probeHealth();
            assertThat(recovered.ready()).isTrue();
            assertThat(recovered.recoveredAfterFailure()).isTrue();
            assertThat(recovered.reason()).isEqualTo(PersistenceOperationalStatus.Reason.READY);
            verify(repository, atLeast(2)).saveOperationRecordsBatch(anyList());
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void end_shouldKeepRunBufferWhenFinalUpdateFailsSoCallerCanRetry() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new ExecutionPersistenceException("database update failed"))
                .doNothing()
                .when(repository).update(any());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = manager(repository, PersistenceRuntimeConfiguration.defaults(),
                                                   flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();
        AssemblyRunTrace trace = new AssemblyRunTrace(runId, "assembly-line", Map.of());
        trace.markSuccess("ok");

        try {
            manager.append(stationRecord(runId));

            // When / Then
            assertThatThrownBy(() -> manager.end(trace))
                    .isInstanceOf(ExecutionPersistenceException.class)
                    .hasMessageContaining("database update failed");
            assertThat(manager.snapshotStats().activeRuns()).as("failed final update must keep retry state")
                    .isEqualTo(1);

            // When
            manager.end(trace);

            // Then
            assertThat(manager.snapshotStats().activeRuns()).isZero();
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldExposeAnUnresolvedRunFinalizationFailure() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new ExecutionPersistenceException("final run update unavailable"))
                .when(repository).update(any());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = manager(repository, PersistenceRuntimeConfiguration.defaults(),
                                                   flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();
        AssemblyRunTrace trace = new AssemblyRunTrace(runId, "assembly-line", Map.of());
        trace.markSuccess("ok");

        try {
            assertThatThrownBy(() -> manager.end(trace))
                    .isInstanceOf(ExecutionPersistenceException.class)
                    .hasMessageContaining("final run update unavailable");

            // When
            PersistenceShutdownReport report = manager.shutdownWithReport(Duration.ofSeconds(1));

            // Then
            assertThat(report.successful()).isFalse();
            assertThat(report.deadlineReached()).isFalse();
            assertThat(report.initialActiveRuns()).isEqualTo(1);
            assertThat(report.remainingActiveRuns()).isEqualTo(1);
            assertThat(report.remainingStationLogs()).isZero();
            assertThat(report.failures()).singleElement().satisfies(failure -> {
                assertThat(failure.runId()).isEqualTo(runId);
                assertThat(failure.attempts()).isZero();
                assertThat(failure.remainingStationLogs()).isZero();
                assertThat(failure.message()).contains("final run update unavailable");
            });
        } finally {
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldKeepFirstJdbcFailureWhenRetryReachesDeadline() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch retryStarted = new CountDownLatch(1);
        CountDownLatch releaseRetry = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new ExecutionPersistenceException("database unavailable");
            }
            retryStarted.countDown();
            boolean interrupted = false;
            while (releaseRetry.getCount() > 0) {
                try {
                    releaseRetry.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(10)
                .maxPendingLogsPerRun(10)
                .flushInterval(Duration.ofDays(1))
                .shutdownRetryInitialBackoff(Duration.ofMillis(5))
                .shutdownRetryMaxBackoff(Duration.ofMillis(10))
                .build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            manager.append(stationRecord(runId));

            // When
            Future<PersistenceShutdownReport> shutdown = shutdownExecutor.submit(
                                                                                 () -> manager
                                                                                         .shutdownWithReport(Duration
                                                                                                 .ofMillis(100)));
            assertThat(retryStarted.await(2, TimeUnit.SECONDS)).as("the retry should reach the JDBC repository")
                    .isTrue();
            PersistenceShutdownReport report = shutdown.get(1, TimeUnit.SECONDS);

            // Then
            PersistenceRuntimeStats stats = manager.snapshotStats();
            assertThat(report.successful()).isFalse();
            assertThat(report.deadlineReached()).isTrue();
            assertThat(report.initialBufferedStationLogs()).isEqualTo(1);
            assertThat(report.flushedStationLogs()).isZero();
            assertThat(report.remainingStationLogs()).isEqualTo(1);
            assertThat(report.flushAttempts()).isEqualTo(2);
            assertThat(attempts).hasValue(2);
            assertThat(report.failures()).singleElement().satisfies(failure -> {
                assertThat(failure.runId()).isEqualTo(runId);
                assertThat(failure.remainingStationLogs()).isEqualTo(1);
                assertThat(failure.attempts()).isEqualTo(report.flushAttempts());
                assertThat(failure.message()).contains("database unavailable");
            });
            assertThat(manager.lastShutdownReport()).contains(report);
            assertThat(stats.activeRuns()).as("a failed shutdown flush must keep the run buffer for diagnostics")
                    .isEqualTo(1);
            assertThat(stats.bufferedStationLogs())
                    .as("drained records must be restored when shutdown persistence fails")
                    .isEqualTo(1);
            assertThat(stats.failedFlushes()).as("every shutdown flush failure must be visible in runtime stats")
                    .isEqualTo((long) report.flushAttempts());
        } finally {
            releaseRetry.countDown();
            shutdownExecutor.shutdownNow();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldRetryTransientFailureAndDrainBeforeDeadline() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        doThrow(new ExecutionPersistenceException("temporary database outage"))
                .doNothing()
                .when(repository)
                .saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(10)
                .maxPendingLogsPerRun(10)
                .flushInterval(Duration.ofDays(1))
                .shutdownRetryInitialBackoff(Duration.ofMillis(1))
                .shutdownRetryMaxBackoff(Duration.ofMillis(5))
                .build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);
        UUID runId = UUID.randomUUID();

        try {
            manager.append(stationRecord(runId));

            // When
            PersistenceShutdownReport report = manager.shutdownWithReport(Duration.ofSeconds(1));

            // Then
            assertThat(report.successful()).isTrue();
            assertThat(report.flushAttempts()).isEqualTo(2);
            assertThat(report.flushedStationLogs()).isEqualTo(1);
            assertThat(report.remainingStationLogs()).isZero();
            assertThat(report.failures()).isEmpty();
            assertThat(manager.shutdownWithReport(Duration.ofSeconds(1))).isSameAs(report);
            verify(repository, times(2)).saveOperationRecordsBatch(anyList());
        } finally {
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldWaitForInFlightFlushAndRecoverItsRestoredRecords() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstFlushStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstFlush = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                firstFlushStarted.countDown();
                releaseFirstFlush.await();
                throw new ExecutionPersistenceException("in-flight database outage");
            }
            return null;
        }).when(repository).saveOperationRecordsBatch(anyList());
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder().batchSize(1)
                .maxPendingLogsPerRun(10)
                .flushInterval(Duration.ofDays(1))
                .shutdownRetryInitialBackoff(Duration.ofMillis(1))
                .shutdownRetryMaxBackoff(Duration.ofMillis(5))
                .build();
        DatabaseExecutionManager manager = manager(repository, configuration, flushExecutor, scheduler);

        try {
            manager.append(stationRecord(UUID.randomUUID()));
            assertThat(firstFlushStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            Future<PersistenceShutdownReport> shutdown = shutdownExecutor.submit(
                                                                                 () -> manager
                                                                                         .shutdownWithReport(Duration
                                                                                                 .ofSeconds(1)));
            releaseFirstFlush.countDown();
            PersistenceShutdownReport report = shutdown.get(2, TimeUnit.SECONDS);

            // Then
            assertThat(report.successful()).isTrue();
            assertThat(report.initialBufferedStationLogs()).isEqualTo(1);
            assertThat(report.flushedStationLogs()).isEqualTo(1);
            assertThat(report.remainingStationLogs()).isZero();
            assertThat(report.flushAttempts()).isEqualTo(1);
            verify(repository, times(2)).saveOperationRecordsBatch(anyList());
        } finally {
            releaseFirstFlush.countDown();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
            shutdownExecutor.shutdownNow();
        }
    }

    @Test
    void shutdownWithReport_shouldRejectAppendThatFinishesRedactionAfterShutdownStarts() throws Exception {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        CountDownLatch redactionStarted = new CountDownLatch(1);
        CountDownLatch releaseRedaction = new CountDownLatch(1);
        AtomicInteger redactions = new AtomicInteger();
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService appendExecutor = Executors.newSingleThreadExecutor();
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .configuration(PersistenceRuntimeConfiguration.defaults())
                .autoCreateTables(false)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .redactor((target, value) -> {
                    if (redactions.getAndIncrement() == 0) {
                        redactionStarted.countDown();
                        try {
                            releaseRedaction.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return value;
                })
                .build();

        try {
            Future<?> append = appendExecutor.submit(() -> manager.append(stationRecord(UUID.randomUUID())));
            assertThat(redactionStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            PersistenceShutdownReport report = manager.shutdownWithReport(Duration.ofSeconds(1));
            releaseRedaction.countDown();

            // Then
            assertThat(report.successful()).isTrue();
            assertThat(report.initialBufferedStationLogs()).isZero();
            assertThatThrownBy(() -> append.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ExecutionPersistenceException.class);
            assertThat(manager.snapshotStats().bufferedStationLogs()).isZero();
            verify(repository, never()).saveOperationRecordsBatch(anyList());
        } finally {
            releaseRedaction.countDown();
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
            appendExecutor.shutdownNow();
        }
    }

    @Test
    void shutdown_shouldNotShutdownCallerManagedExecutors() {
        // Given
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = manager(repository, PersistenceRuntimeConfiguration.defaults(),
                                                   flushExecutor, scheduler);

        try {
            // When
            manager.shutdown(Duration.ofSeconds(1));

            // Then
            assertThat(flushExecutor.isShutdown()).isFalse();
            assertThat(scheduler.isShutdown()).isFalse();
        } finally {
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    private static DatabaseExecutionManager manager(DatabaseAssemblyRunRepository repository,
                                                    PersistenceRuntimeConfiguration configuration,
                                                    ExecutorService flushExecutor,
                                                    ScheduledExecutorService scheduler) {
        return DatabaseExecutionManager.builder()
                .repository(repository)
                .configuration(configuration)
                .autoCreateTables(false)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();
    }

    private static void awaitShutdownAdmissionClosure(DatabaseExecutionManager manager) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (manager.isAlive() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(manager.isAlive()).as("shutdown must close operation admission before waiting").isFalse();
    }

    private static void awaitCompletedFlushes(DatabaseExecutionManager manager, long expectedCompletedFlushes)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PersistenceRuntimeStats stats;
        do {
            stats = manager.snapshotStats();
            if (stats.completedFlushes() >= expectedCompletedFlushes) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        } while (System.nanoTime() < deadline);

        assertThat(stats.completedFlushes()).isGreaterThanOrEqualTo(expectedCompletedFlushes);
    }

    private static void awaitStats(DatabaseExecutionManager manager,
                                   long expectedFailedFlushes,
                                   int expectedBufferedLogs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PersistenceRuntimeStats stats;
        do {
            stats = manager.snapshotStats();
            if (stats.failedFlushes() == expectedFailedFlushes
                    && stats.bufferedStationLogs() == expectedBufferedLogs) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        } while (System.nanoTime() < deadline);

        assertThat(stats.failedFlushes()).isEqualTo(expectedFailedFlushes);
        assertThat(stats.bufferedStationLogs()).as("failed flush must not lose the drained records")
                .isEqualTo(expectedBufferedLogs);
    }

    private static StationLogRecord stationRecord(UUID runId) {
        return new StationLogRecord(UUID.randomUUID(), runId, "station", null, StationLogStatus.SUCCEEDED,
                Instant.now(), Instant.now(), null, null, Map.of(), null);
    }

    private static void assertMetadataOnly(AssemblyRunRecord record, String secret) {
        assertThat(record.context()).isEmpty();
        assertThat(record.inputParams()).isNull();
        assertThat(record.result()).isNull();
        assertThat(record.errorMessage()).isNull();
        assertThat(record.toString()).doesNotContain(secret);
    }
}
