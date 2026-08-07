package io.github.gear4jtest.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.AssemblyLines.chain;
import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class PersistenceConfigurationPropagationTest {
    @Test
    void requestThreshold_shouldReachIteratorAndNestedRunsWithoutDuplicateAppends() {
        RecordingPersistenceManager manager = new RecordingPersistenceManager();
        AssemblyLineEngine engine = engine(manager);
        AssemblyLine<List<String>, List<String>> parent = iteratorCallingNestedChild();

        engine.execute(parent, RunRequest.builder()
                .input(List.of("first", "second"))
                .persistence(configuration(7))
                .resourceFactory(reflectiveResourceFactory())
                .build());

        assertThat(manager.startedThresholds).containsExactly(7, 7, 7);
        assertThat(manager.flushes).hasSize(3);
        assertThat(manager.endedRuns).hasSize(3);
        assertThat(manager.appendAllInvocations).isZero();
        assertThat(manager.appendedRecords)
                .extracting(record -> record.id() + ":" + record.status())
                .doesNotHaveDuplicates();
    }

    @Test
    void assemblyLineThreshold_shouldBeInheritedByNestedRunsWhenRequestDoesNotOverrideIt() {
        RecordingPersistenceManager manager = new RecordingPersistenceManager();
        AssemblyLineEngine engine = engine(manager);

        engine.execute(iteratorCallingNestedChild(), RunRequest.builder()
                .input(List.of("only"))
                .resourceFactory(reflectiveResourceFactory())
                .build());

        assertThat(manager.startedThresholds).containsExactly(3, 3);
    }

    @Test
    void requestPersistenceConfiguration_shouldControlStoredRunResult() {
        RecordingPersistenceManager manager = new RecordingPersistenceManager();
        AssemblyLineEngine engine = engine(manager);
        AssemblyLine<String, String> assemblyLine = AssemblyLines.<String>createAssemblyLine("line")
                .then(processingOperation("identity", IdentityOperator.class).build())
                .build();
        PersistenceConfiguration requestConfiguration = PersistenceConfiguration.builder()
                .storeResultObject(false)
                .stationLogFlushThreshold(5)
                .build();

        ExecutionResult<String> result = engine.execute(assemblyLine, RunRequest.builder()
                .input("value")
                .persistence(requestConfiguration)
                .resourceFactory(reflectiveResourceFactory())
                .build());

        assertThat(result.getResult()).isEqualTo("value");
        assertThat(result.getExecution().getResult()).isNull();
        assertThat(manager.startedThresholds).containsExactly(5);
    }

    private static AssemblyLine<List<String>, List<String>> iteratorCallingNestedChild() {
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                .then(processingOperation("child-step", IdentityOperator.class).build())
                .persistence(configuration(11))
                .build();
        AssemblyLineCallStation<String, String> childCall = AssemblyLineCallStation
                .nestedRun("call-child", child);
        return AssemblyLines.<List<String>>createAssemblyLine("parent")
                .then(Stations.<List<String>>iterate("iterator")
                        .iterableFunction(Function.identity())
                        .sequence(chain("item-chain", childCall).build())
                        .collector(Collectors.toList())
                        .build())
                .persistence(configuration(3))
                .build();
    }

    private static PersistenceConfiguration configuration(int flushThreshold) {
        return PersistenceConfiguration.builder().stationLogFlushThreshold(flushThreshold).build();
    }

    private static AssemblyLineEngine engine(RecordingPersistenceManager manager) {
        return AssemblyLineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(List.of(new PersistenceExtension(manager))))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                try {
                    return type.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    public static class IdentityOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext context) {
            return input;
        }
    }

    private static final class RecordingPersistenceManager implements RunPersistenceManager {
        private final List<Integer> startedThresholds = new ArrayList<>();
        private final List<StationLogRecord> appendedRecords = new ArrayList<>();
        private final List<UUID> flushes = new ArrayList<>();
        private final List<UUID> endedRuns = new ArrayList<>();
        private int appendAllInvocations;

        @Override
        public void start(RunTrace execution) {
            startedThresholds.add(-1);
        }

        @Override
        public void start(RunTrace execution, PersistenceConfiguration configuration) {
            startedThresholds.add(configuration.getStationLogFlushThreshold().orElse(-1));
        }

        @Override
        public void append(StationLogRecord stationLogRecord) {
            appendedRecords.add(stationLogRecord);
        }

        @Override
        public void appendAll(List<StationLogRecord> records) {
            appendAllInvocations++;
        }

        @Override
        public void flush(UUID runId) {
            flushes.add(runId);
        }

        @Override
        public void end(RunTrace finalExecution) {
            endedRuns.add(finalExecution.getId());
        }
    }
}
