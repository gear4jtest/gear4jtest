package io.github.gear4jtest.studio.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * Authenticated identity supplied by the host, never by a management request.
 * principalId is stable and globally scoped within the management federation. A
 * raw OIDC sub is not a principalId. Hosts must agree on the same mapping,
 * including when their identity provider uses client-specific pairwise
 * subjects. Groups and administrator status are always evaluated by the
 * receiving host.
 */
public record StudioIdentity(String principalId, Set<String> groups, boolean administrator) {
    public StudioIdentity {
        identifier(principalId);
        groups = Set.copyOf(groups);
        if (groups.size() > 64)
            throw new IllegalArgumentException("Too many groups");
        groups.forEach(StudioIdentity::identifier);
    }

    /**
     * Deterministic scoped identity for an already authenticated authority/subject
     * pair. Does not authenticate tokens or normalize issuer URLs. Persisted ACLs
     * must use the resulting principalId, not the original subject.
     */
    public static StudioIdentity fromScopedSubject(String authority,
                                                   String subject,
                                                   Set<String> groups,
                                                   boolean administrator) {
        Objects.requireNonNull(authority);
        Objects.requireNonNull(subject);
        if (authority.isBlank() || authority.length() > 2048 || subject.isBlank() || subject.length() > 255
                || authority.chars().anyMatch(Character::isISOControl)
                || subject.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid scoped identity");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update("Gear4J principal v1\0".getBytes(StandardCharsets.UTF_8));
            frame(digest, authority);
            frame(digest, subject);
            return new StudioIdentity("scoped:" + HexFormat.of().formatHex(digest.digest()), groups, administrator);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static void frame(MessageDigest digest, String value) {
        byte[] bytes = utf8(value);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static byte[] utf8(String value) {
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(value));
            var bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid principal Unicode");
        }
    }

    static void identifier(String value) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.length() > 255 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid principal identifier");
        utf8(value);
    }
}
