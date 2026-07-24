package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.exception.PayloadCloneException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceRecordSnapshotTest {
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Test
    void constructors_shouldRejectMissingRequiredState() {
        // Given / When / Then
        assertThatNullPointerException()
                .isThrownBy(() -> assemblyRunRecord(null, "line", ExecutionStatus.RUNNING, Map.of()))
                .withMessage("id must not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assemblyRunRecord(UUID.randomUUID(), " ", ExecutionStatus.RUNNING, Map.of()))
                .withMessage("assemblyLineId must not be blank");
        assertThatNullPointerException()
                .isThrownBy(() -> assemblyRunRecord(UUID.randomUUID(), "line", null, Map.of()))
                .withMessage("status must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> stationLogRecord(null, UUID.randomUUID(), "station", StationLogStatus.RUNNING,
                                                   NOW, Map.of()))
                .withMessage("id must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> stationLogRecord(UUID.randomUUID(), null, "station", StationLogStatus.RUNNING,
                                                   NOW, Map.of()))
                .withMessage("assemblyLineExecutionId must not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> stationLogRecord(UUID.randomUUID(), UUID.randomUUID(), " ",
                                                   StationLogStatus.RUNNING, NOW, Map.of()))
                .withMessage("operationId must not be blank");
        assertThatNullPointerException()
                .isThrownBy(() -> stationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "station", null, NOW,
                                                   Map.of()))
                .withMessage("status must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> stationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "station",
                                                   StationLogStatus.RUNNING, null, Map.of()))
                .withMessage("startedAt must not be null");
    }

    @Test
    void constructors_shouldDefensivelyCopyPublicContextMaps() {
        // Given
        Map<String, Object> runContext = new LinkedHashMap<>();
        runContext.put("tenant", "captured");
        Map<String, Object> stationContext = new LinkedHashMap<>();
        stationContext.put("tenant", "captured");

        // When
        AssemblyRunRecord run = assemblyRunRecord(UUID.randomUUID(), "line", ExecutionStatus.RUNNING, runContext);
        StationLogRecord station = stationLogRecord(UUID.randomUUID(), run.id(), "station",
                                                    StationLogStatus.RUNNING, NOW, stationContext);
        runContext.put("late", true);
        stationContext.put("late", true);

        // Then
        assertThat(run.context()).containsOnlyKeys("tenant");
        assertThat(station.context()).containsOnlyKeys("tenant");
    }

    @Test
    void factories_shouldCloneNestedValuesAfterRedaction() {
        // Given
        MutablePayload runInput = new MutablePayload("input");
        MutablePayload runResult = new MutablePayload("result");
        MutablePayload runContextValue = new MutablePayload("run-context");
        MutablePayload stationContextValue = new MutablePayload("station-context");
        PayloadCloner cloner = new MutablePayloadCloner();
        AssemblyRunTrace runTrace = new AssemblyRunTrace(UUID.randomUUID(), "line", Map.of());
        runTrace.setInputParams(runInput);
        runTrace.setContext(Map.of("payload", runContextValue));
        runTrace.setResult(runResult);
        StationLogTrace stationTrace = StationLogTrace.start(runTrace.getId(), "station", null);
        stationTrace.setContext(Map.of("payload", stationContextValue));

        // When
        AssemblyRunRecord run = AssemblyRunRecord.from(runTrace, SensitiveDataRedactor.none(), cloner);
        StationLogRecord station = StationLogRecord.from(stationTrace)
                .redactedWith(SensitiveDataRedactor.none(), cloner);
        runInput.values().add("mutated");
        runResult.values().add("mutated");
        runContextValue.values().add("mutated");
        stationContextValue.values().add("mutated");

        // Then
        assertThat(((MutablePayload) run.inputParams()).values()).containsExactly("input");
        assertThat(((MutablePayload) run.result()).values()).containsExactly("result");
        assertThat(((MutablePayload) run.context().get("payload")).values()).containsExactly("run-context");
        assertThat(((MutablePayload) station.context().get("payload")).values()).containsExactly("station-context");
    }

    @Test
    void factory_shouldRejectUnknownMutableValuesWithoutExplicitCloner() {
        // Given
        AssemblyRunTrace trace = new AssemblyRunTrace(UUID.randomUUID(), "line", Map.of());
        trace.setInputParams(new MutablePayload("input"));

        // When / Then
        assertThatThrownBy(() -> AssemblyRunRecord.from(trace, SensitiveDataRedactor.none()))
                .isInstanceOf(PayloadCloneException.class)
                .hasMessageContaining("Could not isolate persistence value")
                .hasMessageContaining("PayloadCloner");
    }

    private static AssemblyRunRecord assemblyRunRecord(UUID id,
                                                       String assemblyLineId,
                                                       ExecutionStatus status,
                                                       Map<String, Object> context) {
        return new AssemblyRunRecord(id, assemblyLineId, context, null, null, status, NOW, null, null, null, null,
                null);
    }

    private static StationLogRecord stationLogRecord(UUID id,
                                                     UUID runId,
                                                     String operationId,
                                                     StationLogStatus status,
                                                     Instant startedAt,
                                                     Map<String, Object> context) {
        return new StationLogRecord(id, runId, operationId, null, null, status, startedAt, null, null, null, context,
                null);
    }

    private static final class MutablePayload {
        private final List<String> values;

        private MutablePayload(String value) {
            this.values = new ArrayList<>(List.of(value));
        }

        private MutablePayload(List<String> values) {
            this.values = new ArrayList<>(values);
        }

        private List<String> values() {
            return values;
        }
    }

    private static final class MutablePayloadCloner implements PayloadCloner {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T clonePayload(T payload) {
            if (payload instanceof MutablePayload mutablePayload) {
                return (T) new MutablePayload(mutablePayload.values());
            }
            return payload;
        }
    }
}
