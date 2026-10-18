package io.github.gear4jtest.studio.standalone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.studio.http.StudioManagementHttpHandler;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.StudioIdentity;

/**
 * Bounded, no-redirect adapter. Never retries an application request. Every
 * operation checks protocol/context/subject; the remote endpoint repeats the
 * binding check on the actual call before invoking management. Remote services
 * remain authoritative for ACL, persistence, idempotency and audit.
 */
public final class HttpRemoteManagement implements AutoCloseable {
    private final java.util.concurrent.ExecutorService io = Executors.newFixedThreadPool(4, task -> {
        var thread = new Thread(task, "studio-remote-http");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient client;
    private final TargetDirectory directory;
    private final TargetCredentials credentials;
    private final Duration timeout;
    private final Semaphore capacity;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public HttpRemoteManagement(TargetDirectory directory, TargetCredentials credentials) {
        this(directory, credentials, Duration.ofSeconds(15), 8);
    }

    public HttpRemoteManagement(TargetDirectory directory,
                                TargetCredentials credentials,
                                Duration timeout,
                                int capacity) {
        if (timeout == null || timeout.toMillis() < 50 || timeout.compareTo(Duration.ofMinutes(2)) > 0
                || capacity < 1 || capacity > 32)
            throw new IllegalArgumentException("Invalid remote limits");
        this.directory = java.util.Objects.requireNonNull(directory);
        this.credentials = java.util.Objects.requireNonNull(credentials);
        this.timeout = timeout;
        this.capacity = new Semaphore(capacity);
        client = HttpClient.newBuilder().executor(io).connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
    }

    public Response exchange(StudioIdentity actor,
                             String targetId,
                             String incomingToken,
                             String method,
                             String path,
                             String query,
                             byte[] body) {
        var target = directory.require(actor, targetId);
        if (!ManagementPath.allowed(method, path) || query != null && (query.length() > 4096
                || query.contains("#") || query.chars().anyMatch(Character::isISOControl))
                || body == null || body.length > 65536 || method.equals("GET") && body.length != 0)
            throw new RemoteProblem("INVALID_REQUEST", 400);
        if (closed || !capacity.tryAcquire())
            throw new RemoteProblem("CAPACITY_EXCEEDED", 429);
        boolean mutation = !method.equals("GET");
        boolean dispatched = false;
        try {
            String token;
            try {
                token = credentials.tokenFor(actor, target, incomingToken);
            } catch (RuntimeException denied) {
                throw new RemoteProblem("REMOTE_AUTH_UNAVAILABLE", 403);
            }
            if (token == null || token.isBlank() || token.length() > 16384
                    || token.chars().anyMatch(Character::isISOControl))
                throw new RemoteProblem("REMOTE_AUTH_UNAVAILABLE", 403);
            var context = send(target, actor, token, "GET", "/api/v1/context", null, new byte[0]);
            if (context.statusCode() == 401 || context.statusCode() == 403)
                throw new RemoteProblem("REMOTE_ACCESS_DENIED", 403);
            verify(target, context);
            if (context.statusCode() != 200)
                throw new RemoteProblem("REMOTE_UNAVAILABLE", 502);
            var info = StudioJson.mapper().readTree(context.body());
            if (!target.runtimeContextId().equals(info.path("context").path("id").asText()))
                throw new RemoteProblem("REMOTE_CONTEXT_MISMATCH", 409);
            if (!actor.subject().equals(info.path("identity").path("subject").asText()))
                throw new RemoteProblem("REMOTE_IDENTITY_MISMATCH", 409);
            if (method.equals("GET") && path.equals("/api/v1/context") && query == null)
                return new Response(200, context.body());
            // Re-evaluate local visibility after the possibly slow handshake.
            directory.require(actor, targetId);
            dispatched = true;
            var response = send(target, actor, token, method, path, query, body);
            verify(target, response);
            if (response.statusCode() < 200 || response.statusCode() >= 500 || response.statusCode() / 100 == 3)
                throw new RemoteProblem("REMOTE_UNAVAILABLE", 502);
            // Require bounded JSON, never forward HTML/login pages, Set-Cookie or
            // redirects.
            if (StudioJson.mapper().readTree(response.body()) == null)
                throw new RemoteProblem("REMOTE_INVALID_RESPONSE", 502);
            return new Response(response.statusCode(), response.body());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException)
                Thread.currentThread().interrupt();
            if (mutation && dispatched)
                throw new RemoteProblem("REMOTE_OUTCOME_UNKNOWN", 502);
            if (failure instanceof RemoteProblem problem)
                throw problem;
            throw new RemoteProblem("REMOTE_UNAVAILABLE", 502);
        } finally {
            capacity.release();
        }
    }

    private HttpResponse<byte[]> send(StudioTarget target,
                                      StudioIdentity actor,
                                      String token,
                                      String method,
                                      String path,
                                      String query,
                                      byte[] body)
            throws Exception {
        var request = HttpRequest
                .newBuilder(URI.create(target.endpoint().toASCIIString() + path + (query == null ? "" : "?" + query)))
                .timeout(timeout).header("Authorization", "Bearer " + token).header("Accept", "application/json")
                .header(StudioManagementHttpHandler.EXPECTED_CONTEXT, target.runtimeContextId())
                .header(StudioManagementHttpHandler.EXPECTED_SUBJECT, java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(actor.subject().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .method(method, method.equals("GET") ? HttpRequest.BodyPublishers.noBody() : new OnceBody(body));
        if (!method.equals("GET"))
            request.header("Content-Type", "application/json");
        var future = client.sendAsync(request.build(), ignored -> new BoundedBody(1048576));
        pending.add(future);
        try {
            if (closed)
                throw new IllegalStateException("Closed");
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            pending.remove(future);
            if (!future.isDone())
                future.cancel(true);
        }
    }

    private static void verify(StudioTarget target, HttpResponse<byte[]> response) {
        if (!response.headers().firstValue(StudioManagementHttpHandler.PROTOCOL).orElse("").equals("1"))
            throw new RemoteProblem("REMOTE_PROTOCOL_MISMATCH", 409);
        if (!response.headers().firstValue(StudioManagementHttpHandler.CONTEXT).orElse("")
                .equals(target.runtimeContextId()))
            throw new RemoteProblem("REMOTE_CONTEXT_MISMATCH", 409);
        if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT)
                .startsWith("application/json"))
            throw new RemoteProblem("REMOTE_INVALID_RESPONSE", 502);
    }

    @Override
    public void close() {
        closed = true;
        pending.forEach(future -> future.cancel(true));
        io.shutdownNow();
    }

    public record Response(int status, byte[] body) {}
}
