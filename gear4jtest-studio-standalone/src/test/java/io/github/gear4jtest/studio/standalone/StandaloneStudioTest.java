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
            respond(exchange, 200, "{\"context\":{\"id\":\"runtime\"},\"identity\":{\"subject\":\"other\"}}", "runtime",
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
            // The request body must actually have reached the target before
            // the response is delayed: a preflight timeout is not ambiguous.
            e.getRequestBody().readAllBytes();
            writes.incrementAndGet();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
            }
            respond(e, 201, "{}", "runtime", true);
        }); var remote = remote(host.port(), Duration.ofSeconds(2))) {
            // When / Then: allow the identity/context handshake to complete,
            // but always time out waiting for the deliberately blocked mutation.
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
    void contextTimeoutDoesNotDispatchMutationOrReportUnknownOutcome() throws Exception {
        // Given: the identity/context handshake never completes, so no write
        // can have been dispatched to the target.
        var writes = new AtomicInteger();
        var release = new CountDownLatch(1);
        try (var host = new FakeHost(e -> {
            if (e.getRequestURI().getPath().endsWith("/context")) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    return;
                }
                context(e);
            } else {
                writes.incrementAndGet();
                respond(e, 201, "{}", "runtime", true);
            }
        }); var remote = remote(host.port(), Duration.ofMillis(250))) {
            // When / Then: a failed preflight is an unavailable target, not
            // evidence that a mutation may have happened.
            assertThatThrownBy(() -> remote.exchange(ACTOR, "target", EDITOR, "POST", "/api/v1/domains", null,
                                                     "{}".getBytes()))
                    .hasMessage("REMOTE_UNAVAILABLE");
            assertThat(writes).hasValue(0);
        } finally {
            release.countDown();
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
                user.editor.subject=demo-editor
                user.editor.tokenEnvironment=EDITOR
                user.editor.targets=java,spring
                user.editor.remoteTokenEnvironment.java=EDITOR
                user.editor.remoteTokenEnvironment.spring=EDITOR
                user.viewer.subject=demo-viewer
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
        respond(e, 200, "{\"context\":{\"id\":\"runtime\"},\"identity\":{\"subject\":\"demo-editor\"}}", "runtime",
                true);
    }

    private static void respond(HttpExchange e, int status, String body, String context, boolean protocol)
            throws java.io.IOException {
        try (e) {
            e.getResponseHeaders().set("Content-Type", "application/json");
            if (protocol)
                e.getResponseHeaders().set(StudioManagementHttpHandler.PROTOCOL, "1");
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
