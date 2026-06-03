package io.test.gear4jtest.external.api.repository;

import java.util.List;
import java.util.Set;

public interface OperationChainTagRepository {
    void addTag(String alId, String tag);

    void removeTag(String alId, String tag);

    Set<String> listTags(String alId);

    List<String> findAssemblyLineIdsByTag(String tag);
}
