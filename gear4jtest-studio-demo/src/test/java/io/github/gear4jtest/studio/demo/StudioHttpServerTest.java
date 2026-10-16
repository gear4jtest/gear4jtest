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
                                           "name", "Véhicules", "description", ""));
        return post("/drafts", Map.of("chainId", chain.path("resource").path("id").asText(), "name", "Draft",
                                      "definition", DefinitionJson.write(DemoWiring.sample())));
    }

    @Test
    void durableHttpAuthoringRunsThePinnedRevisionAndRetrievesItsAudit() throws Exception {
        // Given
        assertThat(send("/api/v1/catalog", "GET", null, null).statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(send("/api/v1/catalog", "GET", EDITOR, null).body())).hasSize(5);
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
                .readTree(send("/api/v1/resources/" + id + "/audit?after=0&limit=100", "GET", EDITOR, null).body())
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
                .write(new ChainDefinition(1, "large", "java.lang.String", "java.lang.String", nodes));
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

    @Test
    void httpTransportRejectsMalformedJsonWrongContentTypesAndOversizedBodies() throws Exception {
        // Given: the request decoder must reject invalid requests before any
        // management operation can mutate durable state.
        assertThat(send("/api/v1/context", "GET", "invalid-token", null).statusCode()).isEqualTo(401);
        assertThat(send("/api/v1/context", "GET", null, null).statusCode()).isEqualTo(401);
        assertThat(send("/api/v1/context", "GET", VIEWER, null).statusCode()).isEqualTo(200);

        // When / Then
        assertThat(sendRaw("/api/v1/domains", "POST", null, "{}").statusCode()).isEqualTo(400);
        assertThat(sendRaw("/api/v1/domains", "POST", "text/plain", "{}").statusCode()).isEqualTo(400);
        assertThat(sendRaw("/api/v1/domains", "POST", "application/json", "").statusCode()).isEqualTo(400);
        assertThat(sendRaw("/api/v1/domains", "POST", "application/json", "{").statusCode()).isEqualTo(400);
        assertThat(sendRaw("/api/v1/domains", "POST", "application/json", "{\"a\":1} {\"b\":2}")
                .statusCode()).isEqualTo(400);
        assertThat(sendRaw("/api/v1/domains", "POST", "application/json",
                           "{\"name\":\"" + "x".repeat(65526) + "\"}")
                .statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(send("/api/v1/domains", "GET", EDITOR, null).body()).path("items"))
                .isEmpty();
    }

    @Test
    void queryValidationAndUnknownRoutesReturnSafeClientErrors() throws Exception {
        // Given / When / Then
        assertThat(send("/api/v1/domains?unexpected=1", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/domains?limit=NaN", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/domains?limit", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/domains?parent=1&after=2&limit=3&other=4", "GET", EDITOR, null)
                .statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/resources/not-a-uuid", "GET", EDITOR, null).statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/not-an-endpoint", "GET", EDITOR, null).statusCode()).isEqualTo(404);
        assertThat(send("/api/v1/domains", "DELETE", EDITOR, null).statusCode()).isEqualTo(404);
        assertThat(send("/api/v1/domains", "POST", EDITOR, List.of()).statusCode()).isEqualTo(400);
    }

    @Test
    void accessPolicyAndDefinitionValidationRejectMalformedInputs() throws Exception {
        // Given
        var draft = createDraft();
        var id = draft.path("draft").path("id").asText();

        // When / Then
        assertThat(send("/api/v1/resources/" + id + "/access", "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "policy", Map.of("inheritAccess", true, "grants", "invalid")))
                .statusCode()).isEqualTo(400);
        var invalidGrant = Map.of("subjectType", "USER", "subject", "demo-viewer", "permissions",
                                  List.of("NON_EXISTENT_PERMISSION"));
        assertThat(send("/api/v1/resources/" + id + "/access", "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "policy", Map.of("inheritAccess", true, "grants",
                                                                      List.of(invalidGrant))))
                .statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/resources/" + id + "/access", "PUT", EDITOR,
                        Map.of("expectedVersion", 1, "policy", Map.of("inheritAccess", true, "grants",
                                                                      List.of(Map.of("subjectType", "USER", "subject",
                                                                                     "demo-viewer", "permissions",
                                                                                     List.of(123))))))
                .statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/resources/" + id, "PUT", EDITOR,
                        Map.of("expectedVersion", "one", "name", "Bad", "description", ""))
                .statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/resources/" + id + "/validate", "POST", EDITOR,
                        Map.of("definition", Map.of("schemaVersion", "invalid")))
                .statusCode()).isEqualTo(400);
        // Failed requests must not advance the optimistic resource version.
        assertThat(mapper.readTree(send("/api/v1/resources/" + id, "GET", EDITOR, null).body())
                .path("resource").path("version").asInt()).isEqualTo(1);
    }

    @Test
    void staticAssetsAreReadOnlyAndUnknownPathsAreNotServed() throws Exception {
        // Given / When / Then
        assertThat(send("/", "GET", null, null).statusCode()).isEqualTo(200);
        assertThat(send("/studio.css", "GET", null, null).statusCode()).isEqualTo(200);
        assertThat(send("/studio-client.mjs", "GET", null, null).statusCode()).isEqualTo(200);
        assertThat(send("/management-client.mjs", "GET", null, null).statusCode()).isEqualTo(200);
        assertThat(send("/studio-workspace.mjs", "GET", null, null).statusCode()).isEqualTo(200);
        assertThat(send("/missing-resource", "GET", null, null).statusCode()).isEqualTo(404);
        assertThat(send("/studio.css", "POST", null, null).statusCode()).isEqualTo(404);
    }

    @Test
    void demoTokensMustHaveSafeLengthsAndDistinctValues() {
        // Given / When / Then
        assertThatThrownBy(() -> new StudioHttpServer(null, new RequestActorContext(), 0, null, VIEWER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudioHttpServer(null, new RequestActorContext(), 0, "short", VIEWER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudioHttpServer(null, new RequestActorContext(), 0, EDITOR, "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudioHttpServer(null, new RequestActorContext(), 0, EDITOR, EDITOR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Distinct tokens required");
    }

    private HttpResponse<String> sendRaw(String path, String method, String contentType, String body)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .header("Authorization", "Bearer " + EDITOR);
        if (contentType != null)
            request.header("Content-Type", contentType);
        request.method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
