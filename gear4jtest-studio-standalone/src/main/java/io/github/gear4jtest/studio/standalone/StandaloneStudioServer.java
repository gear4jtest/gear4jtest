package io.github.gear4jtest.studio.standalone;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.StudioIdentity;

/**
 * Local standalone host. Owns its server; caller owns the remote adapter and
 * authentication lifecycle.
 */
public final class StandaloneStudioServer implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32));
    private final TargetDirectory directory;
    private final HttpRemoteManagement remote;
    private final Function<HttpExchange, StudioIdentity> authenticate;
    private static final Set<String> ASSETS = Set.of("index.html", "bootstrap.mjs", "studio.css", "index.mjs",
                                                     "studio-client.mjs", "management-client.mjs",
                                                     "studio-workspace.mjs", "studio-element.mjs",
                                                     "studio-runs.mjs", "studio-versions.mjs", "studio-scenarios.mjs",
                                                     "studio-targets.mjs", "studio-simulation.mjs");

    public StandaloneStudioServer(int port,
                                  TargetDirectory directory,
                                  HttpRemoteManagement remote,
                                  Function<HttpExchange, StudioIdentity> authenticate)
            throws IOException {
        this.directory = java.util.Objects.requireNonNull(directory);
        this.remote = java.util.Objects.requireNonNull(remote);
        this.authenticate = java.util.Objects.requireNonNull(authenticate);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
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
                        || origin != null && !origin.equals("http://" + host))
                    throw new RemoteProblem("ORIGIN_DENIED", 403);
                var uri = exchange.getRequestURI();
                if (uri.toASCIIString().length() > 4096 || !uri.getRawPath().equals(uri.getPath()))
                    throw new RemoteProblem("INVALID_REQUEST", 400);
                String path = uri.getPath();
                if (!path.startsWith("/studio/api/") && !path.startsWith("/targets/")) {
                    asset(exchange, path);
                    return;
                }
                var actor = authenticate.apply(exchange);
                if (actor == null)
                    throw new RemoteProblem("UNAUTHENTICATED", 401);
                if (path.equals("/studio/api/v1/targets") && exchange.getRequestMethod().equals("GET")
                        && uri.getRawQuery() == null) {
                    json(exchange, 200, Map.of("identity", actor.principalId(), "targets", directory.visible(actor)));
                    return;
                }
                String[] parts = path.split("/", 4);
                if (parts.length != 4 || !parts[1].equals("targets"))
                    throw new RemoteProblem("NOT_FOUND", 404);
                var target = directory.require(actor, parts[2]);
                String expectedContext = exchange.getRequestHeaders()
                        .getFirst(io.github.gear4jtest.studio.http.StudioManagementHttpHandler.EXPECTED_CONTEXT);
                if (expectedContext != null && !expectedContext.equals(target.runtimeContextId()))
                    throw new RemoteProblem("REMOTE_CONTEXT_MISMATCH", 409);
                String method = exchange.getRequestMethod();
                String endpointPath = "/" + parts[3];
                if (method.equals("GET") && endpointPath.matches("/api/v1/runs/[a-fA-F0-9-]{36}/events"))
                    throw new RemoteProblem("STREAM_UNAVAILABLE", 501); // Public client falls back to durable snapshot
                                                                        // polling.
                byte[] body = new byte[0];
                if (Set.of("POST", "PUT").contains(method)) {
                    if (!"application/json".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Type")))
                        throw new RemoteProblem("INVALID_REQUEST", 400);
                    body = exchange.getRequestBody().readNBytes(65537);
                    if (body.length > 65536)
                        throw new RemoteProblem("INVALID_REQUEST", 400);
                }
                var response = remote.exchange(actor, parts[2], bearer(exchange), method, endpointPath,
                                               uri.getRawQuery(), body);
                bytes(exchange, response.status(), "application/json; charset=utf-8", response.body());
            } catch (RemoteProblem failure) {
                json(exchange, failure.status(), Map.of("code", failure.getMessage()));
            } catch (RuntimeException failure) {
                json(exchange, 500, Map.of("code", "INTERNAL_ERROR"));
            }
        }
    }

    static String bearer(HttpExchange exchange) {
        var all = exchange.getRequestHeaders().get("Authorization");
        if (all == null || all.size() != 1 || !all.get(0).startsWith("Bearer ") || all.get(0).length() > 16391)
            throw new RemoteProblem("UNAUTHENTICATED", 401);
        return all.get(0).substring(7);
    }

    private void asset(HttpExchange exchange, String path) throws IOException {
        String name = path.equals("/") ? "index.html" : path.substring(1);
        if (!exchange.getRequestMethod().equals("GET") || !ASSETS.contains(name))
            throw new RemoteProblem("NOT_FOUND", 404);
        try (var input = getClass().getResourceAsStream("/studio/" + name)) {
            if (input == null)
                throw new RemoteProblem("NOT_FOUND", 404);
            bytes(exchange, 200, name.endsWith(".mjs") ? "text/javascript; charset=utf-8"
                    : name.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8",
                  input.readAllBytes());
        }
    }

    private static void json(HttpExchange exchange, int status, Object value) throws IOException {
        bytes(exchange, status, "application/json; charset=utf-8", StudioJson.mapper().writeValueAsBytes(value));
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
