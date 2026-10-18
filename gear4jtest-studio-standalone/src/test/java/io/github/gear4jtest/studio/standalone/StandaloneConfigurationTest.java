package io.github.gear4jtest.studio.standalone;

import java.net.URI;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.studio.management.StudioIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StandaloneConfigurationTest {
    private static final String CONFIG = """
            port=0
            applications=app
            application.app.name=Application
            targets=target
            target.target.application=app
            target.target.name=Target
            target.target.context=runtime
            target.target.endpoint=https://example.invalid/management
            users=user
            user.user.subject=alice
            user.user.tokenEnvironment=STUDIO_TOKEN
            user.user.targets=target
            user.user.remoteTokenEnvironment.target=REMOTE_TOKEN
            """;

    private StandaloneConfiguration load(String content) throws Exception {
        return StandaloneConfiguration
                .load(new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                      key -> key.equals("STUDIO_TOKEN") ? "synthetic-studio-token-0123456789"
                              : "synthetic-remote-token-0123456789");
    }

    @Test
    void configurationContainsOnlyReferencesAndResolvesScopedCredentialsWithoutGrantingRuntimeRoles() throws Exception {
        // Given / When
        var configuration = load(CONFIG);
        var alice = new StudioIdentity("alice", Set.of(), false);
        var target = configuration.directory().require(alice, "target");
        // Then
        assertThat(configuration.port()).isZero();
        assertThat(configuration.directory().visible(alice)).hasSize(1);
        assertThat(configuration.directory().visible(new StudioIdentity("bob", Set.of(), true))).isEmpty();
        assertThat(configuration.credentials().tokenFor(alice, target, "unused"))
                .isEqualTo("synthetic-remote-token-0123456789");
        assertThat(configuration.credentials().tokenFor(new StudioIdentity("bob", Set.of(), false), target, "unused"))
                .isNull();
        assertThat(configuration.toString()).doesNotContain("synthetic");
        assertThat(configuration.directory().visible(alice).toString()).doesNotContain("example.invalid");
    }

    @Test
    void malformedOrAmbiguousRegistryAndCredentialConfigurationFailsAtStartup() {
        // Given / When / Then
        for (String invalid : List.of(CONFIG + "unknown=value\n", CONFIG.replace("port=0", "port=99999"),
                                      CONFIG.replace("users=user", "users=user,user"),
                                      CONFIG.replace("user.user.targets=target", "user.user.targets=missing"),
                                      CONFIG.replace("STUDIO_TOKEN", "literal-secret"),
                                      CONFIG.replace("applications=app", "applications=app,app"),
                                      CONFIG.replace("target.target.application=app",
                                                     "target.target.application=unknown")))
            assertThatThrownBy(() -> load(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StandaloneConfiguration.load(new java.io.ByteArrayInputStream(CONFIG.getBytes()),
                                                              key -> null))
                .hasMessage("Credential reference is unavailable");
        assertThatThrownBy(() -> load("x".repeat(65537))).hasMessage("Configuration limit");
        assertThatThrownBy(() -> StandaloneStudioApplication.main(new String[0]))
                .isInstanceOf(IllegalArgumentException.class);
        var app = new StudioApplication("app", "App");
        var target = new StudioTarget("target", "app", "Target", "runtime", URI.create("https://example.invalid"));
        assertThatThrownBy(() -> new TargetDirectory(List.of(app, app), List.of(target), (a, t) -> true))
                .hasMessage("Duplicate application");
        assertThatThrownBy(() -> new TargetDirectory(List.of(app), List.of(target, target), (a, t) -> true))
                .hasMessage("Ambiguous target binding");
        assertThatThrownBy(() -> new TargetDirectory(List.of(), List.of(target), (a, t) -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endpointAllowlistRejectsCredentialsInUrlsInsecureRemoteHttpAndPathEscapes() {
        // Given / When / Then
        for (String endpoint : List.of("file:///etc/passwd", "http://example.invalid",
                                       "https://user:secret@example.invalid",
                                       "https://example.invalid?url=other", "https://example.invalid/#fragment",
                                       "https://example.invalid/%2e%2e/admin",
                                       "https://example.invalid/../admin", "https://example.invalid/"))
            assertThatThrownBy(() -> new StudioTarget("target", "app", "Name", "runtime", URI.create(endpoint)))
                    .hasMessage("Invalid management target binding");
        assertThatThrownBy(() -> new StudioTarget("Target", "app", "Name", "runtime",
                URI.create("https://example.invalid")))
                .hasMessage("Invalid registry identifier");
        assertThatThrownBy(() -> new StudioApplication("app", "\n")).hasMessage("Invalid registry label");
    }
}
