package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.persistence.AssemblyRun;

public class PipelineOperation<IN, OUT> extends AbstractStation<IN, OUT> {

    private final AssemblyLine<IN, OUT> subPipeline;

    public PipelineOperation(String id, AssemblyLine<IN, OUT> subPipeline) {
        super(id, StationKind.PIPELINE);
        this.subPipeline = subPipeline;
    }

    @Override
    public OUT doExecute(IN input, ExecutionContext parentCtx, StationExecutionContext opExecCtx) {
        AssemblyRun parentExec = parentCtx.getPipelineExecution();
        AssemblyRun childExec = AssemblyRun.childOf(parentExec, subPipeline.getId());

        AssemblyRunManager manager = parentCtx.getExecutionManager();
        manager.start(childExec);

        // Contexte enfant : on partage ou copie le contexte métier, à toi de décider. A REVOIR
        ExecutionContext childCtx = parentCtx;

        // On propage l'itemId courant (corrélation cross-pipeline)
        childCtx.setCurrentItemId(parentCtx.getCurrentItemId());

        ExecutionResult<OUT> result = subPipeline.executeWithin(childCtx, input);

        if (result.isSuccess()) {
            childExec.markSuccess(result.getResult());
        } else {
            childExec.markFailed(null);
            opExecCtx.getRecord().markFailed(null);
        }

        manager.end(childExec);
        return result.getResult();
    }

    // DSL builder
    public static class Builder<IN, OUT> {
        private final String id;
        private final AssemblyLine<IN, OUT> subPipeline;

        public Builder(String id, AssemblyLine<IN, OUT> subPipeline) {
            this.id = id;
            this.subPipeline = subPipeline;
        }

        public PipelineOperation<IN, OUT> build() {
            return new PipelineOperation<>(id, subPipeline);
        }
    }
}
