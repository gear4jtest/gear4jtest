package io.github.gear4jtest.studio.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.service.InMemoryStudioService;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.StudioAuthorization;

/**
 * Local demonstration transport, not a production authentication or distributed
 * management server.
 */
public final class StudioHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32));
    private final InMemoryStudioService service;
    private final byte[] editorToken;
    private final byte[] viewerToken;
    private final ObjectMapper mapper;

    public StudioHttpServer(InMemoryStudioService service, int port, String editorToken, String viewerToken)
            throws IOException {
        this.service = service;
        this.editorToken = token(editorToken);
        this.viewerToken = viewerToken == null ? null : token(viewerToken);
        if (this.viewerToken != null && MessageDigest.isEqual(this.editorToken, this.viewerToken))
            throw new IllegalArgumentException("Distinct tokens required");
        mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(16384)
                        .maxNumberLength(20).build())
                .build());
        mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
    }

    private static byte[] token(String value) {
        if (value == null || value.length() < 24 || value.length() > 512)
            throw new IllegalArgumentException("Configure GEAR4J_STUDIO_TOKEN (24..512 characters)");
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'self'; base-uri 'none'");
            try {
                String host = exchange.getRequestHeaders().getFirst("Host");
                if (!("127.0.0.1:" + port()).equals(host) && !("localhost:" + port()).equals(host)) {
                    json(exchange, 403, Map.of("code", "ORIGIN_DENIED"));
                    return;
                }
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (origin != null && !origin.equals("http://" + host)) {
                    json(exchange, 403, Map.of("code", "ORIGIN_DENIED"));
                    return;
                }
                if (exchange.getRequestURI().getPath().startsWith("/api/")) {
                    String actor = actor(exchange);
                    if (actor == null) {
                        json(exchange, 401, Map.of("code", "AUTHENTICATION_REQUIRED"));
                        return;
                    }
                    route(exchange, actor);
                } else
                    asset(exchange);
            } catch (StudioProblem e) {
                int status = switch (e.code()) {
                    case NOT_FOUND -> 404;
                    case CONFLICT -> 409;
                    case CAPACITY_EXCEEDED -> 429;
                    default -> 422;
                };
                json(exchange, status, Map.of("code", e.code().name()));
            } catch (SecurityException e) {
                json(exchange, 403, Map.of("code", "ACTION_DENIED"));
            } catch (IllegalArgumentException | JsonProcessingException e) {
                json(exchange, 400, Map.of("code", "INVALID_REQUEST"));
            } catch (RuntimeException e) {
                json(exchange, 500, Map.of("code", "INTERNAL_ERROR"));
            }
        }
    }

    private String actor(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("Authorization");
        if (value == null || !value.startsWith("Bearer ") || value.length() > 519)
            return null;
        byte[] bytes = value.substring(7).getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(editorToken, bytes))
            return "demo-editor";
        return viewerToken != null && MessageDigest.isEqual(viewerToken, bytes) ? "demo-viewer" : null;
    }

    private void route(HttpExchange exchange, String actor) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (method.equals("GET") && path.equals("/api/context")) {
            var actions = new ArrayList<String>();
            for (var action : StudioAuthorization.Action.values()) {
                try {
                    DemoWiring.authorization().require(actor, action);
                    actions.add(action.name());
                } catch (SecurityException denied) {
                    /* Omit denied action. */ }
            }
            json(exchange, 200, Map.of("context", service.runtime().context(), "actions", actions, "sample",
                                       DefinitionJson.write(DemoWiring.sample())));
            return;
        }
        if (method.equals("GET") && path.equals("/api/catalog")) {
            json(exchange, 200, service.runtime().catalog());
            return;
        }
        if (method.equals("POST") && path.equals("/api/drafts")) {
            var body = body(exchange);
            DefinitionJson.fields(body, Set.of("definition"));
            json(exchange, 201, revision(service.create(actor, DefinitionJson.read(body.path("definition")))));
            return;
        }
        if (method.equals("POST") && path.equals("/api/validate")) {
            var body = body(exchange);
            DefinitionJson.fields(body, Set.of("definition"));
            var report = service.validate(actor, DefinitionJson.read(body.path("definition")));
            json(exchange, 200, Map.of("valid", report.valid(), "diagnostics", report.diagnostics(),
                                       "catalogFingerprint", report.catalogFingerprint()));
            return;
        }
        if (method.equals("POST") && path.equals("/api/export")) {
            var body = body(exchange);
            DefinitionJson.fields(body, Set.of("definition"));
            json(exchange, 200, Map.of("source", service.export(actor, DefinitionJson.read(body.path("definition"))),
                                       "mediaType", "application/xml"));
            return;
        }
        if (method.equals("POST") && path.equals("/api/import")) {
            var body = body(exchange);
            DefinitionJson.fields(body, Set.of("source"));
            json(exchange, 201, revision(service.importDraft(actor, DefinitionJson.text(body, "source"))));
            return;
        }
        String[] parts = path.split("/");
        if (parts.length >= 4 && parts[2].equals("drafts")) {
            UUID id = UUID.fromString(parts[3]);
            if (parts.length == 4 && method.equals("GET")) {
                json(exchange, 200, revision(service.draft(actor, id)));
                return;
            }
            if (parts.length == 4 && method.equals("PUT")) {
                var body = body(exchange);
                DefinitionJson.fields(body, Set.of("expectedRevision", "definition"));
                json(exchange, 200, revision(service.save(actor, id, DefinitionJson.number(body, "expectedRevision"),
                                                          DefinitionJson.read(body.path("definition")))));
                return;
            }
            if (parts.length == 5 && parts[4].equals("revisions") && method.equals("GET")) {
                json(exchange, 200, service.revisions(actor, id).stream().map(StudioHttpServer::revision).toList());
                return;
            }
            if (parts.length == 5 && parts[4].equals("tests") && method.equals("POST")) {
                var body = body(exchange);
                DefinitionJson.fields(body, Set.of("revision", "input", "requestId"));
                json(exchange, 200,
                     service.test(actor, id, DefinitionJson.number(body, "revision"),
                                  UUID.fromString(DefinitionJson.text(body, "requestId")),
                                  DefinitionJson.text(body, "input")));
                return;
            }
        }
        if (parts.length == 4 && parts[2].equals("tests") && method.equals("GET")) {
            json(exchange, 200, service.testRequest(actor, UUID.fromString(parts[3])));
            return;
        }
        json(exchange, 404, Map.of("code", "NOT_FOUND"));
    }

    private JsonNode body(HttpExchange exchange) throws IOException {
        if (!"application/json".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Type")))
            throw new IllegalArgumentException("JSON required");
        byte[] data = exchange.getRequestBody().readNBytes(65537);
        if (data.length > 65536)
            throw new IllegalArgumentException("Body limit");
        JsonNode value = mapper.readTree(data);
        if (value == null)
            throw new IllegalArgumentException("Body required");
        return value;
    }

    private static Map<String, Object> revision(DraftRevision revision) {
        return Map.of("draftId", revision.draftId(), "revisionId", revision.revisionId(), "number", revision.number(),
                      "author", revision.author(), "savedAt", revision.savedAt(), "origin", revision.origin(),
                      "definition", DefinitionJson.write(revision.definition()));
    }

    private void json(HttpExchange exchange, int status, Object body) throws IOException {
        bytes(exchange, status, "application/json; charset=utf-8", mapper.writeValueAsBytes(body));
    }

    private void asset(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> files = Map.of("/", "index.html", "/embedded", "embedded.html", "/studio.css", "studio.css",
                                           "/studio-client.mjs", "studio-client.mjs", "/studio-element.mjs",
                                           "studio-element.mjs", "/bootstrap.mjs", "bootstrap.mjs");
        String file = files.get(path);
        if (!exchange.getRequestMethod().equals("GET") || file == null) {
            json(exchange, 404, Map.of("code", "NOT_FOUND"));
            return;
        }
        try (var stream = getClass().getResourceAsStream("/studio/" + file)) {
            if (stream == null) {
                json(exchange, 404, Map.of("code", "NOT_FOUND"));
                return;
            }
            bytes(exchange, 200,
                  file.endsWith(".mjs") ? "text/javascript; charset=utf-8"
                          : file.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8",
                  stream.readAllBytes());
        }
    }

    private void bytes(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @Override
    public void close() {
        server.stop(1);
        workers.shutdownNow();
    }
}
