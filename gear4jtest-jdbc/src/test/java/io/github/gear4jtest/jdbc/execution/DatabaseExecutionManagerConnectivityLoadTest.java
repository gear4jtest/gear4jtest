package io.github.gear4jtest.jdbc.execution;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PersistenceOperationalStatus;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class DatabaseExecutionManagerConnectivityLoadTest {
    @Test
    void probeHealth_shouldBoundSlowPoolAcquisitionWithoutAccumulatingProbeWorkers() throws Exception {
        // Given
        DataSource saturatedPool = mock(DataSource.class);
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        CountDownLatch releaseAcquisition = new CountDownLatch(1);
        when(saturatedPool.getConnection()).thenAnswer(invocation -> {
            acquisitionStarted.countDown();
            awaitUninterruptibly(releaseAcquisition);
            throw new SQLException("pool remained unavailable");
        });
        DatabaseAssemblyRunRepository repository = DatabaseAssemblyRunRepository.builder()
                .dataSource(saturatedPool)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
        PersistenceRuntimeConfiguration configuration = PersistenceRuntimeConfiguration.builder()
                .connectivityProbeTimeout(Duration.ofMillis(100))
                .shutdownTimeout(Duration.ofSeconds(1))
                .build();
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .configuration(configuration)
                .build();

        try {
            // When
            long firstStartedAt = System.nanoTime();
            PersistenceOperationalStatus first = manager.probeHealth();
            long firstDuration = System.nanoTime() - firstStartedAt;
            long secondStartedAt = System.nanoTime();
            PersistenceOperationalStatus second = manager.probeHealth();
            long secondDuration = System.nanoTime() - secondStartedAt;

            // Then
            assertThat(acquisitionStarted.getCount()).isZero();
            assertConnectivityUnavailable(first);
            assertConnectivityUnavailable(second);
            assertThat(firstDuration).isLessThan(TimeUnit.SECONDS.toNanos(1));
            assertThat(secondDuration).isLessThan(TimeUnit.SECONDS.toNanos(1));
            verify(saturatedPool, times(1)).getConnection();
        } finally {
            releaseAcquisition.countDown();
            manager.shutdown();
        }
    }

    private static void assertConnectivityUnavailable(PersistenceOperationalStatus status) {
        assertThat(status.live()).isTrue();
        assertThat(status.ready()).isFalse();
        assertThat(status.connectivityVerified()).isTrue();
        assertThat(status.connectivityAvailable()).isFalse();
        assertThat(status.reason()).isEqualTo(PersistenceOperationalStatus.Reason.CONNECTIVITY_UNAVAILABLE);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
