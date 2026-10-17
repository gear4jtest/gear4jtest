package io.github.gear4jtest.studio.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.json.DefinitionJson;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.StudioRunRecorder;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.model.StudioRun;
import io.github.gear4jtest.studio.runtime.LocalGear4jRuntime;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StudioHttpServerTest {
    private static final String EDITOR = "synthetic-editor-token-0123456789";
    private static final String VIEWER = "synthetic-viewer-token-0123456789";
    private final ObjectMapper mapper = StudioJson.mapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private StudioHttpServer server;
    private LocalGear4jRuntime runtime;
    private Connection lifetime;
    private DemoPublicationHost publications;
    private JdbcDataSource source;

    @BeforeEach
    void open() throws Exception {
        var ds = new JdbcDataSource();
        source = ds;
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        lifetime = ds.getConnection();
        var resources = DemoWiring.plainResources();
        runtime = DemoWiring.runtime("http", DemoWiring.plainExecutor(resources), resources,
                                     DataCapturePolicy.allowed());
        var actors = new RequestActorContext();
        publications = new DemoPublicationHost(ds, DemoWiring.catalog());
        var service = DemoDatabase.service(ds, runtime, actors, publications.publisher());
        server = new StudioHttpServer(service, actors, 0, EDITOR, VIEWER);
        server.start();
    }

    @AfterEach
    void close() throws Exception {
        try {
            if (server != null)
                server.close();
            if (publications != null)
                publications.close();
        } finally {
            try {
                if (runtime != null)
                    runtime.close();
            } finally {
                if (lifetime != null)
                    lifetime.close();
            }
        }
    }

    private HttpResponse<String> send(String path, String method, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));
        if (token != null)
            request.header("Authorization", "Bearer " + token);
        if (body != null)
            request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode post(String path, Object body) throws Exception {
        var response = send("/api/v1" + path, "POST", EDITOR, body);
        assertThat(response.statusCode()).withFailMessage(response.body()).isBetween(200, 201);
        return mapper.readTree(response.body());
    }

    private JsonNode createDraft() throws Exception {
        var domain = post("/domains", Map.of("key", "catalog", "name", "Catalogue", "description", ""));
        var chain = post("/chains", Map.of("domainId", domain.path("resource").path("id").asText(), "key", "vehicles",
                                           "name", "Véhicules", "description", "", "runtimeAssemblyLineId",
                                           "text_preparation"));
        return post("/drafts", Map.of("chainId", chain.path("resource").path("id").asText(), "name", "Draft",
                                      "definition", DefinitionJson.write(DemoWiring.sample())));
    }

    @Test
    void scenarioRoutesPreserveRevisionsAndTestTheExactFrozenArtifact() throws Exception {
        // Given
        var draft = createDraft();
        String draftId = draft.path("draft").path("id").asText();
        String chainId = draft.path("draft").path("chainId").asText();
        var data = Map.of("input", Map.of("kind", "FIXTURE_REFERENCE", "value", "demo/greeting", "fixtureVersion", "1"),
                          "expected", Map.of("outcome", "SUCCEEDED", "output", "HELLO"));
        var scenario = post("/resources/" + chainId + "/scenarios", Map.of("name", "Greeting", "data", data));
        String id = scenario.path("scenario").path("id").asText();
        var version = post("/drafts/" + draftId + "/versions", Map.of("revision", 1, "label", "1"));
        String versionId = version.path("version").path("id").asText();
        var changed = new ChainDefinition(1, "text_preparation", "java.lang.String", "java.lang.String",
                List.of(new ChainDefinition.Operation("trim", "text.trim", Map.of())));
        assertThat(send("/api/v1/drafts/" + draftId, "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "definition", DefinitionJson.write(changed)))
                .statusCode()).isEqualTo(200);
        // When / Then: assertions use the frozen artifact, and reference values are not
        // captured.
        var result = post("/scenarios/" + id + "/tests", Map.of("revision", 1, "target", Map.of("versionId", versionId),
                                                                "requestId", UUID.randomUUID()));
        assertThat(result.path("run").path("scenarioCheck").path("status").asText()).isEqualTo("PASSED");
        assertThat(result.path("run").path("scenario").path("revisionId").asText())
                .isEqualTo(scenario.path("revision").path("id").asText());
        assertThat(result.path("run").path("input").path("state").asText()).isEqualTo("NOT_CAPTURED");
        var next = send("/api/v1/scenarios/" + id, "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "name", "Next", "data", data));
        assertThat(next.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(next.body()).path("revision").path("number").asInt()).isEqualTo(2);
        assertThat(send("/api/v1/scenarios/" + id + "/revisions", "GET", EDITOR, null).statusCode()).isEqualTo(200);
        assertThat(send("/api/v1/resources/" + chainId + "/scenarios", "GET", EDITOR, null).statusCode())
                .isEqualTo(200);
        assertThat(send("/api/v1/scenarios/" + id, "GET", VIEWER, null).statusCode()).isEqualTo(404);
        assertThat(send("/api/v1/scenarios/" + id + "/tests", "POST", EDITOR,
                        Map.of("revision", 1, "target", Map.of("versionId", versionId), "requestId", UUID.randomUUID(),
                               "input", "secret"))
                .statusCode()).isEqualTo(400);
        var archived = post("/scenarios/" + id + "/archive", Map.of("expectedVersion", 2));
        assertThat(archived.path("scenario").path("archived").asBoolean()).isTrue();
        assertThat(send("/studio-scenarios.mjs", "GET", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void runRoutesFilterDurableSnapshotsAndNeverExposePayloadsWithoutObservabilityPermission() throws Exception {
        // Given
        var draft = createDraft();
        String scope = draft.path("draft").path("id").asText();
        post("/drafts/" + scope + "/tests",
             Map.of("revision", 1, "requestId", UUID.randomUUID(), "input", "synthetic-input"));
        String window = "?from=" + Instant.now().minusSeconds(60) + "&until=" + Instant.now().plusSeconds(60);
        String path = "/api/v1/resources/" + scope;
        // When / Then
        assertThat(send("/studio-runs.mjs", "GET", null, null).statusCode()).isEqualTo(200);
        assertThat(send(path + "/runs", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send(path + "/runs" + window + "&actor=forged", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send(path + "/runs" + window + "&mode=INVALID", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send(path + "/runs?from=2026-01-01T00:00:00Z&until=2026-03-01T00:00:00Z", "GET", EDITOR, null)
                .statusCode()).isEqualTo(400);
        var listing = send(path + "/runs" + window + "&status=SUCCEEDED&mode=TEST", "GET", EDITOR, null);
        assertThat(listing.statusCode()).isEqualTo(200);
        assertThat(listing.body()).doesNotContain("synthetic-input", "SYNTHETIC-INPUT");
        String id = mapper.readTree(listing.body()).path("items").get(0).path("id").asText();
        assertThat(send("/api/v1/runs/" + id, "GET", VIEWER, null).statusCode()).isEqualTo(404);
        assertThat(send(path + "/access", "PUT", EDITOR, Map.of("expectedVersion", 1, "policy",
                                                                Map.of("inheritAccess", false, "grants",
                                                                       List.of(Map.of("subjectType", "USER", "subject",
                                                                                      "demo-viewer", "permissions",
                                                                                      List.of("READ", "READ_RUNS"))))))
                .statusCode()).isEqualTo(200);
        var metadata = send("/api/v1/runs/" + id, "GET", VIEWER, null);
        assertThat(metadata.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(metadata.body()).path("observation").isNull()).isTrue();
        assertThat(send(path + "/metrics" + window, "GET", VIEWER, null).statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(send(path + "/metrics" + window, "GET", EDITOR, null).body()).path("total").asInt())
                .isEqualTo(1);
        var stream = send("/api/v1/runs/" + id + "/events", "GET", EDITOR, null);
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("content-type")).contains("text/event-stream; charset=utf-8");
        assertThat(stream.body()).contains("event: snapshot", "SYNTHETIC-INPUT").doesNotContain("\nid:");
        assertThat(send("/api/v1/runs/" + id + "/events?token=synthetic", "GET", EDITOR, null).statusCode())
                .isEqualTo(400);
    }

    @Test
    void ongoingSnapshotStreamRechecksAclAndClosesWhenAccessIsRevoked() throws Exception {
        // Given: a host run still in preparation, with a viewer explicitly authorized.
        var draft = createDraft();
        String chain = draft.path("draft").path("chainId").asText();
        var recorder = new StudioRunRecorder(new JdbcStudioStore(source), runtime.context().id(), Clock.systemUTC());
        var id = recorder.beginHostRun(UUID
                .fromString(chain), new StudioRun.Provenance(runtime.context().id(), "text_preparation",
                        "build", runtime.context().catalogFingerprint(), null, "application", "RUN", null, null, null,
                        0, null, null));
        String access = "/api/v1/resources/" + chain + "/access";
        assertThat(send(access, "PUT", EDITOR, Map.of("expectedVersion", 1, "policy",
                                                      Map.of("inheritAccess", true, "grants",
                                                             List.of(Map.of("subjectType", "USER", "subject",
                                                                            "demo-viewer",
                                                                            "permissions",
                                                                            List.of("READ", "READ_RUNS",
                                                                                    "READ_OBSERVABILITY"))))))
                .statusCode()).isEqualTo(200);
        var request = HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/v1/runs/" + id + "/events"))
                .header("Authorization", "Bearer " + VIEWER).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            assertThat(response.statusCode()).isEqualTo(200);
            String line;
            do {
                line = reader.readLine();
            } while (line != null && !line.startsWith("data: "));
            assertThat(line).contains("PREPARING");
            // When
            assertThat(send(access, "PUT", EDITOR, Map.of("expectedVersion", 2,
                                                          "policy", Map.of("inheritAccess", true, "grants", List.of())))
                    .statusCode()).isEqualTo(200);
            // Then: at most one heartbeat interval, no further snapshot or provenance.
            String remainder = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            assertThat(remainder).contains("event: unavailable").doesNotContain("event: snapshot", "text_preparation");
        }
        assertThat(send("/api/v1/runs/" + id, "GET", VIEWER, null).statusCode()).isEqualTo(404);
    }

    @Test
    void versionHttpRoutesPublishPromoteCompareAndRejectForgedActors() throws Exception {
        // Given
        var draft = createDraft();
        String draftId = draft.path("draft").path("id").asText();
        assertThat(send("/studio-versions.mjs", "GET", null, null).statusCode()).isEqualTo(200);
        var first = post("/drafts/" + draftId + "/versions", Map.of("revision", 1, "label", "1.0.0"));
        String id = first.path("version").path("id").asText();
        // When / Then
        assertThat(send("/api/v1/versions/" + id, "GET", VIEWER, null).statusCode()).isEqualTo(404);
        assertThat(send("/api/v1/versions/" + id + "/publish", "POST", EDITOR, Map.of("actor", "forged")).statusCode())
                .isEqualTo(400);
        assertThat(post("/versions/" + id + "/publish", Map.of()).path("publications").get(0).path("state").asText())
                .isEqualTo("CONFIRMED");
        assertThat(post("/versions/" + id + "/tests", Map.of("requestId", UUID.randomUUID(), "input", " abc "))
                .path("run").path("versionId").asText()).isEqualTo(id);
        assertThat(post("/versions/" + id + "/promote", Map.of()).path("publications")).hasSize(2);
        assertThat(post("/versions/" + id + "/refresh", Map.of()).path("publications")).hasSize(2);
        var second = post("/drafts/" + draftId + "/versions", Map.of("revision", 1, "label", "2.0.0"));
        assertThat(post("/versions/" + id + "/compare",
                        Map.of("otherVersionId", second.path("version").path("id").asText()))
                .path("baseXml").asText()).isEqualTo(first.path("version").path("source").asText());
        String chainId = draft.path("draft").path("chainId").asText();
        assertThat(mapper.readTree(send("/api/v1/chains/" + chainId + "/versions", "GET", EDITOR, null).body())
                .path("items")).hasSize(2);
    }

    @Test
    void invalidXmlIsAnExactDurableSourceAndRequiresExplicitStructuredRecovery() throws Exception {
        // Given
        var initial = createDraft();
        String id = initial.path("draft").path("id").asText();
        String xml = post("/resources/" + id + "/export",
                          Map.of("definition", initial.path("revision").path("definition")))
                .path("source").asText();
        String broken = xml.replace("</assemblyLine>", "</assemblyL");
        // When
        var saved = send("/api/v1/drafts/" + id + "/source", "PUT", EDITOR,
                         Map.of("expectedVersion", 1, "mediaType", "application/xml", "source", broken));
        assertThat(saved.statusCode()).withFailMessage(saved.body()).isEqualTo(200);
        var restored = mapper.readTree(send("/api/v1/drafts/" + id, "GET", EDITOR, null).body());
        // Then
        assertThat(restored.path("revision").path("kind").asText()).isEqualTo("SOURCE");
        assertThat(restored.path("revision").path("source").path("text").asText()).isEqualTo(broken);
        assertThat(restored.path("revision").path("definition").isNull()).isTrue();
        assertThat(send("/api/v1/drafts/" + id + "/tests", "POST", EDITOR,
                        Map.of("revision", 2, "requestId", UUID.randomUUID(), "input", "synthetic"))
                .body())
                .contains("SOURCE_REQUIRES_ANALYSIS");
        assertThat(send("/api/v1/drafts/" + id + "/source", "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "mediaType", "application/xml", "source", "<assemblyLine>"))
                .statusCode()).isEqualTo(409);
        String forbiddenTail = broken + "<valueParameter retriever=\"unknown\" value=\"synthetic-secret\"/>";
        var denied = send("/api/v1/drafts/" + id + "/source", "PUT", EDITOR,
                          Map.of("expectedVersion", 2, "mediaType", "application/xml", "source", forbiddenTail));
        assertThat(denied.body()).contains("SOURCE_NOT_ADMITTED").doesNotContain("synthetic-secret");
        assertThat(mapper.readTree(send("/api/v1/drafts/" + id, "GET", EDITOR, null).body())
                .path("draft").path("head").asInt()).isEqualTo(2);
        var comparison = post("/drafts/" + id + "/compare", Map.of("fromRevision", 1, "toRevision", 2));
        assertThat(comparison.path("diff").isNull()).isTrue();
        assertThat(comparison.path("proposedXml").asText()).isEqualTo(broken);
        var fork = post("/drafts/" + id + "/fork", Map.of("revision", 2, "name", "Source copy"));
        assertThat(fork.path("revision").path("source").path("text").asText()).isEqualTo(broken);
        // A successful preview does not write; only this explicit save creates
        // STRUCTURED revision 3.
        var preview = post("/resources/" + id + "/xml-preview", Map.of("source", xml));
        var structured = send("/api/v1/drafts/" + id, "PUT", EDITOR,
                              Map.of("expectedVersion", 2, "definition", preview.path("definition")));
        assertThat(structured.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(structured.body()).path("revision").path("kind").asText()).isEqualTo("STRUCTURED");
        assertThat(post("/drafts/" + id + "/tests",
                        Map.of("revision", 3, "requestId", UUID.randomUUID(), "input", " electric "))
                .path("run").path("outcome").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    void durableHttpAuthoringRunsThePinnedRevisionAndRetrievesItsAudit() throws Exception {
        // Given
        assertThat(send("/api/v1/catalog", "GET", null, null).statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(send("/api/v1/catalog", "GET", EDITOR, null).body())).hasSize(7);
        var created = createDraft();
        String id = created.path("draft").path("id").asText();
        var definition = created.path("revision").path("definition");
        // When
        assertThat(post("/resources/" + id + "/validate", Map.of("definition", definition)).path("valid")
                .booleanValue()).isTrue();
        String requestId = UUID.randomUUID().toString();
        var test = Map.of("requestId", requestId, "revision", 1, "input", "  electric  ");
        var result = post("/drafts/" + id + "/tests", test);
        // Then
        assertThat(result.path("run").path("output").path("value").asText()).isEqualTo("ELECTRIC");
        assertThat(result.path("run").path("revisionId")).isEqualTo(created.path("revision").path("revisionId"));
        assertThat(mapper.readTree(send("/api/v1/tests/" + requestId, "GET", EDITOR, null).body())).isEqualTo(result);
        assertThat(post("/drafts/" + id + "/tests", test)).isEqualTo(result);
        assertThat(send("/api/v1/drafts/" + id, "PUT", EDITOR, Map.of("expectedVersion", 0, "definition", definition))
                .statusCode()).isEqualTo(409);
        assertThat(send("/api/v1/drafts/" + id, "PUT", EDITOR, Map.of("expectedVersion", 1, "definition", definition))
                .statusCode()).isEqualTo(200);
        var revisions = mapper
                .readTree(send("/api/v1/drafts/" + id + "/revisions?limit=1", "GET", EDITOR, null).body());
        assertThat(revisions.path("items")).hasSize(1);
        assertThat(revisions.path("nextCursor").asText()).isEqualTo("1");
        var source = post("/resources/" + id + "/export", Map.of("definition", definition)).path("source").asText();
        var imported = post("/import", Map.of("chainId", created.path("draft").path("chainId").asText(), "name",
                                              "Import", "source", source));
        assertThat(imported.path("revision").path("definition"))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(definition)));
        var audit = mapper.readTree(send("/api/v1/resources/" + id + "/audit", "GET", EDITOR, null).body());
        assertThat(audit.path("items")).hasSize(3);
        assertThat(audit.toString()).doesNotContain("electric", "ELECTRIC");
    }

    @Test
    void accessChangesAreVersionedAndAnActorCannotBeForgedByTheBrowser() throws Exception {
        // Given
        var draft = createDraft();
        String id = draft.path("draft").path("id").asText();
        assertThat(send("/api/v1/drafts/" + id, "GET", VIEWER, null).statusCode()).isEqualTo(404);
        assertThat(mapper.readTree(send("/api/v1/drafts", "GET", VIEWER, null).body()).path("items")).isEmpty();
        var policy = Map
                .of("inheritAccess", false, "grants",
                    List.of(Map.of("subjectType", "USER", "subject", "demo-viewer", "permissions", List.of("READ"))));
        // When
        assertThat(send("/api/v1/resources/" + id + "/access", "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "policy", policy))
                .statusCode()).isEqualTo(200);
        // Then
        assertThat(send("/api/v1/drafts/" + id, "GET", VIEWER, null).statusCode()).isEqualTo(200);
        assertThat(send("/api/v1/drafts/" + id + "/tests", "POST", VIEWER,
                        Map.of("revision", 1, "requestId", UUID.randomUUID(), "input", "x"))
                .statusCode()).isEqualTo(403);
        assertThat(send("/api/v1/resources/" + id + "/access", "GET", VIEWER, null).statusCode()).isEqualTo(403);
        assertThat(send("/api/v1/resources/" + id + "/access", "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "policy", policy))
                .statusCode()).isEqualTo(409);
        assertThat(send("/api/v1/drafts", "POST", EDITOR,
                        Map.of("chainId", draft.path("draft").path("chainId").asText(), "name", "Forged", "definition",
                               DefinitionJson.write(DemoWiring.sample()), "actor", "admin"))
                .statusCode()).isEqualTo(400);
        assertThat(send("/api/drafts/" + id, "GET", EDITOR, null).statusCode()).isEqualTo(404);
        var access = mapper.readTree(send("/api/v1/resources/" + id + "/access", "GET", EDITOR, null).body());
        assertThat(access.path("version").asInt()).isEqualTo(2);
        assertThat(send("/api/v1/resources/" + id + "/access", "PUT", EDITOR,
                        Map.of("expectedVersion", 2, "policy", Map.of("inheritAccess", false, "grants", List.of())))
                .statusCode()).isEqualTo(200);
        assertThat(send("/api/v1/drafts/" + id, "GET", VIEWER, null).statusCode()).isEqualTo(404);
    }

    @Test
    void metadataForkArchiveAndScopedListsUseResourceVersions() throws Exception {
        // Given
        var draft = createDraft();
        String id = draft.path("draft").path("id").asText();
        String chainId = draft.path("draft").path("chainId").asText();
        // When
        var fork = post("/drafts/" + id + "/fork", Map.of("revision", 1, "name", "Alternative"));
        var renamed = send("/api/v1/resources/" + id, "PUT", EDITOR,
                           Map.of("expectedVersion", 1, "name", "Revue", "description", ""));
        // Then: metadata increments the optimistic version, not the definition
        // revision.
        assertThat(renamed.statusCode()).isEqualTo(200);
        var current = mapper.readTree(send("/api/v1/drafts/" + id, "GET", EDITOR, null).body());
        assertThat(current.path("draft").path("version").asInt()).isEqualTo(2);
        assertThat(current.path("revision").path("number").asInt()).isEqualTo(1);
        assertThat(fork.path("draft").path("baseRevisionId")).isEqualTo(draft.path("revision").path("revisionId"));
        var first = mapper.readTree(send("/api/v1/drafts?parent=" + chainId + "&limit=1", "GET", EDITOR, null).body());
        assertThat(first.path("items")).hasSize(1);
        var second = mapper.readTree(send("/api/v1/drafts?parent=" + chainId + "&limit=1&after="
                + first.path("nextCursor").asText(), "GET", EDITOR, null).body());
        assertThat(second.path("items")).hasSize(1);
        assertThat(first.path("items").get(0).path("id")).isNotEqualTo(second.path("items").get(0).path("id"));
        var archived = post("/resources/" + id + "/archive", Map.of("expectedVersion", 2));
        assertThat(archived.path("resource").path("abandoned").asBoolean()).isTrue();
        assertThat(send("/api/v1/drafts/" + id, "PUT", EDITOR,
                        Map.of("expectedVersion", 3, "definition", draft.path("revision").path("definition")))
                .statusCode()).isEqualTo(403);
        assertThat(mapper
                .readTree(send("/api/v1/resources/" + id + "/audit?limit=100", "GET", EDITOR, null).body())
                .path("items")).hasSize(3);
    }

    @Test
    void anXmlDocumentLargerThanOneParameterStillRoundTripsThroughHttp() throws Exception {
        // Given: the transport's string limit must permit a whole XML document, not
        // just one parameter.
        var draft = createDraft();
        var nodes = new ArrayList<ChainDefinition.Node>();
        for (int i = 0; i < 5; i++)
            nodes.add(new ChainDefinition.Operation("step" + i, "text.prefix",
                    Map.of("prefix", new ParameterValue(ParameterValue.Kind.TEXT, "x".repeat(3500)))));
        var definition = DefinitionJson
                .write(new ChainDefinition(1, "text_preparation", "java.lang.String", "java.lang.String", nodes));
        // When
        var source = post("/resources/" + draft.path("draft").path("id").asText() + "/export",
                          Map.of("definition", definition))
                .path("source").asText();
        assertThat(source.length()).isGreaterThan(16384);
        var imported = post("/import", Map.of("chainId", draft.path("draft").path("chainId").asText(), "name", "Import",
                                              "source", source));
        // Then
        assertThat(imported.path("revision").path("definition"))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(definition)));
    }

    @Test
    void typedAuthoringXmlPreviewDiffAndScopedDiagnosticsWorkTogether() throws Exception {
        // Given
        var draft = createDraft();
        var id = draft.path("draft").path("id").asText();
        var typed = new ChainDefinition(1, "text_preparation", "java.lang.String", "java.lang.String", List.of(
                                                                                                               new ChainDefinition.Operation(
                                                                                                                       "length",
                                                                                                                       "text.length",
                                                                                                                       Map.of()),
                                                                                                               new ChainDefinition.Operation(
                                                                                                                       "format",
                                                                                                                       "number.format",
                                                                                                                       Map.of())));
        var definition = DefinitionJson.write(typed);
        // When / Then: real generic contracts, compiler, XML adapter, persistence and
        // runtime agree.
        assertThat(post("/resources/" + id + "/diagnostics", Map.of("definition", definition)).path("valid")
                .asBoolean()).isTrue();
        assertThat(post("/resources/" + id + "/validate", Map.of("definition", definition)).path("valid").asBoolean())
                .isTrue();
        var difference = post("/drafts/" + id + "/diff", Map.of("revision", 1, "definition", definition));
        assertThat(difference.path("diff").path("changes")).isNotEmpty();
        var source = post("/resources/" + id + "/export", Map.of("definition", definition)).path("source").asText();
        var preview = post("/resources/" + id + "/xml-preview",
                           Map.of("source", source.replace("<operations>", "<!--normalized--> <operations>")));
        assertThat(preview.path("source").asText()).doesNotContain("normalized");
        assertThat(preview.path("definition")).isEqualTo(mapper.valueToTree(definition));
        assertThat(mapper.readTree(send("/api/v1/drafts/" + id, "GET", EDITOR, null).body()).path("draft").path("head")
                .asInt()).isEqualTo(1);
        assertThat(send("/api/v1/resources/" + id + "/xml-preview", "POST", VIEWER, Map.of("source", source))
                .statusCode()).isEqualTo(404);
        assertThat(send("/api/v1/drafts/" + id, "PUT", EDITOR, Map.of("expectedVersion", 1, "definition", definition))
                .statusCode()).isEqualTo(200);
        assertThat(post("/drafts/" + id + "/compare", Map.of("fromRevision", 1, "toRevision", 2)).path("diff")
                .path("changes"))
                .isEqualTo(difference.path("diff").path("changes"));
        assertThat(post("/drafts/" + id + "/tests",
                        Map.of("revision", 2, "requestId", UUID.randomUUID(), "input", "hello"))
                .path("run").path("output").path("value").asText()).isEqualTo("5");
        var integerInput = new ChainDefinition(1, "text_preparation", "java.lang.Integer", "java.lang.String",
                List.of(typed.nodes().get(1)));
        assertThat(post("/resources/" + id + "/validate", Map.of("definition", DefinitionJson.write(integerInput)))
                .path("valid").asBoolean()).isTrue();
        assertThat(send("/api/v1/drafts/" + id, "PUT", EDITOR,
                        Map.of("expectedVersion", 2, "definition", DefinitionJson.write(integerInput)))
                .statusCode()).isEqualTo(200);
        var unavailableTest = post("/drafts/" + id + "/tests",
                                   Map.of("revision", 3, "requestId", UUID.randomUUID(), "input", "5"));
        assertThat(unavailableTest.path("state").asText()).isEqualTo("PREPARATION_FAILED");
        assertThat(unavailableTest.path("errorCode").asText()).isEqualTo("UNSUPPORTED_CONTRACT");
        var incompatible = new ChainDefinition(1, "text_preparation", "java.lang.String", "java.lang.String",
                List.of(typed.nodes().get(1)));
        var report = post("/resources/" + id + "/diagnostics",
                          Map.of("definition", DefinitionJson.write(incompatible)));
        assertThat(report.path("valid").asBoolean()).isFalse();
        assertThat(report.path("diagnostics").get(0).path("code").asText()).isEqualTo("INCOMPATIBLE");
        assertThat(report.path("diagnostics").get(0).path("path").asText()).isEqualTo("/nodes/0");
    }

    @Test
    void hostileOriginsUnknownFieldsAndXmlEntitiesAreRejected() throws Exception {
        // Given
        var draft = createDraft();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/v1/catalog"))
                .header("Authorization", "Bearer " + EDITOR).header("Origin", "https://untrusted.example").build();
        // When / Then
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        var dangerous = send("/api/v1/import", "POST", EDITOR,
                             Map.of("chainId", draft.path("draft").path("chainId").asText(), "name", "XML", "source",
                                    "<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><x>&x;</x>"));
        assertThat(dangerous.statusCode()).isEqualTo(422);
        assertThat(dangerous.body()).doesNotContain("passwd", "root:");
        assertThat(send("/api/v1/domains?limit=1&limit=2", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send("/embedded", "GET", null, null).body()).contains("gear4j-workspace", "Portail");
        assertThat(send("/studio-element.mjs", "GET", null, null).statusCode()).isEqualTo(200);
    }
}
