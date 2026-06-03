package io.github.gear4jtest.core.extras.history.taskhistory;

public interface TaskHistoryApi {
    <T> TaskHistoryResult<T> get(String key, Class<T> type);
}
