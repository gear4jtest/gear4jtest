package io.github.gear4jtest.studio.management;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.github.gear4jtest.studio.service.StudioProblem;

/**
 * Short-lived opaque continuations; restart invalidates them, never the stored
 * data.
 */
final class PageCursors {
    private final SecureRandom random = new SecureRandom();
    private final byte[] key = new byte[32];
    private final Clock clock;

    PageCursors(Clock clock) {
        this.clock = clock;
        random.nextBytes(key);
    }

    private byte[] aad(StudioIdentity actor, String scope) {
        var value = new StringBuilder();
        frame(value, actor.principalId());
        frame(value, Boolean.toString(actor.administrator()));
        frame(value, scope);
        actor.groups().stream().sorted().forEach(group -> frame(value, group));
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void frame(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    String encode(String position, StudioIdentity actor, String scope) {
        try {
            var nonce = new byte[12];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(actor, scope));
            var encrypted = cipher
                    .doFinal((clock.millis() + 900000L + ":" + position).getBytes(StandardCharsets.UTF_8));
            var buffer = java.nio.ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cursor encryption unavailable", e);
        }
    }

    String decode(String token, StudioIdentity actor, String scope) {
        if (token == null)
            return null;
        try {
            if (token.length() > 256)
                throw new IllegalArgumentException();
            var bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length < 29)
                throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, bytes, 0, 12));
            cipher.updateAAD(aad(actor, scope));
            var plain = new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8).split(":", 2);
            if (plain.length != 2 || Long.parseLong(plain[0]) < clock.millis())
                throw new IllegalArgumentException();
            return plain[1];
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new StudioProblem(StudioProblem.Code.INVALID_REQUEST);
        }
    }
}
