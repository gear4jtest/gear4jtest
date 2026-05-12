package io.github.gear4jtest.core.extras.pipelinecache;

import java.time.Instant;
import java.util.Optional;

public interface PipelineCacheRepository {
    <OUT> Optional<PipelineCacheEntry<OUT>> findValid(PipelineCacheKey key, Instant now);

    <OUT> void save(PipelineCacheEntry<OUT> entry);
}
