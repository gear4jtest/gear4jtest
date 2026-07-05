package io.github.gear4jtest.experimental.cache.history.taskhistory;

public interface TaskHistoryApi {
    <T> TaskHistoryResult<T> get(String key, Class<T> type);
}
