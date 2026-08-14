package io.github.gear4jtest.external.api.repository;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OperationChainPublicationTagsTest {
    @Test
    void normalize_shouldPreserveNullCompatibilityAndReturnSortedUniqueTags() {
        assertThat(OperationChainPublicationTags.normalize(null)).isEmpty();
        assertThat(OperationChainPublicationTags.normalize(List.of("xml", "fast", "xml")))
                .containsExactly("fast", "xml");
    }

    @Test
    void normalize_shouldRejectInvalidTagValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OperationChainPublicationTags.normalize(
                                                                          java.util.Arrays.asList("valid", null)))
                .withMessage("tag must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OperationChainPublicationTags.normalize(List.of(" ")))
                .withMessage("tag must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OperationChainPublicationTags.normalize(
                                                                          List.of("x"
                                                                                  .repeat(OperationChainPublicationTags.MAX_TAG_LENGTH
                                                                                          + 1))))
                .withMessage("tag must not exceed 100 characters");
    }

    @Test
    void normalize_shouldRejectMoreThanTheBoundedNumberOfInputTags() {
        List<String> tags = IntStream.range(0, OperationChainPublicationTags.MAX_TAGS_PER_PUBLICATION + 1)
                .mapToObj(index -> "tag-" + index)
                .toList();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> OperationChainPublicationTags.normalize(tags))
                .withMessage("publication must not contain more than 64 tags");
    }

    @Test
    void merge_shouldApplyTheLimitToThePersistedUnionAndNotToIdempotentRetries() {
        List<String> persistedTags = IntStream.range(0, OperationChainPublicationTags.MAX_TAGS_PER_PUBLICATION)
                .mapToObj(index -> "tag-" + index)
                .toList();

        assertThat(OperationChainPublicationTags.merge(persistedTags, List.of("tag-0")))
                .containsExactlyElementsOf(OperationChainPublicationTags.normalize(persistedTags));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OperationChainPublicationTags.merge(persistedTags, List.of("tag-new")))
                .withMessage("publication must not contain more than 64 tags");
    }
}
