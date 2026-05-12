// package io.github.gear4jtest.core;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import java.util.*;
// import java.util.concurrent.*;
//
// import io.github.gear4jtest.core.event.EventListener;
// import io.github.gear4jtest.core.api.util.ElementModelBuilders;
// import org.junit.jupiter.api.Test;
//
// import io.github.gear4jtest.core.event.*;
// import io.github.gear4jtest.core.execution.*;
// import io.github.gear4jtest.core.spi.factory.ResourceFactory;
// import io.github.gear4jtest.core.model.refactor.*;
// import io.github.gear4jtest.core.persistence.*;
//
// class FullPipelineIntegrationTest {
//
// static class StatefulAdder implements Transformer<Integer, Integer>,
// ConcurrencyAwareTransformer {
// OperationParamsInjector.Parameter<Integer> delta =
// OperationParamsInjector.Parameter.ofDefault(1);
//
// @Override public TransformerStatefulness statefulness() { return
// TransformerStatefulness.STATEFUL; }
//
// @Override
// public Integer transform(Integer input, ExecutionContext ctx,
// OperationExecutionContext op) {
// return input + delta.getValue();
// }
// }
//
// static class Multiply implements Transformer<Integer, Integer> {
// @Override
// public Integer transform(Integer input, ExecutionContext ctx,
// OperationExecutionContext op) {
// return input * 2;
// }
// }
//
// @Test
// void
// fullPipeline_shouldProcessIterator_thenConditional_thenFinalOp_withEvents_andPersistence()
// throws
// InterruptedException {
// // ---------- EventBus ----------
// BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();
//
// EventListener<Event> collector = eventQueue::add;
// SimpleEventBus bus = new SimpleEventBus("bus", List.of(e -> true),
// List.of(collector));
//
// Thread busThread = new Thread(bus::run);
// busThread.start();
//
// EventManager eventManager = new EventManager(List.of(bus));
//
// // ---------- ResourceFactory ----------
// ResourceFactory factory = new TestResourceFactory();
//
// // ---------- Execution Manager ----------
// InMemoryExecutionManager execManager = new InMemoryExecutionManager();
//
// ExecutionContext ctx = new ExecutionContext("pipe-main", eventManager,
// factory, execManager);
//
// // ---------- Build pipeline ----------
// // Iterator → add +1 → multiply *2 → conditional YES/NO → end
//
// // Step 1 : Iterator on [1,2,3]
// IteratorDefinition<Integer, Integer> iterator =
// ElementModelBuilders.iterate("iter")
// .accumulator(new IteratorDefinition.ListAccumulator())
// .
// .itemTransformer(StatefulAdder.class)
// .flushPolicy(FlushPolicy.AFTER_EACH)
// .granularity(ReportGranularity.ITEM)
// .build();
//
// // Step 2 : Multiply all items
// ProcessingOperationDefinition<Integer, Integer> multiply =
// ProcessingOperationDefinition.<Integer, Integer, Multiply>builder()
// .id("mul")
// .type(Multiply.class)
// .build();
//
// // Step 3 : Unary IF/ELSE
// Condition<List<Integer>> hasEven =
// (in, c) -> in.stream().anyMatch(x -> x % 2 == 0);
//
// UnaryIfElseContainerDefinition<List<Integer>> cond =
// UnaryIfElseContainerDefinition.<List<Integer>>builder()
// .conditionally(new ConstantOperation("YES", "OK"), hasEven)
// .elseOp(new ConstantOperation("NO", "KO"))
// .build();
//
// // Step 4 : Terminal op
// ProcessingOperationDefinition<String, String> finalOp =
// ProcessingOperationDefinition.<String, String, MultiplyTerminal>builder()
// .id("end")
// .type(MultiplyTerminal.class)
// .build();
//
// AssemblyLineDefinition<List<Integer>, String> pipeline =
// AssemblyLineDefinition.<List<Integer>, String>builder()
// .id("pipe-main")
// .resourceFactory(factory)
// .then(iterator)
// .then(multiply)
// .then(cond)
// .then(finalOp)
// .build();
//
// // ---------- Execute ----------
// ExecutionResult<String> res =
// pipeline.execute(List.of(1, 2, 3), Map.of(), factory);
//
// assertThat(res.isSuccess()).isTrue();
//
// // ---------- Check events ----------
// bus.stopBus();
// try { busThread.join(2000); } catch (InterruptedException ignored) {}
//
// long startedCount = eventQueue.stream().filter(e -> e instanceof
// OperationStartedEvent).count();
// long completedCount = eventQueue.stream().filter(e -> e instanceof
// OperationCompletedEvent).count();
//
// assertThat(startedCount).isGreaterThan(0);
// assertThat(completedCount).isGreaterThan(0);
//
// // ---------- Check persistence ----------
// var opt =
// InMemoryPipelineExecutionRepository.INSTANCE.findById(res.getExecutionId());
// assertThat(opt).isPresent();
//
// PipelineExecution exec = opt.get();
// assertThat(exec.getOperations().size()).isGreaterThanOrEqualTo(4); //
// iterator has multiple ops
//
// assertThat(res.getResult()).isIn("OK", "KO");
// }
//
// // Terminal transformer
// static class MultiplyTerminal implements Transformer<String, String> {
// @Override
// public String transform(String input, ExecutionContext c,
// OperationExecutionContext op) {
// return "DONE:" + input;
// }
// }
//
// static class ConstantOperation extends
// AbstractOperationDefinition<List<Integer>, String> {
// private final String result;
// ConstantOperation(String id, String result) {
// super(id, OperationKind.PROCESSING);
// this.result = result;
// }
// @Override protected void setUp(List<Integer> x, ExecutionContext c,
// OperationExecutionContext oc) {}
// @Override protected String doExecute(List<Integer> x, ExecutionContext c,
// OperationExecutionContext oc) { return
// result; }
// }
//
// public static class TestResourceFactory implements ResourceFactory {
// @Override
// public <T> T getResource(Class<T> clazz) {
// if (clazz.equals(StatefulAdder.class)) return (T) new StatefulAdder();
// if (clazz.equals(Multiply.class)) return (T) new Multiply();
// throw new IllegalArgumentException("Unknown resource " + clazz);
// }
// }
// }
