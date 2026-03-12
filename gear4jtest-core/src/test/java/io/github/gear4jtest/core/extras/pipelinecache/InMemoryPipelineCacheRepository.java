package io.github.gear4jtest.core.extras.pipelinecache;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPipelineCacheRepository implements PipelineCacheRepository {

  private final Map<PipelineCacheKey, PipelineCacheEntry<?>> entries = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public <OUT> Optional<PipelineCacheEntry<OUT>> findValid(PipelineCacheKey key, Instant now) {
    PipelineCacheEntry<?> entry = entries.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (!entry.isValidAt(now)) {
      return Optional.empty();
    }
    return Optional.of((PipelineCacheEntry<OUT>) entry);
  }

  @Override
  public <OUT> void save(PipelineCacheEntry<OUT> entry) {
    entries.put(entry.key(), entry);
  }
}
