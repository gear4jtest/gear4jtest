// package io.github.gear4jtest.core.service;
//
// import java.time.Duration;
// import java.time.Instant;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.atomic.AtomicInteger;
//
// import io.github.gear4jtest.core.api.AssemblyLine;
// import io.github.gear4jtest.core.api.ExecutionResult;
// import io.github.gear4jtest.core.api.RunRequest;
// import io.github.gear4jtest.core.api.behavior.Operator;
// import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
// import io.github.gear4jtest.core.api.context.ExecutionContext;
// import io.github.gear4jtest.core.api.context.StationExecutionContext;
// import io.github.gear4jtest.core.api.station.WorkStation;
// import io.github.gear4jtest.core.api.util.ElementModelBuilders;
// import io.github.gear4jtest.core.engine.PipelineEngine;
// import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
// import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
// import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
// import io.github.gear4jtest.core.event.Event;
// import io.github.gear4jtest.core.event.EventBus;
// import io.github.gear4jtest.core.event.EventListener;
// import io.github.gear4jtest.core.event.OperationCompletedEvent;
// import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
// import io.github.gear4jtest.core.extras.history.ExpirableDependencyTracker;
// import
// io.github.gear4jtest.core.extras.history.fingerprint.JsonSha256FingerprintStrategy;
// import
// io.github.gear4jtest.core.extras.history.fingerprint.WhitelistedContextFingerprintStrategy;
// import
// io.github.gear4jtest.core.extras.history.taskhistory.RawTaskHistoryApi;
// import io.github.gear4jtest.core.extras.history.taskhistory.TaskHistoryApi;
// import
// io.github.gear4jtest.core.extras.history.taskhistory.TaskHistoryResult;
// import
// io.github.gear4jtest.core.extras.history.taskhistory.TrackingTaskHistoryApi;
// import
// io.github.gear4jtest.core.extras.pipelinecache.InMemoryPipelineCacheRepository;
// import
// io.github.gear4jtest.core.extras.pipelinecache.NoDependencyCachePolicy;
// import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheExtension;
// import
// io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheKeyFactory;
// import io.github.gear4jtest.core.extras.pipelinecache.PipelineCachePolicy;
// import
// io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheRuntimeKeys;
// import io.github.gear4jtest.core.sidecompute.SideComputeHandler;
// import io.github.gear4jtest.core.sidecompute.SideComputeListener;
// import io.github.gear4jtest.core.sidecompute.SideComputer;
// import io.github.gear4jtest.core.spi.factory.ResourceFactory;
// import org.junit.jupiter.api.Test;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// class PipelineCacheEndToEndIT {
//
// @Test
// void should_execute_pipeline_once_then_serve_second_run_from_cache() {
// // given
// InMemoryPipelineCacheRepository cacheRepository = new
// InMemoryPipelineCacheRepository();
//
// PipelineCacheExtension cacheExtension =
// new PipelineCacheExtension(
// new PipelineCachePolicy(
// true,
// NoDependencyCachePolicy.DO_NOT_CACHE,
// null),
// new PipelineCacheKeyFactory(
// new JsonSha256FingerprintStrategy<>(),
// new WhitelistedContextFingerprintStrategy(
// List.of("tenantId"),
// new JsonSha256FingerprintStrategy<>())),
// cacheRepository);
//
// FakeRawTaskHistoryApi rawTaskHistoryApi = new FakeRawTaskHistoryApi(
// Map.of(
// "customer:42", new TaskHistoryResult<>(new CustomerDto("John"),
// Instant.now().plus(Duration.ofMinutes(15))),
// "order:42", new TaskHistoryResult<>(new OrderDto("ORD-42"),
// Instant.now().plus(Duration.ofMinutes(10)))
// )
// );
//
// TaskHistoryApi trackingTaskHistoryApi = new
// TrackingTaskHistoryApi(rawTaskHistoryApi);
//
// AtomicInteger triggerExecutions = new AtomicInteger();
// AtomicInteger joinExecutions = new AtomicInteger();
//
// TriggerSideComputeOperator triggerOperator = new
// TriggerSideComputeOperator(triggerExecutions);
// JoinSideComputeAndTaskHistoryOperator joinOperator =
// new JoinSideComputeAndTaskHistoryOperator(joinExecutions,
// trackingTaskHistoryApi);
//
// ResourceFactory resourceFactory = new TestResourceFactory(triggerOperator,
// joinOperator);
//
// ExecutionContextRegistry executionContextRegistry = new
// ExecutionContextRegistry();
//
// SideComputer<TaskHistoryResult<CustomerDto>, CustomerDto> sideComputer =
// SideComputer.<TaskHistoryResult<CustomerDto>>builder("trigger-customer-fetch",
// "customer-profile")
// .computer(event -> trackingTaskHistoryApi.get("customer:" +
// event.getOutput(), CustomerDto.class))
// .addHandler(new TaskHistoryExpirySideComputeHandler<>())
// .map(TaskHistoryResult::value)
// .build();
//
// SideComputeListener sideComputeListener =
// new SideComputeListener(List.of(sideComputer), executionContextRegistry);
//
// EventBus synchronousBus = new
// SynchronousEventBus(List.of(sideComputeListener));
//
// AssemblyLine<String, FinalOutput> pipeline =
// ElementModelBuilders.<String>createAssemblyLine("customer-enrichment")
// .version("1.0.0")
// .configuration(
// AssemblyLine.Configuration.builder()
// .eventHandling(
// EventHandlingDefinition.builder()
// .bus(synchronousBus)
// .build())
// .build())
// .then(triggerStation())
// .then(joinStation())
// .build();
//
// PipelineEngine pipelineEngine =
// PipelineEngine.builder()
// .runnerChainFactory(
// new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
// .resourceFactory(resourceFactory)
// .extensionResolver(new RuntimeExtensionResolver(List.of()))
// .build();
//
// RunRequest request =
// RunRequest.builder()
// .input("42")
// .context(Map.of("tenantId", "tenant-a"))
// .with(cacheExtension)
// .build();
//
// // when - first run
// ExecutionResult<FinalOutput> firstResult = pipelineEngine.execute(pipeline,
// request);
/// / executionContextRegistry.register(firstResult.getExecution());
//
// // then
// assertThat(firstResult.isSuccess()).isTrue();
// assertThat(firstResult.getResult()).isEqualTo(new FinalOutput("John",
// "ORD-42"));
// assertThat(triggerExecutions).hasValue(1);
// assertThat(joinExecutions).hasValue(1);
// assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);
//
// // when - second run, same input/context/version
// ExecutionResult<FinalOutput> secondResult = pipelineEngine.execute(pipeline,
// request);
//
// // then
// assertThat(secondResult.isSuccess()).isTrue();
// assertThat(secondResult.getResult()).isEqualTo(new FinalOutput("John",
// "ORD-42"));
//
// // cache hit => aucune opération métier rejouée
// assertThat(triggerExecutions).hasValue(1);
// assertThat(joinExecutions).hasValue(1);
//
// // aucune requête supplémentaire taskHistory
// assertThat(rawTaskHistoryApi.totalCalls()).isEqualTo(2);
// }
//
// @Test
// void should_not_cache_pipeline_when_side_compute_expiry_is_missing() {
// // given
// InMemoryPipelineCacheRepository cacheRepository = new
// InMemoryPipelineCacheRepository();
//
// PipelineCacheExtension cacheExtension =
// new PipelineCacheExtension(
// new PipelineCachePolicy(
// true,
// NoDependencyCachePolicy.DO_NOT_CACHE,
// null),
// new PipelineCacheKeyFactory(
// new JsonSha256FingerprintStrategy<>(),
// new WhitelistedContextFingerprintStrategy(
// List.of("tenantId"),
// new JsonSha256FingerprintStrategy<>())),
// cacheRepository);
//
// AtomicInteger triggerExecutions = new AtomicInteger();
// AtomicInteger joinExecutions = new AtomicInteger();
//
// TriggerSideComputeOperator triggerOperator = new
// TriggerSideComputeOperator(triggerExecutions);
// JoinUsingOnlySideComputeOperator joinOperator = new
// JoinUsingOnlySideComputeOperator(joinExecutions);
//
// ResourceFactory resourceFactory = new TestResourceFactory(triggerOperator,
// joinOperator);
//
// ExecutionContextRegistry executionContextRegistry = new
// ExecutionContextRegistry();
//
// SideComputer<RichCustomerPayload, CustomerDto> sideComputer =
// SideComputer.<RichCustomerPayload>builder("trigger-customer-fetch",
// "customer-profile")
// .computer(event -> new RichCustomerPayload(new CustomerDto("John"), null))
// .addHandler(new RichCustomerPayloadExpiryHandler())
// .map(RichCustomerPayload::value)
// .build();
//
// SideComputeListener sideComputeListener =
// new SideComputeListener(List.of(sideComputer), executionContextRegistry);
//
// EventBus synchronousBus = new
// SynchronousEventBus(List.of(sideComputeListener));
//
// AssemblyLine<String, CustomerDto> pipeline =
// ElementModelBuilders.<String>createAssemblyLine("customer-enrichment")
// .version("1.0.0")
// .configuration(
// AssemblyLine.Configuration.builder()
// .eventHandling(
// EventHandlingDefinition.builder()
// .bus(synchronousBus)
// .build())
// .build())
// .then(triggerStation())
// .then(joinOnlySideComputeStation())
// .build();
//
// PipelineEngine pipelineEngine =
// PipelineEngine.builder()
// .runnerChainFactory(
// new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
// .resourceFactory(resourceFactory)
// .extensionResolver(new RuntimeExtensionResolver(List.of()))
// .build();
//
// RunRequest request =
// RunRequest.builder()
// .input("42")
// .context(Map.of("tenantId", "tenant-a"))
// .with(cacheExtension)
// .build();
//
// // when
// ExecutionResult<CustomerDto> firstResult = pipelineEngine.execute(pipeline,
// request);
// ExecutionResult<CustomerDto> secondResult = pipelineEngine.execute(pipeline,
// request);
//
// // then
// assertThat(firstResult.isSuccess()).isTrue();
// assertThat(secondResult.isSuccess()).isTrue();
// assertThat(firstResult.getResult()).isEqualTo(new CustomerDto("John"));
// assertThat(secondResult.getResult()).isEqualTo(new CustomerDto("John"));
//
// // pas de cache => on rejoue tout
// assertThat(triggerExecutions).hasValue(2);
// assertThat(joinExecutions).hasValue(2);
// }
//
// private static WorkStation<String, String> triggerStation() {
// return ElementModelBuilders
// .<String, String,
// TriggerSideComputeOperator>processingOperation("trigger-customer-fetch",
// TriggerSideComputeOperator.class)
// .build();
// }
//
// private static WorkStation<String, FinalOutput> joinStation() {
// return ElementModelBuilders
// .<String, FinalOutput,
// JoinSideComputeAndTaskHistoryOperator>processingOperation("join-sidecompute-and-taskhistory",
// JoinSideComputeAndTaskHistoryOperator.class)
// .build();
// }
//
// private static WorkStation<String, CustomerDto> joinOnlySideComputeStation()
// {
// return ElementModelBuilders
// .<String, CustomerDto,
// JoinUsingOnlySideComputeOperator>processingOperation("join-sidecompute-only",
// JoinUsingOnlySideComputeOperator.class)
// .build();
// }
//
// // --- Operators ---
//
// static final class TriggerSideComputeOperator implements Operator<String,
// String> {
// private final AtomicInteger executions;
//
// TriggerSideComputeOperator(AtomicInteger executions) {
// this.executions = executions;
// }
//
// @Override
// public String transform(String input, StationExecutionContext
// operationExecution) {
// executions.incrementAndGet();
// return input;
// }
// }
//
// static final class JoinSideComputeAndTaskHistoryOperator implements
// Operator<String, FinalOutput> {
// private final AtomicInteger executions;
// private final TaskHistoryApi taskHistoryApi;
//
// JoinSideComputeAndTaskHistoryOperator(AtomicInteger executions,
// TaskHistoryApi taskHistoryApi) {
// this.executions = executions;
// this.taskHistoryApi = taskHistoryApi;
// }
//
// @Override
// public FinalOutput transform(String input, StationExecutionContext
// operationExecution) {
// executions.incrementAndGet();
//
// // ici on attend/récupère réellement le side-compute
// CustomerDto customer =
// operationExecution.getGlobalContext()
// .getSideComputeContext()
// .<CustomerDto>getOrCreateFuture("customer-profile")
// .join();
//
// // appel direct à la taskHistory API depuis le code métier
// TaskHistoryResult<OrderDto> order =
// taskHistoryApi.get("order:" + input, OrderDto.class);
//
// return new FinalOutput(customer.name(), order.value().orderCode());
// }
// }
//
// static final class JoinUsingOnlySideComputeOperator implements
// Operator<String, CustomerDto> {
// private final AtomicInteger executions;
//
// JoinUsingOnlySideComputeOperator(AtomicInteger executions) {
// this.executions = executions;
// }
//
// @Override
// public CustomerDto transform(String input, StationExecutionContext
// operationExecution) {
// executions.incrementAndGet();
//
// return operationExecution.getGlobalContext()
// .getSideComputeContext()
// .<CustomerDto>getOrCreateFuture("customer-profile")
// .join();
// }
// }
//
// // --- Side-compute handlers ---
//
// static final class TaskHistoryExpirySideComputeHandler<T>
// implements SideComputeHandler<TaskHistoryResult<T>> {
//
// @Override
// public void handle(
// String sideComputeKey,
// OperationCompletedEvent event,
// TaskHistoryResult<T> value,
// ExecutionContext executionContext) {
//
// Object trackerObj =
// executionContext.getContext().get(PipelineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER);
//
// if (trackerObj instanceof ExpirableDependencyTracker tracker) {
// if (value == null || value.expiresAt() == null) {
// tracker.recordMissingExpiry("sidecompute:" + sideComputeKey);
// } else {
// tracker.recordConsumed("sidecompute:" + sideComputeKey, value.expiresAt());
// }
// }
// }
// }
//
// static final class RichCustomerPayloadExpiryHandler
// implements SideComputeHandler<RichCustomerPayload> {
//
// @Override
// public void handle(
// String sideComputeKey,
// OperationCompletedEvent event,
// RichCustomerPayload value,
// ExecutionContext executionContext) {
//
// Object trackerObj =
// executionContext.getContext().get(PipelineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER);
//
// if (trackerObj instanceof ExpirableDependencyTracker tracker) {
// if (value == null || value.expiresAt() == null) {
// tracker.recordMissingExpiry("sidecompute:" + sideComputeKey);
// } else {
// tracker.recordConsumed("sidecompute:" + sideComputeKey, value.expiresAt());
// }
// }
// }
// }
//
// // --- Test doubles ---
//
// static final class FakeRawTaskHistoryApi implements RawTaskHistoryApi {
// private final Map<String, TaskHistoryResult<?>> values;
// private final AtomicInteger totalCalls = new AtomicInteger();
//
// FakeRawTaskHistoryApi(Map<String, TaskHistoryResult<?>> values) {
// this.values = values;
// }
//
// @Override
// @SuppressWarnings("unchecked")
// public <T> TaskHistoryResult<T> get(String key, Class<T> type) {
// totalCalls.incrementAndGet();
// return (TaskHistoryResult<T>) values.get(key);
// }
//
// int totalCalls() {
// return totalCalls.get();
// }
// }
//
// static final class TestResourceFactory implements ResourceFactory {
// private final TriggerSideComputeOperator triggerOperator;
// private final JoinSideComputeAndTaskHistoryOperator joinOperator;
// private final JoinUsingOnlySideComputeOperator joinOnlySideComputeOperator;
//
// TestResourceFactory(
// TriggerSideComputeOperator triggerOperator,
// JoinSideComputeAndTaskHistoryOperator joinOperator) {
// this.triggerOperator = triggerOperator;
// this.joinOperator = joinOperator;
// this.joinOnlySideComputeOperator = null;
// }
//
// TestResourceFactory(
// TriggerSideComputeOperator triggerOperator,
// JoinUsingOnlySideComputeOperator joinOnlySideComputeOperator) {
// this.triggerOperator = triggerOperator;
// this.joinOperator = null;
// this.joinOnlySideComputeOperator = joinOnlySideComputeOperator;
// }
//
// @Override
// @SuppressWarnings("unchecked")
// public <T> T getResource(Class<T> clazz) {
// if (clazz.equals(TriggerSideComputeOperator.class)) {
// return (T) triggerOperator;
// }
// if (clazz.equals(JoinSideComputeAndTaskHistoryOperator.class)) {
// return (T) joinOperator;
// }
// if (clazz.equals(JoinUsingOnlySideComputeOperator.class)) {
// return (T) joinOnlySideComputeOperator;
// }
// throw new IllegalArgumentException("Unsupported resource: " + clazz);
// }
// }
//
// /**
// * EventBus de test : dispatch synchrone, zéro thread bloquant.
// * Il rend le test déterministe et évite les fuites de threads dans JUnit.
// */
// static final class SynchronousEventBus implements EventBus {
// private final List<EventListener<?>> listeners;
//
// SynchronousEventBus(List<EventListener<?>> listeners) {
// this.listeners = listeners;
// }
//
// @Override
// public void run() {
// // no-op
// }
//
// @Override
// public void stopBus() {
// // no-op
// }
//
// @Override
// @SuppressWarnings({"rawtypes", "unchecked"})
// public void acceptEvent(Event event) {
// for (EventListener listener : listeners) {
// if (listener.isAcceptable(event)) {
// listener.handleEvent(event);
// }
// }
// }
// }
//
// // --- DTOs ---
//
// record CustomerDto(String name) {}
// record OrderDto(String orderCode) {}
// record FinalOutput(String customerName, String orderCode) {}
// record RichCustomerPayload(CustomerDto value, Instant expiresAt) {}
// }
