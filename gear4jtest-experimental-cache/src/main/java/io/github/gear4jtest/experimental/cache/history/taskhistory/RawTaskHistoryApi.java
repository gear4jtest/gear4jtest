package io.github.gear4jtest.experimental.cache.history.taskhistory;

public interface RawTaskHistoryApi {
    <T> TaskHistoryResult<T> get(String key, Class<T> type);
}
