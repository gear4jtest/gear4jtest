package io.github.gear4jtest.external.api.storage;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactStoreConfigurationFingerprintTest {
    @Test
    void fingerprint_shouldBeStableAcrossPropertyOrderAndChangeWithStoreConfiguration() {
        Map<String, String> firstOrder = new LinkedHashMap<>();
        firstOrder.put("bucket", "artifacts");
        firstOrder.put("region", "eu-west-1");
        Map<String, String> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("region", "eu-west-1");
        reverseOrder.put("bucket", "artifacts");

        String first = ArtifactStoreConfigurationFingerprint.from(
                                                                  new OperationChainConfig("line", false, StoreType.S3,
                                                                          firstOrder));
        String reordered = ArtifactStoreConfigurationFingerprint.from(
                                                                      new OperationChainConfig("line", true,
                                                                              StoreType.S3, reverseOrder));
        String changed = ArtifactStoreConfigurationFingerprint.from(
                                                                    new OperationChainConfig("line", false,
                                                                            StoreType.S3,
                                                                            Map.of("bucket", "other", "region",
                                                                                   "eu-west-1")));

        assertThat(first).hasSize(64).isEqualTo(reordered).isNotEqualTo(changed);
    }
}
