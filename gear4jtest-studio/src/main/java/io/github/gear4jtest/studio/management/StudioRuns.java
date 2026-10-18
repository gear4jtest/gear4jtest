package io.github.gear4jtest.studio.management;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.StudioRuntime;

import static io.github.gear4jtest.studio.management.ResourceAccess.*;
import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

final class StudioRuns {
    private static final int SCAN_BUDGET = 500;
    private final StudioStore store;
    private final StudioRuntime runtime;
    private final Clock clock;
    private final StudioRunRecorder recorder;
    private final PageCursors cursors;

    StudioRuns(StudioStore store, StudioRuntime runtime, Clock clock) {
        this.store = store;
        this.runtime = runtime;
        this.clock = clock;
        recorder = new StudioRunRecorder(store, runtime.context().id(), clock);
        cursors = new PageCursors(clock);
    }

    void begin(StudioStore.Session tx,
               UUID id,
               StudioIdentity actor,
               DraftRevision revision,
               StudioVersion version,
               UUID requestId) {
        begin(tx, id, actor, revision, version, requestId, null, null);
    }

    void begin(StudioStore.Session tx,
               UUID id,
               StudioIdentity actor,
               DraftRevision revision,
               StudioVersion version,
               UUID requestId,
               ScenarioRef scenario,
               ResolvedFixture fixture) {
        var draft = (StudioDraft) resource(tx, revision.draftId());
        var chain = chain(tx, draft);
        var context = runtime.context();
        var provenance = new StudioRun.Provenance(context.id(), chain.runtimeAssemblyLineId(),
                context.applicationBuild(), context.catalogFingerprint(),
                version == null ? null : version.artifactHash(),
                actor.principalId(), "TEST", requestId, draft.id(), revision.revisionId(), revision.number(),
                version == null ? null : version.id(), version == null ? null : version.label());
        recorder.begin(tx, id, version == null ? draft.id() : chain.id(), StudioRun.Origin.CONTROLLED_TEST,
                       provenance.withScenario(scenario).withFixture(fixture));
    }

    ControlledRun execute(UUID id, Function<Consumer<RunObservation>, ControlledRun> execution) {
        try {
            var result = execution.apply(observation -> {
                if (!observation.terminal())
                    recorder.record(id, observation);
            });
            recorder.record(id, RunObservation.completed(result));
            return result;
        } catch (RuntimeException failure) {
            var previous = store.read(tx -> tx.run(id));
            if (previous != null && (previous.status().equals("PREPARING") || previous.status().equals("RUNNING"))) {
                var observation = previous.observation();
                boolean preparation = observation == null && failure instanceof StudioProblem;
                recorder.record(id, new RunObservation(observation == null ? null : observation.runId(),
                        observation == null ? null : observation.parentRunId(),
                        observation == null ? null : observation.rootRunId(),
                        preparation ? "PREPARATION_FAILED" : "UNKNOWN", previous.startedAt(),
                        Instant.ofEpochMilli(clock.millis()),
                        CapturedValue.none(), CapturedValue.none(),
                        preparation ? "TEST_NOT_ADMITTED" : "TEST_OUTCOME_UNKNOWN",
                        observation == null ? List.of() : observation.steps(),
                        observation != null && observation.stepsTruncated(),
                        observation == null ? previous.provenance().artifactHash() : observation.artifactHash(),
                        previous.provenance().scenario() == null ? null
                                : new ScenarioCheck(ScenarioCheck.Status.NOT_EVALUATED, List.of())));
            }
            throw failure;
        }
    }

    StudioRun get(StudioIdentity actor, UUID id) {
        return store.read(tx -> {
            var run = tx.run(id);
            if (run == null)
                throw new StudioProblem(NOT_FOUND);
            var rights = requireRead(tx, actor, resource(tx, run.resourceId()), Permission.READ_RUNS);
            run = fixtureView(tx, actor, run);
            return rights.contains(Permission.READ_OBSERVABILITY) ? run : run.summary();
        });
    }

    StudioPage<StudioRun> list(StudioIdentity actor, UUID scope, RunFilter filter, String after, int size) {
        if (size < 1 || size > 100)
            throw new StudioProblem(INVALID_REQUEST);
        String cursorScope = "runs:" + scope + ":" + filter;
        String position = cursors.decode(after, actor, cursorScope);
        return store.read(tx -> {
            var page = scan(tx, actor, scope, filter, position, size, Permission.READ_RUNS);
            return new StudioPage<>(page.items().stream().map(r -> fixtureView(tx, actor, r).summary()).toList(),
                    page.nextCursor() == null ? null : cursors.encode(page.nextCursor(), actor, cursorScope));
        });
    }

    private StudioRun fixtureView(StudioStore.Session tx, StudioIdentity actor, StudioRun run) {
        if (run.provenance().fixture() == null)
            return run;
        var reference = run.provenance().scenario();
        var scenario = reference == null ? null : tx.scenario(reference.scenarioId());
        if (scenario != null) {
            var rights = permissions(tx, actor, resource(tx, scenario.resourceId()));
            if (rights.contains(Permission.READ) && rights.contains(Permission.READ_SCENARIOS))
                return run;
        }
        return run.withoutFixtureProvenance();
    }

