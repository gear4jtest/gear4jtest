package io.github.gear4jtest.core.api;

/**
 * Interface publique du moteur d'exécution. Utile pour mocker l'engine dans tes
 * tests de contrôleurs API.
 */
public interface PipelineExecutor {
    <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest request);
}
