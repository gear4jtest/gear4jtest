package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.execution.PipelineExecutionManager;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class PipelineOperation<IN, OUT> extends AbstractOperationDefinition<IN, OUT> {

    private final AssemblyLineDefinition<IN, OUT> subPipeline;

    public PipelineOperation(String id, AssemblyLineDefinition<IN, OUT> subPipeline) {
        super(id, OperationKind.PIPELINE);
        this.subPipeline = subPipeline;
    }

    @Override
    public OUT doExecute(IN input, ExecutionContext parentCtx, OperationExecutionContext opExecCtx) {
        PipelineExecution parentExec = parentCtx.getPipelineExecution();
        PipelineExecution childExec = PipelineExecution.childOf(parentExec, subPipeline.getId());

        PipelineExecutionManager manager = parentCtx.getExecutionManager();
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
        private final AssemblyLineDefinition<IN, OUT> subPipeline;

        public Builder(String id, AssemblyLineDefinition<IN, OUT> subPipeline) {
            this.id = id;
            this.subPipeline = subPipeline;
        }

        public PipelineOperation<IN, OUT> build() {
            return new PipelineOperation<>(id, subPipeline);
        }
    }
}
