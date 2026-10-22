package io.github.gear4jtest.studio.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.studio.http.RequestActorContext;
import io.github.gear4jtest.studio.http.StudioManagementHttpHandler;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.model.ScenarioData;
import io.github.gear4jtest.studio.model.ScenarioExpectation;
import io.github.gear4jtest.studio.model.ScenarioInput;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DemoHostTest {
    private static final String EDITOR = "synthetic-editor-token-0123456789";
    private static final String VIEWER = "synthetic-viewer-token-0123456789";

    @Test
    void demoStillWiresItsLocalIdentityPolicyAndAssetsAroundThePublicHandler() throws Exception {
        // Given: the demo owns the host and authentication; the reusable handler owns
        // routes.
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var resources = DemoWiring.plainResources();
        try (var lifetime = source.getConnection();
                var runtime = DemoWiring.runtime("demo-host", DemoWiring.plainExecutor(resources), resources,
                                                 DemoWiring.capturePolicy("none"))) {
            var actors = new RequestActorContext();
            var service = DemoDatabase.service(source, runtime, actors);
            assertThatThrownBy(() -> new StudioHttpServer(service, actors, 0, "short", VIEWER))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StudioHttpServer(service, actors, 0, EDITOR, EDITOR))
                    .isInstanceOf(IllegalArgumentException.class);
            try (var host = new StudioHttpServer(service, actors, 0, EDITOR, VIEWER)) {
                host.start();
                var client = HttpClient.newHttpClient();
                String base = "http://127.0.0.1:" + host.port();
                // When / Then: both local users reach the same context with different rights.
                for (String token : new String[] { EDITOR, VIEWER }) {
                    var response = get(client, base + "/api/v1/context", token);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().firstValue(StudioManagementHttpHandler.PROTOCOL)).contains("2");
                    var body = StudioJson.mapper().readTree(response.body());
                    assertThat(body.path("identity").path("principalId").asText())
                            .isEqualTo(token.equals(EDITOR) ? "demo-editor" : "demo-viewer");
                    assertThat(body.path("canCreateDomain").asBoolean()).isEqualTo(token.equals(EDITOR));
                }
                assertThat(get(client, base + "/api/v1/context", null).statusCode()).isEqualTo(401);
                assertThat(get(client, base + "/api/v1/context", "wrong").statusCode()).isEqualTo(401);
                var foreign = HttpRequest.newBuilder(URI.create(base + "/api/v1/context"))
                        .header("Authorization", "Bearer " + EDITOR).header("Origin", "https://untrusted.invalid")
                        .build();
                assertThat(client.send(foreign, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
                for (String path : new String[] { "/", "/embedded", "/studio.css", "/bootstrap.mjs",
                        "/studio-workspace.mjs", "/studio-scenarios.mjs" }) {
                    var asset = get(client, base + path, null);
                    assertThat(asset.statusCode()).isEqualTo(200);
                    assertThat(asset.headers().firstValue("Content-Security-Policy")).isPresent();
                    assertThat(asset.body()).isNotBlank();
                }
                assertThat(get(client, base + "/unlisted-file", null).statusCode()).isEqualTo(404);
                assertThat(client.send(HttpRequest.newBuilder(URI.create(base + "/studio.css"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                        .statusCode()).isEqualTo(404);
            }
        }
    }

    @Test
    void optionalViewerTokenIsReallyDisabledAndAssetsAreServedWithoutAnActor() throws Exception {
        // Given: this host accepts one local editor token and no viewer token.
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var resources = DemoWiring.plainResources();
        try (var lifetime = source.getConnection();
                var runtime = DemoWiring.runtime("single-token", DemoWiring.plainExecutor(resources), resources,
                                                 DataCapturePolicy.none())) {
            var actors = new RequestActorContext();
            var service = DemoDatabase.service(source, runtime, actors);
            assertThatThrownBy(() -> new StudioHttpServer(service, actors, 0, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StudioHttpServer(service, actors, 0, "x".repeat(513), null))
                    .isInstanceOf(IllegalArgumentException.class);
            try (var host = new StudioHttpServer(service, actors, 0, EDITOR, null)) {
                host.start();
                var client = HttpClient.newHttpClient();
                String base = "http://127.0.0.1:" + host.port();

                // When / Then: editor auth works, but the old viewer token is not accepted.
                assertThat(get(client, base + "/api/v1/context", EDITOR).statusCode()).isEqualTo(200);
                assertThat(get(client, base + "/api/v1/context", VIEWER).statusCode()).isEqualTo(401);
                var header = HttpRequest.newBuilder(URI.create(base + "/api/v1/context"))
                        .header("Authorization", "Token " + EDITOR).build();
                assertThat(client.send(header, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
                var origin = HttpRequest.newBuilder(URI.create(base + "/api/v1/context"))
                        .header("Authorization", "Bearer " + EDITOR).header("Origin", base).build();
                assertThat(client.send(origin, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
                var script = get(client, base + "/studio-element.mjs", null);
                assertThat(script.statusCode()).isEqualTo(200);
                assertThat(script.headers().firstValue("Content-Type")).contains("text/javascript; charset=utf-8");
                assertThat(script.body()).isNotBlank();
            }
        }
    }

    @Test
    void hostAcceptsAnExplicitAuthenticationProviderInsteadOfTokens() throws Exception {
        // Given: a host-specific identity provider can replace demo bearer tokens.
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var resources = DemoWiring.plainResources();
        try (var lifetime = source.getConnection();
                var runtime = DemoWiring.runtime("custom-auth", DemoWiring.plainExecutor(resources), resources,
                                                 DataCapturePolicy.none())) {
            var actors = new RequestActorContext();
            var service = DemoDatabase.service(source, runtime, actors);
            try (var host = new StudioHttpServer(service, actors, 0,
                    exchange -> new StudioIdentity("host-actor", Set.of("domain-creators"), false))) {
                host.start();
                var response = get(HttpClient.newHttpClient(),
                                   "http://127.0.0.1:" + host.port() + "/api/v1/context", null);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(StudioJson.mapper().readTree(response.body()).path("identity").path("principalId").asText())
                        .isEqualTo("host-actor");
            }
        }
    }

    @Test
    void demoFixturesAreAdmittedExplicitlyAndFingerprintKeysAreLocalToOneHost() {
        // Given
        var actor = new StudioIdentity("demo-editor", Set.of(), false);
        var scope = UUID.randomUUID();
        var policy = new DemoScenarioPolicy();
        var reference = new ScenarioInput(ScenarioInput.Kind.FIXTURE_REFERENCE, "demo/greeting", "1");
        var synthetic = new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, "  hello  ", null);
        // When / Then
        policy.assertSafeToStore(actor, scope, new ScenarioData(synthetic, null));
        policy.assertSafeToStore(actor, scope, new ScenarioData(reference, null));
        var resolved = policy.resolveFixture(actor, scope, reference);
        assertThat(resolved.value()).isEqualTo("  hello  ");
        assertThat(policy.resolveFixture(actor, scope, reference).fingerprint()).isEqualTo(resolved.fingerprint());
        assertThat(new DemoScenarioPolicy().resolveFixture(actor, scope, reference).fingerprint().keyId())
                .isNotEqualTo(resolved.fingerprint().keyId());
        policy.assertSafeToStore(actor, scope, new ScenarioData(synthetic,
                new ScenarioExpectation("SUCCEEDED", "HELLO")));
        assertThatThrownBy(() -> policy.assertSafeToStore(actor, scope,
                                                          new ScenarioData(synthetic,
                                                                  new ScenarioExpectation("SUCCEEDED",
                                                                          "private output"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.resolveFixture(actor, scope, synthetic))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.assertSafeToStore(actor, scope,
                                                          new ScenarioData(
                                                                  new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT,
                                                                          "unapproved", null),
                                                                  null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.assertSafeToStore(actor, scope,
                                                          new ScenarioData(new ScenarioInput(
                                                                  ScenarioInput.Kind.FIXTURE_REFERENCE, "demo/greeting",
                                                                  "2"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulationOptInAdmitsOnlyKnownCapabilitiesAndSyntheticMockValues() {
        // Given: the demo policy is intentionally narrower than arbitrary text.
        var actor = new StudioIdentity("demo-editor", Set.of(), false);
        var scope = UUID.randomUUID();
        var policy = new DemoScenarioPolicy();
        assertThat(policy.simulationEnabled()).isTrue();
        // When / Then
        for (String value : java.util.List.of("", "hello", "  hello  ", "HELLO", "5", "true")) {
            var mock = new io.github.gear4jtest.studio.model.SimulationProfile.Mock("text.trim",
                    io.github.gear4jtest.studio.model.SimulationProfile.Behavior.RETURN,
                    io.github.gear4jtest.studio.model.SimulationProfile.Scalar.STRING, value);
            assertThatCode(() -> policy.assertSafeSimulationToStore(actor, scope,
                                                                    new io.github.gear4jtest.studio.model.SimulationProfile(
                                                                            1, java.util.Map.of("trim", mock))))
                    .doesNotThrowAnyException();
        }
        for (String capability : java.util.List.of("text.trim", "unapproved")) {
            var mock = new io.github.gear4jtest.studio.model.SimulationProfile.Mock(capability,
                    io.github.gear4jtest.studio.model.SimulationProfile.Behavior.RETURN,
                    io.github.gear4jtest.studio.model.SimulationProfile.Scalar.STRING, "unapproved-value");
            assertThatThrownBy(() -> policy.assertSafeSimulationToStore(actor, scope,
                                                                        new io.github.gear4jtest.studio.model.SimulationProfile(
                                                                                1, java.util.Map.of("trim", mock))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        policy.assertSafeSimulationToStore(actor, scope, new io.github.gear4jtest.studio.model.SimulationProfile(1,
                java.util.Map.of("trim", new io.github.gear4jtest.studio.model.SimulationProfile.Mock("text.trim",
                        io.github.gear4jtest.studio.model.SimulationProfile.Behavior.FAIL, null, null))));
    }

    private HttpResponse<String> get(HttpClient client, String url, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url));
        if (token != null)
            request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
