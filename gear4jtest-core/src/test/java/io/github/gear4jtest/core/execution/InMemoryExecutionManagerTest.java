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

    private static StationLogRecord record(UUID runId, String operationId) {
        return new StationLogRecord(UUID.randomUUID(), runId, operationId, null, null, StationLogStatus.RUNNING,
                Instant.now(), null, null, null, Map.of("secret", "value"), null);
    }
}
