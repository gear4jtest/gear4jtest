package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

public class OperationChain<IN, OUT> {

    private final List<OperationDefinition<?, ?>> steps = new ArrayList<>();

    public List<OperationDefinition<?, ?>> getSteps() {
        return steps;
    }

    public OperationChain<IN, OUT> then(OperationDefinition<?, ?> step) {
        steps.add(step);
        return this;
    }

    @SuppressWarnings("unchecked")
    public OperationChainResult<OUT> execute(IN input, ExecutionContext ctx) {
        OperationExecutionRecord rec = null;
        Object in = input;
        boolean success = true;

        for (OperationDefinition<?, ?> step : steps) {
            OperationDefinition<Object, Object> typed = (OperationDefinition<Object, Object>) step;
            rec = typed.run(in, ctx);
//            ctx.getExecutionManager().append(rec);

            if (rec.getStatus() == OperationExecutionRecord.Status.FAILED
                    || rec.getStatus() == OperationExecutionRecord.Status.STOPPED) {
                success = false;
                break;
            }

            in = rec.getOutput(Object.class);
        }
        return new OperationChainResult<>((OUT) in, rec, success);
    }


    public static class Builder<IN, OUT> {
        private final OperationChain<IN, OUT> chain = new OperationChain<>();

        public Builder(OperationDefinition<IN, OUT> step) {
            this.chain.then(step);
        }

        public <A> Builder<OUT, A> then(OperationDefinition<OUT, A> step) {
            chain.then(step);
            return (Builder<OUT, A>) this;
        }

        public OperationChain<IN, OUT> build() {
            return chain;
        }
    }
}
