package io.github.gear4jtest.external.api.identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OperationChainIdentityCodecTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void canonicalIds_shouldDistinguishLegacyDelimiterCollisionCandidates() {
        // Given
        OperationChainObject first = object("a:b", "c", ExecutionMode.TEST, HASH_A);
        OperationChainObject second = object("a", "b:c", ExecutionMode.TEST, HASH_A);
        assertThat(legacyLoaderId(first)).isEqualTo(legacyLoaderId(second));

        // When / Then
        assertThat(OperationChainIdentityCodec.loaderId(first))
                .isNotEqualTo(OperationChainIdentityCodec.loaderId(second));
        assertThat(OperationChainIdentityCodec.publicationStageId(first))
                .isNotEqualTo(OperationChainIdentityCodec.publicationStageId(second));
    }

    @Test
    void canonicalIds_shouldRemainInjectiveAcrossSeparatorsUnicodeModesAndLimits() {
        // Given
        List<String> assemblyLineIds = List.of("a", "a:b", "pipeline/été", "é".repeat(200));
        List<String> versions = List.of("1", "b:c", "版本-δ", "版".repeat(100));
        List<String> loaderIds = new ArrayList<>();
        List<String> stageIds = new ArrayList<>();

        // When
        for (String assemblyLineId : assemblyLineIds) {
            for (String version : versions) {
                for (ExecutionMode mode : ExecutionMode.values()) {
                    OperationChainObject object = object(assemblyLineId, version, mode, HASH_A);
                    loaderIds.add(OperationChainIdentityCodec.loaderId(object));
                    stageIds.add(OperationChainIdentityCodec.publicationStageId(object));
                }
            }
        }

        // Then
        assertThat(loaderIds).doesNotHaveDuplicates();
        assertThat(stageIds).doesNotHaveDuplicates();
    }

    @Test
    void loaderId_shouldIncludeContentHashWhileStageIdUsesOnlyPublicationIdentity() {
        // Given
        OperationChainObject first = object("line", "1.0.0", ExecutionMode.RUN, HASH_A);
        OperationChainObject samePublicationWithDifferentContent = object("line", "1.0.0", ExecutionMode.RUN,
                                                                          HASH_B);

        // When / Then
        assertThat(OperationChainIdentityCodec.loaderId(first))
                .isNotEqualTo(OperationChainIdentityCodec.loaderId(samePublicationWithDifferentContent));
        assertThat(OperationChainIdentityCodec.publicationStageId(first))
                .isEqualTo(OperationChainIdentityCodec.publicationStageId(samePublicationWithDifferentContent));
    }

    @Test
    void canonicalIds_shouldBeDeterministicAndRejectNullObjects() {
        // Given
        OperationChainObject first = object("pipeline:été", "版本:1", ExecutionMode.TEST, HASH_A);
        OperationChainObject equivalent = object("pipeline:été", "版本:1", ExecutionMode.TEST, HASH_A);

        // When / Then
        assertThat(OperationChainIdentityCodec.loaderId(first))
                .isEqualTo(OperationChainIdentityCodec.loaderId(equivalent))
                .startsWith("g4j-loader-v1:");
        assertThat(OperationChainIdentityCodec.publicationStageId(first))
                .isEqualTo(OperationChainIdentityCodec.publicationStageId(equivalent));
        assertThatNullPointerException().isThrownBy(() -> OperationChainIdentityCodec.loaderId(null))
                .withMessage("object must not be null");
        assertThatNullPointerException().isThrownBy(() -> OperationChainIdentityCodec.publicationStageId(null))
                .withMessage("object must not be null");
    }

    private static String legacyLoaderId(OperationChainObject object) {
        return object.alId() + ":" + object.version() + ":" + object.mode() + ":" + object.contentHash();
    }

    private static OperationChainObject object(String assemblyLineId,
                                               String version,
                                               ExecutionMode mode,
                                               String hash) {
        return new OperationChainObject(null, assemblyLineId, version, mode, hash, 42L, "application/xml",
                Instant.EPOCH, "tester", Instant.EPOCH);
    }
}
