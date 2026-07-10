package io.github.gear4jtest.core.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.security.RedactionTarget;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryExecutionManagerTest {
    @Test
    void builder_shouldRequireRepository() {
        assertThatThrownBy(() -> InMemoryExecutionManager.builder().repository(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("repository must not be null");
    }

    @Test
    void startAppendAppendAllAndEnd_shouldPersistRedactedRecords() {
        InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();
        SensitiveDataRedactor redactor = (target, value) -> target == RedactionTarget.RUN_CONTEXT
                || target == RedactionTarget.STATION_CONTEXT ? Map.of("redacted", true) : value;
        InMemoryExecutionManager manager = InMemoryExecutionManager.builder()
                .repository(repository)
                .redactor(redactor)
                .build();
        UUID runId = UUID.randomUUID();
        AssemblyRunTrace run = new AssemblyRunTrace(runId, "pipe", Map.of("secret", "value"));
        run.setContext(Map.of("secret", "value"));
        run.markStarted();

        manager.start(run);
        manager.append(record(runId, "root"));
        manager.append(null);
        manager.appendAll(List.of(record(runId, "child")));
        manager.appendAll(null);
        run.markSuccess("done");
        manager.end(run);

        assertThat(repository.findById(runId)).isPresent();
        assertThat(repository.findById(runId).orElseThrow().context()).containsEntry("redacted", true);
        assertThat(repository.findAllLogsByRunId(runId, PageRequest.first(10)))
                .extracting(StationLogRecord::operationId)
                .containsExactlyInAnyOrder("root", "child");
        assertThat(repository.findAllLogsByRunId(runId, PageRequest.first(10)))
                .allSatisfy(record -> assertThat(record.context()).containsEntry("redacted", true));
    }

    @Test
    void startAndEnd_shouldRejectNullRuns() {
        InMemoryExecutionManager manager = InMemoryExecutionManager.builder().build();

        assertThatNullPointerException().isThrownBy(() -> manager.start(null))
                .withMessage("execution must not be null");
        assertThatNullPointerException().isThrownBy(() -> manager.end(null))
                .withMessage("finalExecution must not be null");
    }

    @Test
    void defaultPolicy_shouldPersistMetadataWithoutSensitiveValues() {
        // Given
        String secret = "fixture-secret-must-not-leak";
        InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();
        InMemoryExecutionManager manager = InMemoryExecutionManager.builder().repository(repository).build();
        UUID runId = UUID.randomUUID();
        AssemblyRunTrace run = new AssemblyRunTrace(runId, "pipe", Map.of("token", secret));
        run.setContext(Map.of("token", secret));
        run.setResult(secret);
        run.setErrorMessage(secret);

        // When
        manager.start(run);
        manager.append(new StationLogRecord(UUID.randomUUID(), runId, "root", null, null,
                StationLogStatus.FAILED, Instant.now(), Instant.now(), secret, secret, Map.of("token", secret), null));
        manager.end(run);

        // Then
        var storedRun = repository.findById(runId).orElseThrow();
        assertThat(storedRun.context()).isEmpty();
        assertThat(storedRun.inputParams()).isNull();
        assertThat(storedRun.result()).isNull();
        assertThat(storedRun.errorMessage()).isNull();
        assertThat(storedRun.toString()).doesNotContain(secret);
        assertThat(repository.findAllLogsByRunId(runId, PageRequest.first(10))).singleElement()
                .satisfies(log -> {
                    assertThat(log.context()).isEmpty();
                    assertThat(log.errorMessage()).isNull();
                    assertThat(log.errorHandlerMessages()).isNull();
                    assertThat(log.toString()).doesNotContain(secret);
                });
    }

    private static StationLogRecord record(UUID runId, String operationId) {
        return new StationLogRecord(UUID.randomUUID(), runId, operationId, null, null, StationLogStatus.RUNNING,
                Instant.now(), null, null, null, Map.of("secret", "value"), null);
    }
}
