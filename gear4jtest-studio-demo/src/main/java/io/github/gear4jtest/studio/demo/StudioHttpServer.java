package io.github.gear4jtest.studio.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.management.StudioManagementService;
import io.github.gear4jtest.studio.service.StudioProblem;

/**
 * Local transport example. Authentication is host supplied; never inferred from
 * body fields.
 */
public final class StudioHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32));
    private final ManagementRoutes routes;
    private final RequestActorContext actors;
    private final Function<HttpExchange, StudioIdentity> authenticate;
    private final ObjectMapper mapper = StudioJson.mapper();

    public StudioHttpServer(StudioManagementService service,
                            RequestActorContext actors,
                            int port,
                            String editorToken,
                            String viewerToken)
            throws IOException {
        this(service, actors, port, tokens(editorToken, viewerToken));
    }

    public StudioHttpServer(StudioManagementService service,
                            RequestActorContext actors,
                            int port,
                            Function<HttpExchange, StudioIdentity> authenticate)
            throws IOException {
        this.routes = new ManagementRoutes(java.util.Objects.requireNonNull(service));
        this.actors = java.util.Objects.requireNonNull(actors);
        this.authenticate = java.util.Objects.requireNonNull(authenticate);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
    }

    private static byte[] token(String value) {
        if (value == null || value.length() < 24 || value.length() > 512)
            throw new IllegalArgumentException("Configure a token of 24..512 characters");
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Function<HttpExchange, StudioIdentity> tokens(String editor, String viewer) {
        byte[] edit = token(editor);
        byte[] read = viewer == null ? null : token(viewer);
        if (read != null && MessageDigest.isEqual(edit, read))
            throw new IllegalArgumentException("Distinct tokens required");
        return exchange -> {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ") || header.length() > 519)
                return null;
            byte[] supplied = header.substring(7).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(edit, supplied))
                return new StudioIdentity("demo-editor", Set.of("domain-creators"), false);
            return read != null && MessageDigest.isEqual(read, supplied)
                    ? new StudioIdentity("demo-viewer", Set.of(), false) : null;
        };
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
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (!("127.0.0.1:" + port()).equals(host) && !("localhost:" + port()).equals(host)
                        || origin != null && !origin.equals("http://" + host)) {
                    json(exchange, 403, Map.of("code", "ORIGIN_DENIED"));
                    return;
                }
                if (exchange.getRequestURI().toString().length() > 4096)
                    throw new IllegalArgumentException("URI too long");
                if (exchange.getRequestURI().getPath().startsWith("/api/")) {
                    var identity = authenticate.apply(exchange);
                    if (identity == null) {
                        json(exchange, 401, Map.of("code", "UNAUTHENTICATED"));
                        return;
                    }
                    try (var scope = actors.bind(identity)) {
                        String method = exchange.getRequestMethod();
                        JsonNode body = Set.of("POST", "PUT").contains(method) ? body(exchange) : null;
                        var response = routes.handle(method, exchange.getRequestURI().getPath(),
                                                     query(exchange.getRequestURI().getRawQuery()), body);
                        json(exchange, response.status(), response.value());
                    }
                } else
                    asset(exchange);
            } catch (StudioProblem failure) {
                int status = switch (failure.code()) {
                    case NOT_FOUND -> 404;
                    case CONFLICT -> 409;
                    case CAPACITY_EXCEEDED -> 429;
                    case UNAUTHENTICATED -> 401;
                    case FORBIDDEN -> 403;
                    case INVALID_REQUEST -> 400;
                    case STORAGE_UNAVAILABLE -> 503;
                    default -> 422;
                };
                json(exchange, status, Map.of("code", failure.code().name()));
            } catch (SecurityException failure) {
                json(exchange, 403, Map.of("code", "ACTION_DENIED"));
            } catch (IllegalArgumentException | JsonProcessingException failure) {
                json(exchange, 400, Map.of("code", "INVALID_REQUEST"));
            } catch (RuntimeException failure) {
                json(exchange, 500, Map.of("code", "INTERNAL_ERROR"));
            }
        }
    }

    private static Map<String, String> query(String raw) {
        var result = new LinkedHashMap<String, String>();
        if (raw == null || raw.isEmpty())
            return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2 || result.size() >= 3)
                throw new IllegalArgumentException("Invalid query");
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (result.putIfAbsent(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null)
                throw new IllegalArgumentException("Duplicate query");
        }
        return result;
    }

    private JsonNode body(HttpExchange exchange) throws IOException {
        if (!"application/json".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Type")))
            throw new IllegalArgumentException("JSON required");
        byte[] data = exchange.getRequestBody().readNBytes(65537);
        if (data.length > 65536)
            throw new IllegalArgumentException("Body limit");
        var value = mapper.readTree(data);
        if (value == null)
            throw new IllegalArgumentException("Body required");
        return value;
    }

    private void json(HttpExchange exchange, int status, Object value) throws IOException {
        bytes(exchange, status, "application/json; charset=utf-8", mapper.writeValueAsBytes(value));
    }

    private void asset(HttpExchange exchange) throws IOException {
        Map<String, String> files = Map.of("/", "index.html", "/embedded", "embedded.html", "/studio.css", "studio.css",
                                           "/studio-client.mjs", "studio-client.mjs", "/studio-element.mjs",
                                           "studio-element.mjs", "/bootstrap.mjs", "bootstrap.mjs",
                                           "/management-client.mjs", "management-client.mjs", "/studio-workspace.mjs",
                                           "studio-workspace.mjs", "/studio-versions.mjs", "studio-versions.mjs");
        String file = files.get(exchange.getRequestURI().getPath());
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

    private static void bytes(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
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
