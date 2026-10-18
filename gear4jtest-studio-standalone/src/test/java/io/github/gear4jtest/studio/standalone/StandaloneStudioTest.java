package io.github.gear4jtest.studio.standalone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.gear4jtest.studio.demo.*;
import io.github.gear4jtest.studio.http.RequestActorContext;
import io.github.gear4jtest.studio.http.StudioManagementHttpHandler;
import io.github.gear4jtest.studio.json.DefinitionJson;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.studio.springdemo.SpringStudioApplication;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StandaloneStudioTest {
    private static final String EDITOR = "synthetic-editor-01234567890123456789";
    private static final String VIEWER = "synthetic-viewer-01234567890123456789";
    private static final StudioIdentity ACTOR = new StudioIdentity("demo-editor", Set.of(), false);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void federatesJavaAndSpringWithIndependentDataAclPublicationAndDurableScenarioProvenance() throws Exception {
        // Given: two real runtimes, distinct databases, and one standalone process
        // facade.
        var javaDb = database();
        var springDb = database();
        try (var javaLife = javaDb.getConnection(); var springLife = springDb.getConnection()) {
            var resources = DemoWiring.plainResources();
            try (var runtime = DemoWiring.runtime("java", DemoWiring.plainExecutor(resources), resources,
                                                  DataCapturePolicy.allowed());
                    var publisher = new DemoPublicationHost(javaDb, DemoWiring.catalog())) {
                var actors = new RequestActorContext();
                var service = DemoDatabase.service(javaDb, runtime, actors, publisher.publisher());
                try (var javaHost = new StudioHttpServer(service, actors, 0, EDITOR, VIEWER);
                        var springHost = SpringStudioApplication.start(springDb, 0, EDITOR, VIEWER,
                                                                       DataCapturePolicy.none())) {
                    javaHost.start();
                    var config = configuration(javaHost.port(), springHost.port());
                    try (var remote = new HttpRemoteManagement(config.directory(), config.credentials());
                            var studio = new StandaloneStudioServer(0, config.directory(), remote,
                                    config::authenticate)) {
                        studio.start();
                        var inventory = json(call(studio.port(), "/studio/api/v1/targets", "GET", null, EDITOR));
                        assertThat(inventory.path("targets")).hasSize(2);
                        assertThat(inventory.toString()).doesNotContain("endpoint", "127.0.0.1", EDITOR);
                        assertThat(json(call(studio.port(), "/studio/api/v1/targets", "GET", null, VIEWER))
                                .path("targets")).hasSize(1);
                        assertThat(call(studio.port(), "/studio/api/v1/targets", "GET", null, "wrong").statusCode())
                                .isEqualTo(401);
                        assertThat(call(studio.port(), "/targets/spring/api/v1/context", "GET", null, VIEWER)
                                .statusCode()).isEqualTo(404);
                        // When: identical names live in different contexts; mutations use the selected
                        // namespace.
                        var first = json(call(studio.port(), "/targets/java/api/v1/domains", "POST",
                                              Map.of("key", "catalog", "name", "Catalog", "description", ""), EDITOR));
                        var second = json(call(studio.port(), "/targets/spring/api/v1/domains", "POST",
                                               Map.of("key", "catalog", "name", "Catalog", "description", ""), EDITOR));
                        assertThat(first.path("resource").path("id").asText())
                                .isNotEqualTo(second.path("resource").path("id").asText());
                        var chain = json(call(studio.port(), "/targets/java/api/v1/chains", "POST",
                                              Map.of("domainId", first.path("resource").path("id").asText(),
                                                     "key", "greeting", "name", "Greeting", "description", "",
                                                     "runtimeAssemblyLineId", "text_preparation"),
                                              EDITOR))
                                .path("resource").path("id").asText();
                        var draft = json(call(studio.port(), "/targets/java/api/v1/drafts", "POST",
                                              Map.of("chainId", chain, "name", "Draft", "definition",
                                                     DefinitionJson.write(DemoWiring.sample())),
                                              EDITOR))
                                .path("draft").path("id").asText();
                        assertThat(call(studio.port(), "/targets/spring/api/v1/drafts/" + draft, "GET", null, EDITOR)
                                .statusCode()).isEqualTo(404);
                        assertThat(call(studio.port(), "/targets/java/api/v1/drafts/" + draft, "GET", null, VIEWER)
                                .statusCode()).isEqualTo(404);
                        var version = json(call(studio.port(), "/targets/java/api/v1/drafts/" + draft + "/versions",
                                                "POST", Map.of("revision", 1, "label", "1.0.0"), EDITOR))
                                .path("version").path("id").asText();
                        var scenario = json(call(studio.port(),
                                                 "/targets/java/api/v1/resources/" + chain + "/scenarios", "POST",
                                                 Map.of("name", "Greeting", "data", Map.of(
                                                                                           "input",
                                                                                           Map.of("kind",
                                                                                                  "FIXTURE_REFERENCE",
                                                                                                  "value",
                                                                                                  "demo/greeting",
                                                                                                  "fixtureVersion",
                                                                                                  "1"),
                                                                                           "expected",
                                                                                           Map.of("outcome",
                                                                                                  "SUCCEEDED", "output",
                                                                                                  "HELLO"))),
                                                 EDITOR))
                                .path("scenario").path("id").asText();
                        String request = UUID.randomUUID().toString();
                        var tested = json(call(studio.port(), "/targets/java/api/v1/scenarios/" + scenario + "/tests",
                                               "POST", Map.of("revision", 1, "target", Map.of("versionId", version),
                                                              "requestId", request),
                                               EDITOR));
                        assertThat(tested.path("state").asText()).isEqualTo("COMPLETED");
                        assertThat(tested.path("run").path("scenarioCheck").path("status").asText())
                                .isEqualTo("PASSED");
                        assertThat(tested.path("run").path("fixture").path("fingerprint").path("value").asText())
                                .hasSize(64);
                        assertThat(tested.path("run").path("input").path("state").asText()).isEqualTo("NOT_CAPTURED");
                        assertThat(json(call(studio.port(), "/targets/java/api/v1/tests/" + request, "GET", null,
                                             EDITOR)))
                                .isEqualTo(tested);
                        assertThat(call(studio.port(), "/targets/java/api/v1/versions/" + version + "/publish", "POST",
                                        Map.of(), EDITOR)
                                .statusCode()).isEqualTo(200);
                        assertThat(call(studio.port(), "/targets/java/api/v1/versions/" + version + "/promote", "POST",
                                        Map.of(), EDITOR)
                                .statusCode()).isEqualTo(200);
                        var runs = json(call(studio.port(),
                                             "/targets/java/api/v1/resources/" + chain + "/runs?from="
                                                     + Instant.now().minusSeconds(60) + "&until="
                                                     + Instant.now().plusSeconds(60),
                                             "GET", null, EDITOR));
                        String run = runs.path("items").get(0).path("id").asText();
                        assertThat(runs.path("items").get(0).path("provenance").path("fixture"))
                                .isEqualTo(tested.path("run").path("fixture"));
                        assertThat(runs.path("items").get(0).path("provenance").path("actor").asText())
                                .isEqualTo("demo-editor");
                        assertThat(call(studio.port(), "/targets/java/api/v1/runs/" + run + "/events", "GET", null,
                                        EDITOR)
                                .statusCode()).isEqualTo(501);
                        assertThat(call(studio.port(), "/targets/java/api/v1/runs/" + run, "GET", null, EDITOR)
                                .statusCode()).isEqualTo(200);
                        assertThat(call(studio.port(), "/", "GET", null, EDITOR).body()).contains("gear4j-targets");
                        assertThat(call(studio.port(), "/studio-targets.mjs", "GET", null, EDITOR).statusCode())
                                .isEqualTo(200);
                        assertThat(call(studio.port(), "/targets/java/api/v1/../../context", "GET", null, EDITOR)
                                .statusCode()).isEqualTo(400);
                        assertThat(call(studio.port(), "/targets/java/api/v1/%63ontext", "GET", null, EDITOR)
                                .statusCode()).isEqualTo(400);
                        assertThat(call(studio.port(), "/studio-simulation.mjs", "GET", null, EDITOR).statusCode())
                                .isEqualTo(200);
                        assertThat(call(javaHost.port(), "/studio-simulation.mjs", "GET", null, EDITOR).statusCode())
                                .isEqualTo(200);
                        assertThat(call(springHost.port(), "/studio-simulation.mjs", "GET", null, EDITOR).statusCode())
                                .isEqualTo(200);
                        // P6 routes use the same contract through standalone on Java and Spring.
                        for (String destination : List.of("java", "spring")) {
                            String domainId = (destination.equals("java") ? first : second).path("resource").path("id")
                                    .asText();
                            String base = "/targets/" + destination + "/api/v1";
                            var simChain = json(call(studio.port(), base + "/chains", "POST",
                                                     Map.of("domainId", domainId, "key", "simulation", "name",
                                                            "Simulation", "description", "", "runtimeAssemblyLineId",
                                                            "simulation"),
                                                     EDITOR))
                                    .path("resource").path("id").asText();
                            var simDefinition = new io.github.gear4jtest.studio.model.ChainDefinition(1, "simulation",
                                    "java.lang.String", "java.lang.String", DemoWiring.sample().nodes());
                            var simDraft = json(call(studio.port(), base + "/drafts", "POST",
                                                     Map.of("chainId", simChain, "name", "Simulation", "definition",
                                                            DefinitionJson.write(simDefinition)),
                                                     EDITOR))
                                    .path("draft").path("id").asText();
                            var simData = Map.of("input", Map.of("kind", "SYNTHETIC_TEXT", "value", "  hello  "),
                                                 "simulation", Map.of("schemaVersion", 1, "mocks", Map.of(
                                                                                                          "trim",
                                                                                                          Map.of("capability",
                                                                                                                 "text.trim",
                                                                                                                 "behavior",
                                                                                                                 "RETURN",
                                                                                                                 "type",
                                                                                                                 "STRING",
                                                                                                                 "value",
                                                                                                                 "hello"),
                                                                                                          "uppercase",
                                                                                                          Map.of("capability",
                                                                                                                 "text.uppercase",
                                                                                                                 "behavior",
                                                                                                                 "RETURN",
                                                                                                                 "type",
                                                                                                                 "STRING",
                                                                                                                 "value",
                                                                                                                 "HELLO"))));
                            var simScenario = json(call(studio.port(), base + "/resources/" + simDraft + "/scenarios",
                                                        "POST",
                                                        Map.of("name", "Simulation", "data", simData), EDITOR))
                                    .path("scenario").path("id").asText();
                            var simTarget = Map.of("draftId", simDraft, "revision", 1);
                            assertThat(json(call(studio.port(), base + "/scenarios/" + simScenario + "/simulation-plan",
                                                 "POST",
                                                 Map.of("revision", 1, "target", simTarget), EDITOR))
                                    .path("valid").asBoolean()).isTrue();
                            String simId = UUID.randomUUID().toString();
                            var simulated = json(call(studio.port(),
                                                      base + "/scenarios/" + simScenario + "/simulations", "POST",
                                                      Map.of("revision", 1, "target", simTarget, "requestId", simId),
                                                      EDITOR));
                            assertThat(simulated.path("result").path("outcome").asText()).isEqualTo("SUCCEEDED");
                            assertThat(json(call(studio.port(), base + "/simulations/" + simId, "GET", null, EDITOR)))
                                    .isEqualTo(simulated);
                            assertThat(json(call(studio.port(), base + "/scenarios/" + simScenario + "/simulations",
                                                 "GET", null, EDITOR))
                                    .path("items")).hasSize(1);
                        }
                        // Then: backend binding headers also reject mismatches before any management
                        // mutation.
                        var denied = client.send(HttpRequest
                                .newBuilder(URI.create("http://127.0.0.1:" + javaHost.port() + "/api/v1/domains"))
                                .header("Authorization", "Bearer " + EDITOR).header("Content-Type", "application/json")
                                .header(StudioManagementHttpHandler.EXPECTED_CONTEXT, "wrong")
                                .POST(HttpRequest.BodyPublishers
                                        .ofString("{\"key\":\"denied\",\"name\":\"Denied\",\"description\":\"\"}"))
                                .build(), HttpResponse.BodyHandlers.ofString());
                        assertThat(denied.statusCode()).isEqualTo(409);
                        var staleBrowser = client.send(HttpRequest
                                .newBuilder(URI
                                        .create("http://127.0.0.1:" + studio.port() + "/targets/java/api/v1/domains"))
                                .header("Authorization", "Bearer " + EDITOR).header("Content-Type", "application/json")
                                .header(StudioManagementHttpHandler.EXPECTED_CONTEXT, "old-context")
                                .POST(HttpRequest.BodyPublishers
                                        .ofString("{\"key\":\"stale\",\"name\":\"Stale\",\"description\":\"\"}"))
                                .build(), HttpResponse.BodyHandlers.ofString());
                        assertThat(staleBrowser.statusCode()).isEqualTo(409);
                        assertThat(staleBrowser.body()).contains("REMOTE_CONTEXT_MISMATCH");
                        assertThat(json(call(studio.port(), "/targets/java/api/v1/domains", "GET", null, EDITOR))
                                .path("items")).hasSize(1);
                    }
                }
            }
        }
    }

    @Test
    void mismatchedContextSubjectProtocolAndRevokedTargetCannotDispatchMutations() throws Exception {
        // Given
        var writes = new AtomicInteger();
        try (var host = new FakeHost(exchange -> {
            if (exchange.getRequestMethod().equals("POST"))
                writes.incrementAndGet();
            respond(exchange, 200, "{\"context\":{\"id\":\"runtime\"},\"identity\":{\"principalId\":\"other\"}}",
                    "runtime",
                    true);
        })) {
            var allowed = new AtomicBoolean(true);
            var target = target(host.port(), "runtime");
            var directory = new TargetDirectory(List.of(new StudioApplication("app", "App")), List.of(target),
                    (a, t) -> allowed.get());
            try (var remote = new HttpRemoteManagement(directory, (a, t, supplied) -> supplied)) {
                assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/domains", null,
                                                         "{}".getBytes()))
                        .hasMessage("REMOTE_IDENTITY_MISMATCH");
                allowed.set(false);
                assertThat(directory.visible(ACTOR)).isEmpty();
                assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/domains", null,
                                                         "{}".getBytes()))
                        .hasMessage("TARGET_UNAVAILABLE");
            }
            for (String context : List.of("wrong", "runtime")) {
                var wrong = new TargetDirectory(List.of(new StudioApplication("app", "App")),
                        List.of(target(host.port(), context)), (a, t) -> true);
                try (var remote = new HttpRemoteManagement(wrong, (a, t, supplied) -> supplied)) {
                    assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/domains", null,
                                                             "{}".getBytes()))
                            .isInstanceOf(RemoteProblem.class);
                }
            }
            assertThat(writes).hasValue(0);
        }
        try (var legacy = new FakeHost(e -> respond(e, 200, "{}", "runtime", false));
                var remote = remote(legacy.port(), Duration.ofSeconds(2))) {
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/domains", null,
                                                     "{}".getBytes()))
                    .hasMessage("REMOTE_PROTOCOL_MISMATCH");
        }
    }

    @Test
    void mutationTimeoutIsUnknownNeverReplayedAndResponseBodyIsBounded() throws Exception {
        // Given: the target has accepted the action, but its answer is delayed.
        var writes = new AtomicInteger();
        var release = new CountDownLatch(1);
        try (var host = new FakeHost(e -> {
            if (e.getRequestURI().getPath().endsWith("/context")) {
                context(e);
                return;
            }
            writes.incrementAndGet();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
            }
            respond(e, 201, "{}", "runtime", true);
        }); var remote = remote(host.port(), Duration.ofMillis(250))) {
            // When / Then
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/domains", null,
                                                     "{}".getBytes()))
                    .hasMessage("REMOTE_OUTCOME_UNKNOWN");
            assertThat(writes).hasValue(1);
            release.countDown();
        } finally {
            release.countDown();
        }
        try (var host = new FakeHost(e -> {
            if (e.getRequestURI().getPath().endsWith("/context"))
                context(e);
            else
                respond(e, 200, "x".repeat(1048577), "runtime", true);
        }); var remote = remote(host.port(), Duration.ofSeconds(2))) {
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "GET", "/api/v1/catalog", null,
                                                     new byte[0]))
                    .hasMessage("REMOTE_UNAVAILABLE");
        }
    }

    @Test
    void droppedPutResponseCannotCauseASecondMutationBodyToBeSent() throws Exception {
        // Given: PUT is normally considered retryable by HTTP clients, but this is a
        // management revision.
        var bodies = new AtomicInteger();
        try (var host = new FakeHost(e -> {
            if (e.getRequestURI().getPath().endsWith("/context"))
                context(e);
            else {
                if (new String(e.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).equals("{}"))
                    bodies.incrementAndGet();
                e.close(); // Ambiguous disconnect after receiving the complete body.
            }
        }); var remote = remote(host.port(), Duration.ofSeconds(2))) {
            // When / Then
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "PUT",
                                                     "/api/v1/drafts/" + UUID.randomUUID(), null, "{}".getBytes()))
                    .hasMessage("REMOTE_OUTCOME_UNKNOWN");
            assertThat(bodies).hasValue(1);
        }
    }

    @Test
    void redirectsNeverReceiveCredentialsAndArbitraryProxyPathsAreRejected() throws Exception {
        // Given
        var redirected = new AtomicInteger();
        try (var destination = new FakeHost(e -> {
            redirected.incrementAndGet();
            context(e);
        });
                var host = new FakeHost(e -> {
                    e.getResponseHeaders().set("Location",
                                               "http://127.0.0.1:" + destination.port() + "/api/v1/context");
                    respond(e, 302, "{}", "runtime", true);
                });
                var remote = remote(host.port(), Duration.ofSeconds(2))) {
            // When / Then
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "GET", "/api/v1/catalog", null,
                                                     new byte[0]))
                    .hasMessage("REMOTE_UNAVAILABLE");
            assertThat(redirected).hasValue(0);
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/admin", null,
                                                     "{}".getBytes()))
                    .hasMessage("INVALID_REQUEST");
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "GET", "/api/v1/catalog", "x=#fragment",
                                                     new byte[0]))
                    .hasMessage("INVALID_REQUEST");
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/domains", null,
                                                     new byte[65537]))
                    .hasMessage("INVALID_REQUEST");
        }
    }

    @Test
    void slowCredentialProviderIsContainedAndCallerHttpExecutorRemainsOwnedByCaller() throws Exception {
        // Given: a stalled provider on target slow must not exhaust the other target.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var caller = java.util.concurrent.Executors.newSingleThreadExecutor();
        var io = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var host = new FakeHost(StandaloneStudioTest::context)) {
            var directory = new TargetDirectory(List.of(new StudioApplication("app", "App")),
                    List.of(target(host.port(), "runtime"), new StudioTarget("slow", "app", "Slow", "slow-context",
                            URI.create("http://127.0.0.1:" + host.port()))),
                    (a, t) -> true);
            var injected = HttpClient.newBuilder().executor(io).build();
            try (var remote = new HttpRemoteManagement(directory, (a, t, token) -> {
                if (t.id().equals("slow")) {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    throw new IllegalStateException("Safe provider rejection");
                }
                return token;
            }, Duration.ofSeconds(2), 2, 1, injected)) {
                var slow = caller.submit(() -> assertThatThrownBy(() -> remote.exchange(ACTOR, "slow", EDITOR,
                                                                                        "GET", "/api/v1/context", null,
                                                                                        new byte[0]))
                        .hasMessage("REMOTE_AUTH_UNAVAILABLE"));
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                // When / Then
                assertThatThrownBy(() -> remote.exchange(ACTOR, "slow", EDITOR, "GET", "/api/v1/context", null,
                                                         new byte[0]))
                        .hasMessage("CAPACITY_EXCEEDED");
                assertThat(remote.exchange(ACTOR, "target", EDITOR, "GET", "/api/v1/context", null, new byte[0])
                        .status()).isEqualTo(200);
                release.countDown();
                slow.get(3, TimeUnit.SECONDS);
                // Failed credential lookups release both permits.
                assertThat(remote.exchange(ACTOR, "target", EDITOR, "GET", "/api/v1/context", null, new byte[0])
                        .status()).isEqualTo(200);
            }
            assertThat(io.isShutdown()).isFalse();
            assertThatThrownBy(() -> new HttpRemoteManagement(directory, (a, t, token) -> token,
                    Duration.ofSeconds(2), 2, 1,
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            release.countDown();
            caller.shutdownNow();
            io.shutdownNow();
        }
    }

    private static JdbcDataSource database() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        return source;
    }

    private static StudioTarget target(int port, String context) {
        return new StudioTarget("target", "app", "Target", context, URI.create("http://127.0.0.1:" + port));
    }

    private static HttpRemoteManagement remote(int port, Duration timeout) {
        return new HttpRemoteManagement(new TargetDirectory(List.of(new StudioApplication("app", "App")),
                List.of(target(port, "runtime")), (a, t) -> true), (a, t, token) -> token, timeout, 1);
    }

    private static StandaloneConfiguration configuration(int javaPort, int springPort) throws Exception {
        String config = """
                port=0
                applications=examples
                application.examples.name=Examples
                targets=java,spring
                target.java.application=examples
                target.java.name=Java
                target.java.context=studio-local.java
                target.java.endpoint=http://127.0.0.1:%d
                target.spring.application=examples
                target.spring.name=Spring
                target.spring.context=studio-local.spring
                target.spring.endpoint=http://127.0.0.1:%d
                users=editor,viewer
                user.editor.principalId=demo-editor
                user.editor.tokenEnvironment=EDITOR
                user.editor.targets=java,spring
                user.editor.remoteTokenEnvironment.java=EDITOR
                user.editor.remoteTokenEnvironment.spring=EDITOR
                user.viewer.principalId=demo-viewer
                user.viewer.tokenEnvironment=VIEWER
                user.viewer.targets=java
                user.viewer.remoteTokenEnvironment.java=VIEWER
                """.formatted(javaPort, springPort);
        return StandaloneConfiguration
                .load(new java.io.ByteArrayInputStream(config.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                      key -> Map.of("EDITOR", EDITOR, "VIEWER", VIEWER).get(key));
    }

    private HttpResponse<String> call(int port, String path, String method, Object body, String token)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(30));
        if (body != null)
            request.header("Content-Type", "application/json");
        return client.send(request
                .method(method,
                        body == null ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(StudioJson.mapper().writeValueAsBytes(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return StudioJson.mapper().readTree(response.body());
    }

    private static void context(HttpExchange e) throws java.io.IOException {
        respond(e, 200, "{\"context\":{\"id\":\"runtime\"},\"identity\":{\"principalId\":\"demo-editor\"}}", "runtime",
                true);
    }

    private static void respond(HttpExchange e, int status, String body, String context, boolean protocol)
            throws java.io.IOException {
        try (e) {
            e.getResponseHeaders().set("Content-Type", "application/json");
            if (protocol)
                e.getResponseHeaders().set(StudioManagementHttpHandler.PROTOCOL, "2");
            e.getResponseHeaders().set(StudioManagementHttpHandler.CONTEXT, context);
            var bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            e.sendResponseHeaders(status, bytes.length);
            e.getResponseBody().write(bytes);
        }
    }

    private static final class FakeHost implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
                .newFixedThreadPool(2);

        FakeHost(com.sun.net.httpserver.HttpHandler handler) throws Exception {
            server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 8);
            server.setExecutor(executor);
            server.createContext("/", handler);
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