    private StudioPage<StudioRun> scan(StudioStore.Session tx,
                                       StudioIdentity actor,
                                       UUID scope,
                                       RunFilter filter,
                                       String position,
                                       int size,
                                       Permission permission) {
        var target = resource(tx, scope);
        requireRead(tx, actor, target, permission);
        UUID draftId = target instanceof StudioDraft ? target.id() : null;
        var chain = target instanceof StudioDraft draft ? chain(tx, draft) : target instanceof StudioChain c ? c : null;
        if (chain == null)
            throw new StudioProblem(INVALID_REQUEST);
        String cursor = position;
        var visible = new ArrayList<StudioRun>();
        int scanned = 0;
        while (scanned < SCAN_BUDGET) {
            var batch = tx.runs(chain.id(), draftId, filter, cursor, Math.min(100, SCAN_BUDGET - scanned));
            for (var run : batch) {
                var rights = permissions(tx, actor, resource(tx, run.resourceId()));
                if (rights.contains(Permission.READ) && rights.contains(permission)) {
                    if (visible.size() == size)
                        return new StudioPage<>(visible, cursor);
                    visible.add(run);
                }
                cursor = position(run);
                scanned++;
            }
            if (batch.size() < 100)
                return new StudioPage<>(visible, null);
        }
        return new StudioPage<>(visible, cursor);
    }

    private static String position(StudioRun run) {
        return run.createdAt().toEpochMilli() + ":" + run.id();
    }

    RunMetrics metrics(StudioIdentity actor, UUID scope, RunFilter filter) {
        return store.read(tx -> {
            var page = scan(tx, actor, scope, filter, null, SCAN_BUDGET, Permission.READ_OBSERVABILITY);
            var statuses = new TreeMap<String, Long>();
            var durations = new ArrayList<Long>();
            var buckets = new TreeMap<Instant, long[]>();
            var operations = new java.util.LinkedHashMap<NodeKey, OperationValues>();
            long bucketMillis = Duration.between(filter.from(), filter.until()).compareTo(Duration.ofDays(2)) > 0
                    ? Duration.ofDays(1).toMillis() : Duration.ofHours(1).toMillis();
            for (var run : page.items()) {
                statuses.merge(run.status(), 1L, Long::sum);
                if (run.durationMillis() != null)
                    durations.add(run.durationMillis());
                long bucket = Math.floorDiv(run.createdAt().toEpochMilli(), bucketMillis) * bucketMillis;
                var counters = buckets.computeIfAbsent(Instant.ofEpochMilli(bucket), ignored -> new long[2]);
                counters[0]++;
                if (run.status().equals("FAILED"))
                    counters[1]++;
                var p = run.provenance();
                var dimension = new RunMetrics.Dimension(p.versionId(), p.mode(), p.artifactHash(),
                        p.catalogFingerprint(), p.applicationBuild(),
                        p.artifactHash() == null || p.catalogFingerprint() == null || p.applicationBuild() == null
                                ? run.id() : null);
                if (run.observation() != null)
                    for (var step : run.observation().steps())
                        operations.computeIfAbsent(new NodeKey(step.nodeId(), dimension),
                                                   ignored -> new OperationValues())
                                .add(step);
            }
            durations.sort(Long::compareTo);
            var timeline = buckets.entrySet().stream()
                    .map(e -> new RunMetrics.Bucket(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
            var values = operations.entrySet().stream().map(e -> e.getValue().result(e.getKey())).toList();
            var highlights = new LinkedHashSet<RunMetrics.Operation>();
            values.stream()
                    .sorted(Comparator
                            .comparing(RunMetrics.Operation::averageMillis,
                                       Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(RunMetrics.Operation::nodeId))
                    .limit(20).forEach(highlights::add);
            values.stream().filter(value -> value.failures() > 0)
                    .sorted(Comparator.comparingLong(RunMetrics.Operation::failures).reversed()
                            .thenComparing(RunMetrics.Operation::nodeId))
                    .limit(20).forEach(highlights::add);
            boolean partial = page.nextCursor() != null || page.items().stream()
                    .anyMatch(run -> run.observation() != null && run.observation().stepsTruncated());
            return new RunMetrics(page.items().size(), statuses, durations.isEmpty() ? null : durations.get(0),
                    durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).average().orElseThrow(),
                    durations.isEmpty() ? null : durations.get(durations.size() - 1),
                    durations.isEmpty() ? null : durations.get((int) Math.ceil(durations.size() * 0.95) - 1),
                    timeline, List.copyOf(highlights), partial, filter.from(), filter.until());
        });
    }

    private static final class OperationValues {
        private long count;
        private long failures;
        private final List<Long> durations = new ArrayList<>();

        void add(ControlledRun.Step step) {
            count++;
            if (step.status().equals("FAILED"))
                failures++;
            if (step.startedAt() != null && step.endedAt() != null)
                durations.add(Math.max(0, Duration.between(step.startedAt(), step.endedAt()).toMillis()));
        }

        RunMetrics.Operation result(NodeKey key) {
            return new RunMetrics.Operation(key.nodeId(), key.dimension(), count, failures,
                    durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).average().orElseThrow(),
                    durations.stream().max(Long::compareTo).orElse(null));
        }
    }

    private record NodeKey(String nodeId, RunMetrics.Dimension dimension) {}
}
