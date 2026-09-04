package io.github.gear4jtest.external.api.artifact;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactStoreExecutorsTest {
    @Test
    void createDefaultAsyncPool_shouldBoundWorkAndRetireIdleCoreThreads() {
        // When
        ThreadPoolExecutor executor = ArtifactStoreExecutors.createDefaultAsyncPool();

        try {
            // Then
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(512);
            assertThat(executor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(30L);
            assertThat(executor.allowsCoreThreadTimeOut()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
