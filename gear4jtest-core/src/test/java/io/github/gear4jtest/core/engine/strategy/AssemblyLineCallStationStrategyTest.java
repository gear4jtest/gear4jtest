package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineCallStationStrategyTest {
    private static AssemblyLineEngine engine() {
        return AssemblyLineEngine.builder().resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry()).build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ReflectiveResourceFactory();
    }

    @Test
    void inline_pipeline_call_executes_child_root_inside_parent_run() {
        // Given
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                .then(processingOperation("child-step", AppendChild.class).build()).build();
        AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                .then(AssemblyLineCallStation.inline("call-child", child))
                .then(processingOperation("parent-step", AppendParent.class).build()).build();
        AssemblyLineEngine engine = engine();

        // When
        ExecutionResult<String> result = engine
                .execute(parent,
                         RunRequest.builder().input("input").resourceFactory(reflectiveResourceFactory()).build());

        // Then
        assertThat(result.isSuccess()).as("inline child assembly line should be executed as part of the parent run")
                .isTrue();
        assertThat(result.getResult()).as("the child output should feed the next parent station")
                .isEqualTo("input-child-parent");
        assertThat(result.getExecution().getParentExecutionId())
                .as("inline execution must not create nested run lineage on the parent run").isNull();
    }

    @Test
    void inline_assembly_line_call_shouldBeThreadConfinedAcrossParallelContainerBranchesUnderRepeatedLoad() {
        // Given
        AwaitBothBranches.reset(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("shared-child")
                .then(processingOperation("child-step", AwaitBothBranches.class).build()).build();
        var left = Stations.branch("left", AssemblyLineCallStation.inline("call-left", child));
        var right = Stations.branch("right", AssemblyLineCallStation.inline("call-right", child));
        var parallelContainer = Stations.container(String.class, executor)
                .withBranch(left)
                .withBranch(right)
                .returns(results -> results.get(left) + "|" + results.get(right));
        AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                .then(parallelContainer)
                .build();
        AssemblyLineEngine engine = engine();

        try {
            for (int attempt = 0; attempt < 25; attempt++) {
                AwaitBothBranches.reset(2);

                // When
                ExecutionResult<String> result = engine
                        .execute(parent,
                                 RunRequest.builder().input("input").resourceFactory(reflectiveResourceFactory())
                                         .build());

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getResult()).isEqualTo("input-child|input-child");
                assertThat(AwaitBothBranches.invocations()).isEqualTo(2);
            }
        } finally {
            executor.shutdownNow();
            AwaitBothBranches.reset(1);
        }
    }

    @Test
    void nested_assembly_line_call_shouldRunConcurrentlyAcrossParallelContainerBranchesUnderRepeatedLoad() {
        // Given
        AwaitBothBranches.reset(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("shared-child")
                .then(processingOperation("child-step", AwaitBothBranches.class).build()).build();
        var left = Stations.branch("left", AssemblyLineCallStation.nestedRun("call-left", child));
        var right = Stations.branch("right", AssemblyLineCallStation.nestedRun("call-right", child));
        var parallelContainer = Stations.container(String.class, executor)
                .withBranch(left)
                .withBranch(right)
                .returns(results -> results.get(left) + "|" + results.get(right));
        AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                .then(parallelContainer)
                .build();
        AssemblyLineEngine engine = engine();

        try {
            for (int attempt = 0; attempt < 25; attempt++) {
                AwaitBothBranches.reset(2);

                // When
                ExecutionResult<String> result = engine
                        .execute(parent,
                                 RunRequest.builder().input("input").resourceFactory(reflectiveResourceFactory())
                                         .build());

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getResult()).isEqualTo("input-child|input-child");
                assertThat(AwaitBothBranches.invocations()).isEqualTo(2);
            }
        } finally {
            executor.shutdownNow();
            AwaitBothBranches.reset(1);
        }
    }

    @Test
    void nested_pipeline_call_executes_child_as_separate_run_with_lineage() {
        // Given
        RunCaptureExtension childRunCapture = new RunCaptureExtension();
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                .then(processingOperation("child-step", AppendChild.class).build()).defaultExtension(childRunCapture)
                .build();
        AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                .then(AssemblyLineCallStation.nestedRun("call-child", child))
                .then(processingOperation("parent-step", AppendParent.class).build()).build();
        AssemblyLineEngine engine = engine();

        // When
        ExecutionResult<String> result = engine
                .execute(parent,
                         RunRequest.builder().input("input").resourceFactory(reflectiveResourceFactory()).build());

        // Then
        assertThat(result.isSuccess()).as("nested child assembly line should complete successfully").isTrue();
        assertThat(result.getResult()).as("the nested child result should feed the next parent station")
                .isEqualTo("input-child-parent");
        assertThat(childRunCapture.completedRuns).as("the child assembly line should have its own run lifecycle")
                .hasSize(1);
        AssemblyRunTrace childRun = childRunCapture.completedRuns.get(0);
        assertThat(childRun.getId()).as("child run should have a distinct execution id")
                .isNotEqualTo(result.getExecution().getId());
        assertThat(childRun.getParentExecutionId()).as("child run should be linked to the parent execution")
                .isEqualTo(result.getExecution().getId());
        assertThat(childRun.getRootExecutionId()).as("child run should keep the top-level root execution id")
                .isEqualTo(result.getExecution().getId());
        assertThat(childRun.getParentStationLogId())
                .as("child run should be linked to the station log that triggered it").isNotNull();
    }

    @Test
    void nested_pipeline_call_applies_child_run_interceptors() {
        // Given
        CountingRunInterceptor childInterceptor = new CountingRunInterceptor();
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                .then(processingOperation("child-step", AppendChild.class).build()).defaultExtension(childInterceptor)
                .build();
        AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                .then(AssemblyLineCallStation.nestedRun("call-child", child)).build();
        AssemblyLineEngine engine = engine();

        // When
        ExecutionResult<String> result = engine
                .execute(parent,
                         RunRequest.builder().input("input").resourceFactory(reflectiveResourceFactory()).build());

        // Then
        assertThat(result.isSuccess()).as("nested execution should succeed").isTrue();
        assertThat(childInterceptor.invocations).as("child default run interceptors should run in NESTED_RUN mode")
                .hasValue(1);
    }

    @Test
    void inline_pipeline_call_rejects_child_pipeline_with_runtime_contract_forbidding_inline() {
        // Given
        CountingRunInterceptor childInterceptor = new CountingRunInterceptor();
        AssemblyLine<String, String> child = AssemblyLines.<String>createAssemblyLine("child")
                .then(processingOperation("child-step", AppendChild.class).build()).defaultExtension(childInterceptor)
                .build();
        AssemblyLine<String, String> parent = AssemblyLines.<String>createAssemblyLine("parent")
                .then(AssemblyLineCallStation.inline("call-child", child)).build();
        AssemblyLineEngine engine = engine();

        // When
        ExecutionResult<String> result = engine
                .execute(parent,
                         RunRequest.builder().input("input").resourceFactory(reflectiveResourceFactory()).build());

        // Then
        assertThat(result.isSuccess()).as("inline execution should be rejected when the child contract forbids it")
                .isFalse();
        assertThat(result.getError()).as("the failure should explain that NESTED_RUN is required")
                .hasMessageContaining("cannot be executed inline").hasMessageContaining("NESTED_RUN");
        assertThat(childInterceptor.invocations).as("the rejected inline child should not execute its run interceptor")
                .hasValue(0);
    }

    public static class ReflectiveResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static class AppendChild implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "-child";
        }
    }

    public static class AwaitBothBranches implements Operator<String, String> {
        private static CyclicBarrier barrier = new CyclicBarrier(1);
        private static final AtomicInteger INVOCATIONS = new AtomicInteger();

        private static void reset(int parties) {
            barrier = new CyclicBarrier(parties);
            INVOCATIONS.set(0);
        }

        private static int invocations() {
            return INVOCATIONS.get();
        }

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            INVOCATIONS.incrementAndGet();
            try {
                barrier.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException(e);
            }
            return input + "-child";
        }
    }

    public static class AppendParent implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "-parent";
        }
    }

    private static final class RunCaptureExtension implements RunLifecycleExtension {
        private final List<AssemblyRunTrace> completedRuns = new ArrayList<>();

        @Override
        public void onRunCompleted(ExecutionContext ctx, AssemblyRunTrace run) {
            completedRuns.add(run);
        }
    }

    private static final class CountingRunInterceptor implements RunInterceptorExtension {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                                        RunRequest request,
                                                        ExecutionContext ctx,
                                                        RunChain<IN, OUT> chain) {
            invocations.incrementAndGet();
            return chain.proceed();
        }
    }
}
