package io.github.gear4jtest.studio.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.studio.json.DefinitionJson;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ParameterValue;
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

    @BeforeEach
    void open() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        lifetime = ds.getConnection();
        var resources = DemoWiring.plainResources();
        runtime = DemoWiring.runtime("http", DemoWiring.plainExecutor(resources), resources,
                                     DataCapturePolicy.allowed());
        var actors = new RequestActorContext();
        var service = DemoDatabase.service(ds, runtime, actors);
        server = new StudioHttpServer(service, actors, 0, EDITOR, VIEWER);
        server.start();
    }

    @AfterEach
    void close() throws Exception {
        try {
            if (server != null)
                server.close();
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
    void servesDemoPagesAndCanonicalStudioWebModules() throws Exception {
        // Given: demo-local pages depend on the shared Studio Web ES modules.
        for (String path : List.of("/", "/embedded", "/studio.css", "/bootstrap.mjs",
                                   "/studio-client.mjs", "/management-client.mjs", "/studio-element.mjs",
                                   "/studio-workspace.mjs")) {
            // When
            var response = send(path, "GET", null, null);
            // Then
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path).isNotBlank();
        }
        // The current shared transport uses /api/v1; the old copied StudioClient used
        // /api.
        assertThat(send("/studio-client.mjs", "GET", null, null).body()).contains("/api/v1");
        assertThat(send("/management-client.mjs", "GET", null, null).body())
                .contains("StudioTransport");
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
