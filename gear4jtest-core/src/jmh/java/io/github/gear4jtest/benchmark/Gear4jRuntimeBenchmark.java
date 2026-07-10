package io.github.gear4jtest.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCacheEntry;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCacheKey;
import io.github.gear4jtest.experimental.cache.assemblylinecache.InMemoryAssemblyLineCacheRepository;
import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompilers;
import io.github.gear4jtest.jackson.JacksonPayloadCloner;
import io.github.gear4jtest.xml.expression.GearExpression;
import io.github.gear4jtest.xml.expression.GearExpressionContext;
import io.github.gear4jtest.xml.expression.GearExpressionParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@BenchmarkMode({ Mode.Throughput, Mode.AverageTime })
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class Gear4jRuntimeBenchmark {
    private static final int ARTIFACT_SIZE = 8 * 1024 * 1024;

    private final Event event = new Event("benchmark", UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "station-finished");
    private final EventSubscription<Event> subscription = EventSubscription.on(Event.class,
                                                                               candidate -> candidate.getName()
                                                                                       .startsWith("station"),
                                                                               candidate -> {
                                                                                   // Benchmark predicate dispatch only;
                                                                                   // reaction execution is measured
                                                                                   // separately by runtime tests.
                                                                               });
    private final GearExpression expression = GearExpressionParser
            .parse("input.customer.active == true && variables.tenant == 'benchmark'");
    private final GearExpressionContext expressionContext = new GearExpressionContext(
            Map.of("customer", Map.of("active", true)), Map.of("tenant", "benchmark"));
    private final JacksonPayloadCloner payloadCloner = new JacksonPayloadCloner(new ObjectMapper());
    private final List<Map<String, Object>> payload = new ArrayList<>(List.of(
                                                                              Map.of("id", 1, "tags",
                                                                                     new ArrayList<>(
                                                                                             List.of("a", "b", "c"))),
                                                                              Map.of("id", 2, "tags", new ArrayList<>(
                                                                                      List.of("d", "e", "f")))));
    private final GeneratedSourceCompiler compiler = GeneratedSourceCompilers.javac();
    private final byte[] generatedSource = ("package benchmark.generated; "
            + "public final class GeneratedStep { public String apply(String value) { return value + \"-ok\"; } }")
            .getBytes(StandardCharsets.UTF_8);
    private final InMemoryAssemblyLineCacheRepository cache = new InMemoryAssemblyLineCacheRepository(128,
            PayloadCloners.immutableAware());
    private final AssemblyLineCacheKey cacheKey = new AssemblyLineCacheKey("benchmark", "1", new byte[] { 1 },
            new byte[] { 2 });
    private final byte[] artifactBytes = new byte[ARTIFACT_SIZE];
    private final Artifact artifact = new Artifact("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ARTIFACT_SIZE, Map.of(), () -> new java.io.ByteArrayInputStream(artifactBytes));

    public Gear4jRuntimeBenchmark() {
        cache.save(new AssemblyLineCacheEntry<>(cacheKey, "cached-output", Instant.now().plusSeconds(3_600),
                Instant.now()));
    }

    @Benchmark
    public boolean eventSubscriptionFilter() {
        return subscription.accepts(event);
    }

    @Benchmark
    public boolean gelEvaluate() {
        return expression.evaluateBoolean(expressionContext);
    }

    @Benchmark
    public Object jacksonClone() {
        return payloadCloner.clonePayload(payload);
    }

    @Benchmark
    public int generatedSourceCompilation() {
        return compiler.compile("benchmark.generated.GeneratedStep", generatedSource).size();
    }

    @Benchmark
    public String cacheRead() {
        return cache.<String>findValid(cacheKey, Instant.now()).orElseThrow().output();
    }

    @Benchmark
    public long artifactStream8MiB() throws IOException {
        try (var input = artifact.openStreamChecked()) {
            return input.transferTo(java.io.OutputStream.nullOutputStream());
        }
    }
}
