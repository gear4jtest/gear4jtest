package io.github.gear4jtest.external.api.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import io.github.gear4jtest.external.api.model.OperationChainObject;

/**
 * Canonical encoder for internal operation-chain identities.
 *
 * <p>
 * The binary format starts with a format version. Every following component is
 * encoded as its four-byte big-endian UTF-8 byte length followed by the UTF-8
 * bytes. A domain component separates loader identities from publication-stage
 * identities.
 * </p>
 */
public final class OperationChainIdentityCodec {
    private static final byte FORMAT_VERSION = 1;
    private static final String LOADER_DOMAIN = "operation-chain-loader";
    private static final String PUBLICATION_STAGE_DOMAIN = "operation-chain-publication-stage";
    private static final String LOADER_ID_PREFIX = "g4j-loader-v1:";

    private OperationChainIdentityCodec() {
    }

    /**
     * Returns an opaque loader key including the artifact content identity.
     */
    public static String loaderId(OperationChainObject object) {
        OperationChainObject requiredObject = Objects.requireNonNull(object, "object must not be null");
        byte[] canonicalBytes = encode(LOADER_DOMAIN, requiredObject.alId(), requiredObject.version(),
                                       requiredObject.mode().name(), requiredObject.contentHash());
        return LOADER_ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(canonicalBytes);
    }

    /**
     * Returns the deterministic id of a staged publication.
     *
     * <p>
     * Content metadata is deliberately excluded: two attempts targeting the same
     * assembly-line id, version and mode must address the same stage so conflicting
     * content is rejected by the repository.
     * </p>
     */
    public static String publicationStageId(OperationChainObject object) {
        OperationChainObject requiredObject = Objects.requireNonNull(object, "object must not be null");
        byte[] canonicalBytes = encode(PUBLICATION_STAGE_DOMAIN, requiredObject.alId(), requiredObject.version(),
                                       requiredObject.mode().name());
        return UUID.nameUUIDFromBytes(canonicalBytes).toString();
    }

    private static byte[] encode(String domain, String... components) {
        byte[][] fields = new byte[components.length + 1][];
        fields[0] = utf8(domain);
        for (int index = 0; index < components.length; index++) {
            fields[index + 1] = utf8(components[index]);
        }

        long encodedSize = 1L;
        for (byte[] field : fields) {
            encodedSize += Integer.BYTES + (long) field.length;
        }
        if (encodedSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Canonical identity exceeds the maximum supported byte length");
        }

        ByteBuffer buffer = ByteBuffer.allocate((int) encodedSize);
        buffer.put(FORMAT_VERSION);
        for (byte[] field : fields) {
            buffer.putInt(field.length);
            buffer.put(field);
        }
        return buffer.array();
    }

    private static byte[] utf8(String value) {
        return Objects.requireNonNull(value, "identity component must not be null")
                .getBytes(StandardCharsets.UTF_8);
    }
}
