package io.github.gear4jtest.studio.standalone;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import io.github.gear4jtest.studio.demo.DemoDatabase;
import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.http.RequestActorContext;
import io.github.gear4jtest.studio.http.StudioManagementHttpHandler;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.*;

/**
 * Real sockets, TLS handshakes and host authentication; no permissive trust
 * manager.
 */
class DeploymentSecurityTest {
    private static final char[] PASSWORD = "synthetic-test-keystore".toCharArray();
    @TempDir
    Path directory;

    @Test
    void mutualTlsHostIdentityAclCredentialRotationAndHostnameChecksApplyBeforeManagement() throws Exception {
        // Given: independent certificates, each explicitly trusted.
        certificate("server", "localhost");
        certificate("client", "studio-client");
        trust("server", "client");
        trust("client", "server");
        var serverTls = tls("server", "server");
        var clientTls = tls("client", "client");
        var actor = StudioIdentity.fromScopedSubject("https://issuer.example/a", "same-sub", Set.of(), false);
        var otherIssuer = StudioIdentity.fromScopedSubject("https://issuer.example/b", "same-sub", Set.of(), false);
        var accepted = new AtomicReference<>("synthetic-credential-initial");
        var supplied = new AtomicReference<>(accepted.get());
        var db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var workers = Executors.newFixedThreadPool(3);
        try (var lifetime = db.getConnection();
                var runtime = DemoWiring.runtime("tls", null, null, DataCapturePolicy.none())) {
            var actors = new RequestActorContext();
            var service = DemoDatabase.service(db, runtime, actors);
            var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
            server.setHttpsConfigurator(new HttpsConfigurator(serverTls) {
                @Override
                public void configure(HttpsParameters parameters) {
                    var ssl = serverTls.getDefaultSSLParameters();
                    ssl.setNeedClientAuth(true);
                    parameters.setSSLParameters(ssl);
                }
            });
            server.setExecutor(workers);
            server.createContext("/api/", new StudioManagementHttpHandler(service, actors,
                    exchange -> ("Bearer " + accepted.get())
                            .equals(exchange.getRequestHeaders().getFirst("Authorization"))
                                    ? actor : null,
                    DemoWiring.sample()));
            server.start();
            try {
                String endpoint = "https://localhost:" + server.getAddress().getPort();
                var client = HttpClient.newBuilder().sslContext(clientTls).connectTimeout(Duration.ofSeconds(2))
                        .build();
                try (var remote = remote(endpoint, runtime.context().id(), supplied, client)) {
                    // When / Then: transport access does not grant Studio permissions.
                    assertThat(call(remote, actor, "GET", "/api/v1/context", "").status()).isEqualTo(200);
                    assertThat(call(remote, actor, "POST", "/api/v1/domains",
                                    "{\"key\":\"private\",\"name\":\"Private\",\"description\":\"\"}")
                            .status()).isEqualTo(403);
                    assertThatThrownBy(() -> call(remote, otherIssuer, "GET", "/api/v1/catalog", ""))
                            .isInstanceOf(RemoteProblem.class);
                    accepted.set("synthetic-credential-rotated");
                    assertThatThrownBy(() -> call(remote, actor, "GET", "/api/v1/context", ""))
                            .hasMessage("REMOTE_ACCESS_DENIED");
                    supplied.set(accepted.get());
                    assertThat(call(remote, actor, "GET", "/api/v1/context", "").status()).isEqualTo(200);
                }
                try (var remote = remote(endpoint, runtime.context().id(), supplied,
                                         HttpClient.newBuilder().sslContext(tls(null, "client")).build())) {
                    assertThatThrownBy(() -> call(remote, actor, "GET", "/api/v1/context", ""))
                            .hasMessage("REMOTE_UNAVAILABLE");
                }
                try (var remote = remote(endpoint.replace("localhost", "127.0.0.1"), runtime.context().id(), supplied,
                                         client)) {
                    assertThatThrownBy(() -> call(remote, actor, "GET", "/api/v1/context", ""))
                            .hasMessage("REMOTE_UNAVAILABLE");
                }
                try (var remote = remote(endpoint, "wrong.context", supplied, client)) {
                    assertThatThrownBy(() -> call(remote, actor, "GET", "/api/v1/catalog", ""))
                            .hasMessage("REMOTE_CONTEXT_MISMATCH");
                }
                try (var remote = remote(endpoint, runtime.context().id(), supplied, HttpClient.newHttpClient())) {
                    assertThatThrownBy(() -> call(remote, actor, "GET", "/api/v1/context", ""))
                            .hasMessage("REMOTE_UNAVAILABLE");
                }
            } finally {
                server.stop(0);
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static HttpRemoteManagement remote(String endpoint,
                                               String context,
                                               AtomicReference<String> token,
                                               HttpClient client) {
        var directory = new TargetDirectory(List.of(new StudioApplication("app", "App")),
                List.of(new StudioTarget("tls", "app", "TLS", context, URI.create(endpoint))), (a, t) -> true);
        return new HttpRemoteManagement(directory, (a, t, incoming) -> token.get(), Duration.ofSeconds(3), 2, 1,
                client);
    }

    private static HttpRemoteManagement.Response call(HttpRemoteManagement remote,
                                                      StudioIdentity actor,
                                                      String method,
                                                      String path,
                                                      String body) {
        return remote.exchange(actor, "tls", "never-forward-this-browser-token", method, path, null,
                               body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void certificate(String name, String dns) throws Exception {
        keytool("-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname",
                "CN=" + dns,
                "-ext", "SAN=dns:" + dns, "-keystore", file(name + ".p12"), "-storetype", "PKCS12", "-storepass",
                new String(PASSWORD));
        keytool("-exportcert", "-alias", name, "-keystore", file(name + ".p12"), "-storepass", new String(PASSWORD),
                "-file", file(name + ".cer"));
    }

    private void trust(String name, String peer) throws Exception {
        keytool("-importcert", "-noprompt", "-alias", peer, "-file", file(peer + ".cer"), "-keystore",
                file(name + "-trust.p12"), "-storetype", "PKCS12", "-storepass", new String(PASSWORD));
    }

    private SSLContext tls(String keys, String trust) throws Exception {
        KeyManagerFactory km = null;
        if (keys != null) {
            km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            km.init(store(keys + ".p12"), PASSWORD);
        }
        var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(store(trust + "-trust.p12"));
        var result = SSLContext.getInstance("TLS");
        result.init(km == null ? null : km.getKeyManagers(), tm.getTrustManagers(), null);
        return result;
    }

    private KeyStore store(String file) throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(directory.resolve(file))) {
            store.load(input, PASSWORD);
        }
        return store;
    }

    private String file(String name) {
        return directory.resolve(name).toString();
    }

    private void keytool(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool" + suffix).toString());
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(directory.resolve("keytool.log").toFile()).start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            process.destroyForcibly();
        }
    }
}
