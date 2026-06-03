package io.github.gear4jtest.core.extras.history.taskhistory;

public interface RawTaskHistoryApi {
    <T> TaskHistoryResult<T> get(String key, Class<T> type);
}
