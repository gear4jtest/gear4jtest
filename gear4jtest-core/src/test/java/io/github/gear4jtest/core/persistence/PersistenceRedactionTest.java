package io.github.gear4jtest.core.persistence;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRedactionTest {
    private final SensitiveDataRedactor redactor = (target, value) -> switch (target) {
        case RUN_CONTEXT, STATION_CONTEXT -> Map.of("token", "***");
        case RUN_INPUT, RUN_RESULT -> "***";
        case RUN_ERROR_MESSAGE, STATION_ERROR_MESSAGE, STATION_ERROR_HANDLER_MESSAGES -> "redacted-error";
        default -> value;
    };

    @Test
    void assemblyRunRecord_shouldRedactPersistedValues() {
        // Given
        AssemblyRunTrace trace = new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of("secret", "value"));
        trace.setContext(Map.of("token", "raw"));
        trace.setResult("raw-result");
        trace.setErrorMessage("raw-error");

        // When
        AssemblyRunRecord record = AssemblyRunRecord.from(trace, redactor);

        // Then
        assertThat(record.context()).containsEntry("token", "***");
        assertThat(record.inputParams()).isEqualTo("***");
        assertThat(record.result()).isEqualTo("***");
        assertThat(record.errorMessage()).isEqualTo("redacted-error");
    }

    @Test
    void stationLogRecord_shouldRedactPersistedDiagnostics() {
        // Given
        StationLogTrace trace = StationLogTrace.start(UUID.randomUUID(), "step", null);
        trace.setContext(Map.of("token", "raw"));
        trace.markFailed(new IllegalStateException("raw-error"));

        // When
        StationLogRecord record = StationLogRecord.from(trace).redactedWith(redactor);

        // Then
        assertThat(record.context()).containsEntry("token", "***");
        assertThat(record.errorMessage()).isEqualTo("redacted-error");
        assertThat(record.errorHandlerMessages()).isEqualTo("redacted-error");
    }
}
