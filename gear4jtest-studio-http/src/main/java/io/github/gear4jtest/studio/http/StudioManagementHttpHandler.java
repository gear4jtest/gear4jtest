package io.github.gear4jtest.studio.http;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.management.StudioManagementService;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.service.StudioProblem;

/**
 * Public optional JDK HTTP adapter. Host owns server, bounded executor, TLS,
 * origin/CSRF policy and authentication. No identity is accepted from DTOs.
 * Expected-context/principal headers constrain an authenticated call; they
 * never authenticate or grant any permission.
 */
public final class StudioManagementHttpHandler implements HttpHandler {
    public static final String PROTOCOL_VERSION = "2";
    public static final String PROTOCOL = "X-Gear4J-Management-Protocol";
    public static final String EXPECTED_CONTEXT = "X-Gear4J-Expected-Context";
    public static final String EXPECTED_PRINCIPAL = "X-Gear4J-Expected-Principal";
    public static final String CONTEXT = "X-Gear4J-Runtime-Context";
    private final ManagementRoutes routes;
    private final StudioManagementService service;
    private final RequestActorContext actors;
    private final Function<HttpExchange, StudioIdentity> authenticate;
    private final ObjectMapper mapper = StudioJson.mapper();
    private final java.util.concurrent.Semaphore streams = new java.util.concurrent.Semaphore(1);

    public StudioManagementHttpHandler(StudioManagementService service,
                                       RequestActorContext actors,
                                       Function<HttpExchange, StudioIdentity> authenticate,
                                       ChainDefinition sample) {
        this.service = java.util.Objects.requireNonNull(service);
        this.actors = java.util.Objects.requireNonNull(actors);
        this.authenticate = java.util.Objects.requireNonNull(authenticate);
        this.routes = new ManagementRoutes(service, java.util.Objects.requireNonNull(sample));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            try {
                if (exchange.getRequestURI().toString().length() > 4096
                        || !exchange.getRequestURI().getRawPath().equals(exchange.getRequestURI().getPath()))
                    throw new IllegalArgumentException("Invalid URI");
                var identity = authenticate.apply(exchange);
                if (identity == null) {
                    json(exchange, 401, Map.of("code", "UNAUTHENTICATED"));
                    return;
                }
                String context;
                try (var scope = actors.bind(identity)) {
                    context = service.context().id();
                }
                exchange.getResponseHeaders().set(PROTOCOL, PROTOCOL_VERSION);
                exchange.getResponseHeaders().set(CONTEXT, context);
                String expected = exchange.getRequestHeaders().getFirst(EXPECTED_CONTEXT);
                String principal = exchange.getRequestHeaders().getFirst(EXPECTED_PRINCIPAL);
                String encodedPrincipal = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(identity.principalId().getBytes(StandardCharsets.UTF_8));
                if (expected != null && !expected.equals(context)
                        || principal != null && !principal.equals(encodedPrincipal)) {
                    json(exchange, 409, Map.of("code", "REMOTE_BINDING_MISMATCH"));
                    return;
                }
                String method = exchange.getRequestMethod();
                if (ManagementEndpoint.match(method, exchange.getRequestURI().getPath())
                        .orElse(null) == ManagementEndpoint.RUN_EVENTS) {
                    stream(exchange, identity);
                    return;
                }
                try (var scope = actors.bind(identity)) {
                    JsonNode body = Set.of("POST", "PUT").contains(method) ? body(exchange) : null;
                    var response = routes.handle(method, exchange.getRequestURI().getPath(),
                                                 query(exchange.getRequestURI().getRawQuery()), body);
                    json(exchange, response.status(), response.value());
                }
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
            if (parts.length != 2 || result.size() >= 12)
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

    /**
     * Best-effort snapshots. No replay log; reconnect always starts with the
     * durable snapshot.
     */
    private void stream(HttpExchange exchange, StudioIdentity initialActor) throws IOException {
        if (exchange.getRequestURI().getRawQuery() != null)
            throw new IllegalArgumentException("No stream query parameters");
        var id = java.util.UUID.fromString(exchange.getRequestURI().getPath().split("/")[4]);
        io.github.gear4jtest.studio.model.StudioRun current;
        try (var scope = actors.bind(initialActor)) {
            current = service.run(id);
        }
        if (!streams.tryAcquire())
            throw new StudioProblem(StudioProblem.Code.CAPACITY_EXCEEDED);
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
            exchange.sendResponseHeaders(200, 0);
            var output = exchange.getResponseBody();
            output.write("retry: 2000\n\n".getBytes(StandardCharsets.UTF_8));
            io.github.gear4jtest.studio.model.StudioRun previous = null;
            for (int tick = 0; tick < 15; tick++) {
                try {
                    var actor = authenticate.apply(exchange);
                    if (actor == null || !actor.principalId().equals(initialActor.principalId()))
                        throw new StudioProblem(StudioProblem.Code.UNAUTHENTICATED);
                    try (var scope = actors.bind(actor)) {
                        current = service.run(id);
                    }
                    if (!current.equals(previous)) {
                        output.write(("event: snapshot\ndata: " + mapper.writeValueAsString(current) + "\n\n")
                                .getBytes(StandardCharsets.UTF_8));
                        previous = current;
                    } else
                        output.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    if (!Set.of("PREPARING", "RUNNING").contains(current.status()))
                        break;
                    TimeUnit.SECONDS.sleep(1);
                } catch (RuntimeException revoked) {
                    output.write("event: unavailable\ndata: {\"code\":\"ACCESS_OR_STORAGE_UNAVAILABLE\"}\n\n"
                            .getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    break;
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            streams.release();
        }
    }

    private void json(HttpExchange exchange, int status, Object value) throws IOException {
        bytes(exchange, status, "application/json; charset=utf-8", mapper.writeValueAsBytes(value));
    }

    private static void bytes(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

}
