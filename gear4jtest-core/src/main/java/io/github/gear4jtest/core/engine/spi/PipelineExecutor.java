package io.github.gear4jtest.core.engine.spi;


import io.github.gear4jtest.core.engine.core.RunRequest;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ExecutionResult;

/**
 * Interface publique du moteur d'exécution.
 * Utile pour mocker l'engine dans tes tests de contrôleurs API.
 */
public interface PipelineExecutor {
    <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest request);
}