package io.github.gear4jtest.studio.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StudioHttpServerTest {
    private static final String EDITOR = "synthetic-editor-token-0123456789";
    private static final String VIEWER = "synthetic-viewer-token-0123456789";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(StudioHttpServer server, String path, String method, String token, Object body)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));
        if (token != null)
            request.header("Authorization", "Bearer " + token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (body != null)
            request.header("Content-Type", "application/json");
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void httpFlowRunsPinnedRevisionAndRecoversReceipt() throws Exception {
        // Given
        var resources = DemoWiring.plainResources();
        try (var runtime = DemoWiring.runtime("http", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.allowed());
                var server = new StudioHttpServer(DemoWiring.service(runtime), 0, EDITOR, VIEWER)) {
            server.start();
            assertThat(send(server, "/api/catalog", "GET", null, null).statusCode()).isEqualTo(401);
            assertThat(mapper.readTree(send(server, "/api/catalog", "GET", EDITOR, null).body())).hasSize(5);
            // When
            var created = send(server, "/api/drafts", "POST", EDITOR,
                               Map.of("definition", DefinitionJson.write(DemoWiring.sample())));
            assertThat(created.statusCode()).isEqualTo(201);
            var draft = mapper.readTree(created.body());
            String id = draft.path("draftId").asText();
            var validation = send(server, "/api/validate", "POST", EDITOR,
                                  Map.of("definition", draft.path("definition")));
            assertThat(mapper.readTree(validation.body()).path("valid").booleanValue()).isTrue();
            String requestId = UUID.randomUUID().toString();
            var body = Map.of("requestId", requestId, "revision", 1, "input", "  electric  ");
            var executed = send(server, "/api/drafts/" + id + "/tests", "POST", EDITOR, body);
            // Then
            assertThat(executed.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(executed.body()).path("run").path("output").path("value").asText())
                    .isEqualTo("ELECTRIC");
            assertThat(send(server, "/api/tests/" + requestId, "GET", EDITOR, null).body()).isEqualTo(executed.body());
            assertThat(send(server, "/api/drafts/" + id + "/tests", "POST", EDITOR, body).body())
                    .isEqualTo(executed.body());
            assertThat(send(server, "/api/drafts/" + id, "PUT", EDITOR,
                            Map.of("expectedRevision", 0, "definition", draft.path("definition")))
                    .statusCode()).isEqualTo(409);
            var exported = mapper.readTree(send(server, "/api/export", "POST", EDITOR,
                                                Map.of("definition", draft.path("definition")))
                    .body());
            var imported = send(server, "/api/import", "POST", EDITOR,
                                Map.of("source", exported.path("source").asText()));
            assertThat(imported.statusCode()).isEqualTo(201);
            assertThat(mapper.readTree(imported.body()).path("definition")).isEqualTo(draft.path("definition"));
        }
    }

    @Test
    void hostileRequestsAndViewerCannotMutateOrProvideTheirOwnActor() throws Exception {
        var resources = DemoWiring.plainResources();
        try (var runtime = DemoWiring.runtime("http", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.none());
                var server = new StudioHttpServer(DemoWiring.service(runtime), 0, EDITOR, VIEWER)) {
            server.start();
            var definition = DefinitionJson.write(DemoWiring.sample());
            assertThat(send(server, "/api/drafts", "POST", VIEWER, Map.of("definition", definition)).statusCode())
                    .isEqualTo(403);
            assertThat(send(server, "/api/drafts", "POST", EDITOR, Map.of("definition", definition, "actor", "admin"))
                    .statusCode()).isEqualTo(400);
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/catalog"))
                    .header("Authorization", "Bearer " + EDITOR).header("Origin", "https://untrusted.example").build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
            var dangerous = send(server, "/api/import", "POST", EDITOR,
                                 Map.of("source", "<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><x>&x;</x>"));
            assertThat(dangerous.statusCode()).isEqualTo(422);
            assertThat(dangerous.body()).doesNotContain("passwd", "root:");
            assertThat(send(server, "/embedded", "GET", null, null).body()).contains("<gear4j-studio>", "Portail");
            assertThat(send(server, "/studio-element.mjs", "GET", null, null).statusCode()).isEqualTo(200);
        }
    }
}
