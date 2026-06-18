package io.github.gear4jtest.external.api.repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.gear4jtest.core.persistence.PageRequest;

public interface OperationChainTagRepository {
    void addTag(String alId, String tag);

    void removeTag(String alId, String tag);

    Set<String> listTags(String alId);

    /**
     * Lists a bounded, zero-based page of tags for one assembly line.
     */
    default Set<String> listTags(String alId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return listTags(alId).stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.limit())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    List<String> findAssemblyLineIdsByTag(String tag);

    /**
     * Finds a bounded, zero-based page of assembly-line ids associated with a tag.
     */
    default List<String> findAssemblyLineIdsByTag(String tag, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return findAssemblyLineIdsByTag(tag).stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.limit())
                .toList();
    }
}
