package io.github.gear4jtest.studio.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RunObservationDispatcherTest {
    @Test
    void blockedProgressSinkDoesNotDelayBusinessCompletionOrFinalSnapshot() throws Exception {
        // Given: the operation finishes only once persistence is blocked on the worker.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var operation = new WaitForObserver(entered);
        ResourceFactory resources = new ResourceFactory() {
            public <T> T getResource(Class<T> type) {
                return type.cast(operation);
            }
        };
        var executor = DemoWiring.plainExecutor(resources);
        var pool = Executors.newSingleThreadExecutor();
        try (var dispatcher = new RunObservationDispatcher(1, Duration.ofMillis(10));
                var observer = new Gear4jRunObserver(dispatcher, value -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    await(release);
                }, DataCapturePolicy.none(), null)) {
            var line = AssemblyLines.<String>createAssemblyLine("observed")
                    .then(Stations.processingOperation("work", WaitForObserver.class).build()).build();
            // When
            var result = pool.submit(() -> executor.execute(line,
                                                            RunRequest.builder().input("synthetic")
                                                                    .resourceFactory(resources).with(observer).build()))
                    .get(5, TimeUnit.SECONDS);
            // Then: no persistence latch has been released, but all final steps exist.
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(calls).hasValue(1);
            var finalSnapshot = observer.finish(result);
            assertThat(finalSnapshot.status()).isEqualTo("SUCCEEDED");
            assertThat(finalSnapshot.steps()).singleElement()
                    .satisfies(s -> assertThat(s.status()).isEqualTo("SUCCEEDED"));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void capacityDropsOnlyProgressAndReleasedSlotsCanBeReusedWithoutAnEventQueue() throws Exception {
        // Given
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var dropped = new AtomicInteger();
        var next = new CountDownLatch(1);
        try (var dispatcher = new RunObservationDispatcher(1, Duration.ofMillis(10))) {
            var first = dispatcher.register(() -> snapshot("RUNNING"), value -> {
                blocked.countDown();
                await(release);
            });
            assertThat(blocked.await(2, TimeUnit.SECONDS)).isTrue();
            var discarded = dispatcher.register(() -> {
                dropped.incrementAndGet();
                return snapshot("RUNNING");
            }, value -> {
            });
            // When: unregistering never waits for a blocked sink; a new slot is available.
            first.close();
            dispatcher.register(() -> snapshot("RUNNING"), value -> next.countDown());
            release.countDown();
            assertThat(next.await(2, TimeUnit.SECONDS)).isTrue();
            // Then
            assertThat(dropped).hasValue(0);
            discarded.close();
        } finally {
            release.countDown();
        }
        assertThatThrownBy(() -> new RunObservationDispatcher(0, Duration.ofMillis(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scenarioChecksUseActualOutputsEvenWhenCaptureIsMaskedOrDisabledForReferences() {
        // Given
        var resources = DemoWiring.plainResources();
        try (var runtime = DemoWiring.runtime("scenario", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.masked(text -> "[redacted]"))) {
            var r = new DraftRevision(UUID.randomUUID(), UUID.randomUUID(), 1, "alice", Instant.now(),
                    DraftRevision.Origin.STUDIO, DemoWiring.sample());
            var expected = new ScenarioExpectation("SUCCEEDED", "HELLO");
            var literal = new ScenarioData(new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, "  hello  ", null),
                    expected);
            // When / Then
            var result = runtime.executeScenario(r, "  hello  ", UUID.randomUUID(), "alice", literal, ignored -> {
            });
            assertThat(result.scenarioCheck().status()).isEqualTo(ScenarioCheck.Status.PASSED);
            assertThat(result.output().value()).isEqualTo("[redacted]");
            var reference = new ScenarioData(new ScenarioInput(ScenarioInput.Kind.FIXTURE_REFERENCE, "fixture", "1"),
                    expected);
            var withoutCapture = runtime.executeScenario(r, "  hello  ", UUID.randomUUID(), "alice", reference,
                                                         ignored -> {
                                                         });
            assertThat(withoutCapture.scenarioCheck().status()).isEqualTo(ScenarioCheck.Status.PASSED);
            assertThat(withoutCapture.input().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
            assertThat(withoutCapture.output().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
        }
    }

    private static RunObservation snapshot(String status) {
        return new RunObservation(null, null, null, status, Instant.now(), null, CapturedValue.none(),
                CapturedValue.none(), null, List.of(), false, null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("Synthetic deadline");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class WaitForObserver implements Operator<String, String> {
        private final CountDownLatch entered;

        WaitForObserver(CountDownLatch entered) {
            this.entered = entered;
        }

        public String transform(String input, StationExecutionContext context) {
            await(entered);
            return input;
        }
    }
}
