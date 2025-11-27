package io.github.gear4jtest.core.model.refactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

class OperationContextUtilsTest {

    static class StringLengthTransformer implements Transformer<String, Integer> {
        @Override
        public Integer transform(String input,
                                 ExecutionContext context,
                                 OperationExecutionContext operationExecution) {
            return input != null ? input.length() : 0;
        }
    }

    @Test
    void isProcessing_shouldReturnTrueOnlyForProcessingKind() {
        ExecutionContext global =
                new ExecutionContext("pipe", null, null, null);
        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec", "op", null);

        DefaultOperationExecutionContext ctxProcessing =
                new DefaultOperationExecutionContext("op", OperationKind.PROCESSING, global, record);
        DefaultOperationExecutionContext ctxIterator =
                new DefaultOperationExecutionContext("op", OperationKind.ITERATOR, global, record);

        assertThat(OperationContextUtils.isProcessing(ctxProcessing)).isTrue();
        assertThat(OperationContextUtils.isProcessing(ctxIterator)).isFalse();
    }

    @Test
    void getRawAndTypedTransformer_shouldReadFromCapabilities() {
        ExecutionContext global =
                new ExecutionContext("pipe", null, null, null);
        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec", "op", null);
        DefaultOperationExecutionContext ctx =
                new DefaultOperationExecutionContext("op", OperationKind.PROCESSING, global, record);

        StringLengthTransformer transformer = new StringLengthTransformer();
        ctx.addCapability(Transformer.class, transformer);

        Optional<Transformer<?, ?>> raw = OperationContextUtils.getRawTransformer(ctx);
        Optional<Transformer<String, Integer>> typed =
                OperationContextUtils.getTypedTransformer(ctx);

        assertThat(raw).contains(transformer);
        assertThat(typed).contains(transformer);
    }

    @Test
    void getProcessingParameters_shouldReturnParametersCapabilityIfPresent() {
        ExecutionContext global =
                new ExecutionContext("pipe", null, null, null);
        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec", "op", null);
        DefaultOperationExecutionContext ctx =
                new DefaultOperationExecutionContext("op", OperationKind.PROCESSING, global, record);

        assertThat(OperationContextUtils.getProcessingParameters(ctx)).isEmpty();

        OperationParamsInjector.Parameters params =
                OperationParamsInjector.Parameters.newBuilder().build();
        ctx.addCapability(OperationParamsInjector.Parameters.class, params);

        assertThat(OperationContextUtils.getProcessingParameters(ctx))
                .contains(params);
    }
}
