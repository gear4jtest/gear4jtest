package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.persistence.StationLog;

public class StationChain<IN, OUT> {

    private final List<Station<?, ?>> steps = new ArrayList<>();

    public List<Station<?, ?>> getSteps() {
        return steps;
    }

    public StationChain<IN, OUT> then(Station<?, ?> step) {
        steps.add(step);
        return this;
    }

    @SuppressWarnings("unchecked")
    public StationChainResult<OUT> execute(IN input, ExecutionContext ctx) {
        StationLog rec = null;
        Object in = input;
        boolean success = true;

        for (Station<?, ?> step : steps) {
            Station<Object, Object> typed = (Station<Object, Object>) step;
            rec = typed.run(in, ctx);
//            ctx.getExecutionManager().append(rec);

            if (rec.getStatus() == StationLog.Status.FAILED
                    || rec.getStatus() == StationLog.Status.STOPPED) {
                success = false;
                break;
            }

            in = rec.getOutput(Object.class);
        }
        return new StationChainResult<>((OUT) in, rec, success);
    }


    public static class Builder<IN, OUT> {
        private final StationChain<IN, OUT> chain = new StationChain<>();

        public Builder(Station<IN, OUT> step) {
            this.chain.then(step);
        }

        public <A> Builder<OUT, A> then(Station<OUT, A> step) {
            chain.then(step);
            return (Builder<OUT, A>) this;
        }

        public StationChain<IN, OUT> build() {
            return chain;
        }
    }
}
