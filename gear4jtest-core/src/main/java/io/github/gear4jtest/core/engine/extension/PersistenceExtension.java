package io.github.gear4jtest.core.engine.extension;

import java.time.Instant;

import io.github.gear4jtest.core.engine.core.RunRequest;
import io.github.gear4jtest.core.engine.feature.PersistenceFeature;
import io.github.gear4jtest.core.engine.spi.PersistingStationRunner;
import io.github.gear4jtest.core.engine.spi.RuntimeExtension;
import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.persistence.ExecutionStatus;

public class PersistenceExtension implements RuntimeExtension {

    private final AssemblyRunManager manager;

    public PersistenceExtension(AssemblyRunManager manager) {
        this.manager = manager;
    }

    @Override
    public void prepare(ExecutionContext ctx, RunRequest request) {
        // 1. Récupération sécurisée de la Feature
        // On sait qu'elle est là car le Builder ne nous appelle que si la clé est présente
//        PersistenceFeature feature = request.getFeature(PersistenceFeature.class)
//                .orElseThrow(() -> new IllegalStateException("PersistenceExtension active sans PersistenceFeature !"));

        // 2. Récupération du Manager fourni par l'utilisateur
//        AssemblyRunManager manager = feature.getManager();

        // 3. Injection dans le contexte (clé standardisée = nom de la classe interface)
//        ctx.registerResource(AssemblyRunManager.class.getName(), manager);

        // 4. Démarrage du cycle de vie
//        manager.start(ctx.getPipelineExecution()); // ou ctx.getAssemblyRun() selon ton nommage
    }

    @Override
    public StationRunner decorate(StationRunner current, ExecutionContext ctx) {
        // Récupération du manager injecté
//        AssemblyRunManager manager = ctx.getResource(AssemblyRunManager.class.getName(), AssemblyRunManager.class);
        
        // Retourne le wrapper qui intercepte les logs
        return new PersistingStationRunner(current, manager);
    }

    @Override
    public void onStart(ExecutionContext ctx) {
        manager.start(ctx.getPipelineExecution());
    }

    @Override
    public void onSuccess(ExecutionContext ctx, Object result) {
        ctx.getPipelineExecution().setResult(result);
    }

    @Override
    public void onEnd(ExecutionContext ctx) {
        ctx.getPipelineExecution().setContext(ctx.getContext());
        ctx.getPipelineExecution().setEndTime(Instant.now());
        ctx.getPipelineExecution().setStatus(ExecutionStatus.SUCCEEDED);
        manager.end(ctx.getPipelineExecution());
    }
}