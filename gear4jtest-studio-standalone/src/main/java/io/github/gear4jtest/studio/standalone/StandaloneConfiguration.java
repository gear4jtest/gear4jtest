package io.github.gear4jtest.studio.standalone;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import io.github.gear4jtest.studio.management.StudioIdentity;

/**
 * Small local launcher configuration: immutable inventory and environment
 * references for per-user credentials. Production hosts may provide live
 * authentication/token-exchange implementations instead. Never use this token
 * map as an enterprise identity provider.
 */
public final class StandaloneConfiguration {
    private final TargetDirectory directory;
    private final Map<String, User> users;
    private final int port;

    private StandaloneConfiguration(TargetDirectory directory, Map<String, User> users, int port) {
        this.directory = directory;
        this.users = Map.copyOf(users);
        this.port = port;
    }

    public static StandaloneConfiguration load(InputStream input, Function<String, String> environment)
            throws java.io.IOException {
        byte[] bytes = input.readNBytes(65537);
        if (bytes.length > 65536)
            throw new IllegalArgumentException("Configuration limit");
        var properties = new Properties();
        properties.load(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        var consumed = new HashSet<String>();
        Function<String, String> value = key -> {
            consumed.add(key);
            String setting = properties.getProperty(key);
            if (setting == null || setting.isBlank())
                throw new IllegalArgumentException("Missing configuration setting: " + key);
            return setting.trim();
        };
        int port = Integer.parseInt(value.apply("port"));
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException("Invalid port");
        var applications = new ArrayList<StudioApplication>();
        for (String id : identifiers(value.apply("applications")))
            applications.add(new StudioApplication(id, value.apply("application." + id + ".name")));
        var targets = new ArrayList<StudioTarget>();
        for (String id : identifiers(value.apply("targets"))) {
            String prefix = "target." + id + ".";
            targets.add(new StudioTarget(id, value.apply(prefix + "application"), value.apply(prefix + "name"),
                    value.apply(prefix + "context"), URI.create(value.apply(prefix + "endpoint"))));
        }
        var knownTargets = targets.stream().map(StudioTarget::id).collect(java.util.stream.Collectors.toSet());
        var users = new HashMap<String, User>();
        for (String id : identifiers(value.apply("users"))) {
            String prefix = "user." + id + ".";
            String principalId = value.apply(prefix + "principalId");
            var identity = new StudioIdentity(principalId, Set.of(), false);
            var allowed = Set.copyOf(identifiers(value.apply(prefix + "targets")));
            if (!knownTargets.containsAll(allowed))
                throw new IllegalArgumentException("Unknown user target");
            String token = secret(environment, value.apply(prefix + "tokenEnvironment"));
            var remote = new HashMap<String, String>();
            for (String target : allowed)
                remote.put(target, secret(environment, value.apply(prefix + "remoteTokenEnvironment." + target)));
            var user = new User(identity, token.getBytes(StandardCharsets.UTF_8), Map.copyOf(remote));
            if (users.values().stream().anyMatch(u -> MessageDigest.isEqual(u.token, user.token))
                    || users.putIfAbsent(principalId, user) != null)
                throw new IllegalArgumentException("Ambiguous user credentials");
        }
        if (!consumed.equals(properties.stringPropertyNames()))
            throw new IllegalArgumentException("Unknown configuration setting");
        var directory = new TargetDirectory(applications, targets, (actor, target) -> {
            var user = users.get(actor.principalId());
            return user != null && user.remote.containsKey(target.id());
        });
        return new StandaloneConfiguration(directory, users, port);
    }

    private static List<String> identifiers(String value) {
        var values = java.util.Arrays.stream(value.split(",", -1)).map(String::trim).toList();
        values.forEach(TargetDirectory::identifier);
        if (values.isEmpty() || values.size() > 32 || new HashSet<>(values).size() != values.size())
            throw new IllegalArgumentException("Invalid configuration list");
        return values;
    }

    private static String secret(Function<String, String> environment, String key) {
        if (!key.matches("[A-Z][A-Z0-9_]{0,127}"))
            throw new IllegalArgumentException("Invalid credential reference");
        String token = environment.apply(key);
        if (token == null || token.length() < 24 || token.length() > 16384
                || token.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Credential reference is unavailable");
        return token;
    }

    public StudioIdentity authenticate(HttpExchange exchange) {
        final byte[] supplied;
        try {
            supplied = StandaloneStudioServer.bearer(exchange).getBytes(StandardCharsets.UTF_8);
        } catch (RemoteProblem invalid) {
            return null;
        }
        return users.values().stream().filter(u -> MessageDigest.isEqual(u.token, supplied)).map(u -> u.identity)
                .findFirst().orElse(null);
    }

    public TargetCredentials credentials() {
        return (actor, target, incoming) -> {
            var user = users.get(actor.principalId());
            return user == null ? null : user.remote.get(target.id());
        };
    }

    public TargetDirectory directory() {
        return directory;
    }

    public int port() {
        return port;
    }

    private static final class User {
        final StudioIdentity identity;
        final byte[] token;
        final Map<String, String> remote;

        User(StudioIdentity identity, byte[] token, Map<String, String> remote) {
            this.identity = identity;
            this.token = token;
            this.remote = remote;
        }
    }
}
