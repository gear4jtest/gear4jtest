package io.github.gear4jtest.external.api.repository;

import java.util.List;
import java.util.TreeSet;

import io.github.gear4jtest.core.api.annotation.Internal;

/**
 * Shared validation for publication tags persisted by Gear4J repositories.
 *
 * <p>
 * This infrastructure helper is public only so provider modules can apply the
 * same limits before opening a transaction. It is not part of the application
 * API compatibility contract.
 * </p>
 */
@Internal
public final class OperationChainPublicationTags {
    /** Maximum number of tag entries accepted in one publication request. */
    public static final int MAX_TAGS_PER_PUBLICATION = 64;
    /** Maximum tag length, aligned with the external JDBC schema. */
    public static final int MAX_TAG_LENGTH = 100;

    private OperationChainPublicationTags() {
    }

    /**
     * Validates, deduplicates and sorts publication tags.
     *
     * <p>
     * A {@code null} list retains the historical meaning of no tags.
     * </p>
     */
    public static List<String> normalize(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        if (tags.size() > MAX_TAGS_PER_PUBLICATION) {
            throw new IllegalArgumentException(
                    "publication must not contain more than " + MAX_TAGS_PER_PUBLICATION + " tags");
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String tag : tags) {
            normalized.add(requireValidTag(tag));
        }
        return List.copyOf(normalized);
    }

    /** Merges two already bounded tag sets while enforcing the persisted total. */
    public static List<String> merge(List<String> existing, List<String> additional) {
        TreeSet<String> merged = new TreeSet<>();
        merged.addAll(normalize(existing));
        merged.addAll(normalize(additional));
        if (merged.size() > MAX_TAGS_PER_PUBLICATION) {
            throw new IllegalArgumentException(
                    "publication must not contain more than " + MAX_TAGS_PER_PUBLICATION + " tags");
        }
        return List.copyOf(merged);
    }

    /** Validates one tag against the publication schema contract. */
    public static String requireValidTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        if (tag.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException("tag must not exceed " + MAX_TAG_LENGTH + " characters");
        }
        return tag;
    }
}
