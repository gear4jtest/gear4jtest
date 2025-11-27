//package io.github.gear4jtest.core.model.refactor;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import java.util.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import io.github.gear4jtest.core.execution.ReportGranularity;
//import org.junit.jupiter.api.Test;
//
//import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
//import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
//
//class IteratorDefinitionIntegrationTest {
//
//    static class MultiplyTransformer implements Transformer<Integer, Integer> {
//
//        OperationParamsInjector.Parameter<Integer> factor = OperationParamsInjector.Parameter.<Integer>newBuilder().defaultValue(1).build();
//
//        @Override
//        public Integer transform(Integer input, ExecutionContext context, OperationExecutionContext op) {
//            return input * factor.getValue();
//        }
//    }
//
//    @Test
//    void iterator_shouldIterateList_applyTransformer_flushPerItem_andPersistEachRecord() {
//        var execManager = new InMemoryExecutionManager();
//        var ctx = new ExecutionContext("iter-p", null, c -> new MultiplyTransformer(), execManager);
//
//        IteratorDefinition.Builder<Integer, Integer, MultiplyTransformer> builder =
//                new IteratorDefinition.Builder<>();
//
//        // Accumulator = List
//        IteratorDefinition<Integer, Integer> it =
//                builder
//                        .id("iter")
//                        .source(List.of(1, 2, 3))
//                        .accumulator(new IteratorDefinition.ListAccumulator())
//                        .itemTransformer(MultiplyTransformer.class)
//                        .flushPolicy(FlushPolicy.AFTER_EACH)
//                        .granularity(ReportGranularity.ITEM)
//                        .build();
//
//        OperationExecutionRecord record = it.run(42, ctx);
//
//        assertThat(record.getStatus()).isEqualTo(OperationExecutionRecord.Status.SUCCEEDED);
//        assertThat(record.getOutput(List.class)).containsExactly(1, 2, 3);
//
//        var execOpt = execManager.findById(record.getExecutionId());
//        assertThat(execOpt).isPresent();
//        assertThat(execOpt.get().getOperations()).hasSize(3); // un record par item
//    }
//
//    @Test
//    void iterator_shouldAccumulateIntoSet_andComputeResults() {
//        var execManager = new InMemoryExecutionManager();
//        var ctx = new ExecutionContext("iter-p2", null, c -> new MultiplyTransformer(), execManager);
//
//        IteratorDefinition.Builder<Integer, Integer, MultiplyTransformer> builder =
//                new IteratorDefinition.Builder<>();
//
//        IteratorDefinition<Integer, Integer> it =
//                builder
//                        .id("iter-set")
//                        .source(List.of(2, 2, 3))     // duplicate values
//                        .accumulator(new IteratorDefinition.SetAccumulator())
//                        .itemTransformer(MultiplyTransformer.class)
//                        .flushPolicy(FlushPolicy.END)
//                        .granularity(ReportGranularity.SUMMARY)
//                        .build();
//
//        OperationExecutionRecord rec = it.run(null, ctx);
//        Set<Integer> out = rec.getOutput(Set.class);
//
//        assertThat(out).containsExactlyInAnyOrder(2, 3);
//    }
//}
