// package io.github.gear4jtest.core.model;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import java.util.Optional;
// import java.util.UUID;
//
// import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
// import io.github.gear4jtest.core.api.context.ExecutionContext;
// import io.github.gear4jtest.core.api.behavior.Operator;
// import io.github.gear4jtest.core.api.context.StationContextUtils;
// import io.github.gear4jtest.core.api.context.StationExecutionContext;
// import io.github.gear4jtest.core.api.station.StationKind;
// import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
// import org.junit.jupiter.api.Test;
//
// import io.github.gear4jtest.core.execution.trace.StationLogTrace;
//
// class StationContextUtilsTest {
//
// static class StringLengthOperator implements Operator<String, Integer> {
// @Override
// public Integer transform(String input,
// ExecutionContext context,
// StationExecutionContext operationExecution) {
// return input != null ? input.length() : 0;
// }
// }
//
// @Test
// void isProcessing_shouldReturnTrueOnlyForProcessingKind() {
// ExecutionContext global =
// new ExecutionContext(UUID.randomUUID(), "pipe", null, null, null, null);
// StationLogTrace record =
// StationLogTrace.start("exec", "op", null);
//
// DefaultStationExecutionContext ctxProcessing =
// new DefaultStationExecutionContext("op", StationKind.PROCESSING, global,
// record);
// DefaultStationExecutionContext ctxIterator =
// new DefaultStationExecutionContext("op", StationKind.ITERATOR, global,
// record);
//
// assertThat(StationContextUtils.isProcessing(ctxProcessing)).isTrue();
// assertThat(StationContextUtils.isProcessing(ctxIterator)).isFalse();
// }
//
// @Test
// void getRawAndTypedTransformer_shouldReadFromCapabilities() {
// ExecutionContext global =
// new ExecutionContext(UUID.randomUUID(), "pipe", null, null, null, null);
// StationLogTrace record =
// StationLogTrace.start("exec", "op", null);
// DefaultStationExecutionContext ctx =
// new DefaultStationExecutionContext("op", StationKind.PROCESSING, global,
// record);
//
// StringLengthOperator transformer = new StringLengthOperator();
// ctx.addCapability(Operator.class, transformer);
//
// Optional<Operator<?, ?>> raw = StationContextUtils.getRawTransformer(ctx);
// Optional<Operator<String, Integer>> typed =
// StationContextUtils.getTypedTransformer(ctx);
//
// assertThat(raw).contains(transformer);
// assertThat(typed).contains(transformer);
// }
//
// @Test
// void getProcessingParameters_shouldReturnParametersCapabilityIfPresent() {
// ExecutionContext global =
// new ExecutionContext(UUID.randomUUID(), "pipe", null, null, null, null);
// StationLogTrace record =
// StationLogTrace.start("exec", "op", null);
// DefaultStationExecutionContext ctx =
// new DefaultStationExecutionContext("op", StationKind.PROCESSING, global,
// record);
//
// assertThat(StationContextUtils.getProcessingParameters(ctx)).isEmpty();
//
// WorkerParamsInjector.Parameters params =
// WorkerParamsInjector.Parameters.newBuilder().build();
// ctx.addCapability(WorkerParamsInjector.Parameters.class, params);
//
// assertThat(StationContextUtils.getProcessingParameters(ctx))
// .contains(params);
// }
// }
