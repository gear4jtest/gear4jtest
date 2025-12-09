package io.github.gear4jtest.core.sidecompute;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.event.OperationCompletedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.github.gear4jtest.core.model.refactor.DefaultOperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.EventBus;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationKind;
import io.github.gear4jtest.core.model.refactor.OperationParamsInjector;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SideComputeFlowIntegrationTest {

    @Test
    void fullFlow_shouldPublishEvent_computeInBackground_andInjectValue() throws Exception {
        // 1) Event bus + listener + registry
        ExecutionContextRegistry registry = new ExecutionContextRegistry();

        SideComputer<String> sc = SideComputer.of(
                "step-op",
                "bigStuff",
                ev -> "call-" + ev.getOutput()
        );

        SideComputeListener listener = new SideComputeListener(List.of(sc), registry);
        EventBus sideBus = ElementModelBuilders.simpleBus("sideCompute")
                .eventListener(listener)
                .build();

        EventManager eventManager = new EventManager(List.of(sideBus));

        // 2) ExecutionContext enregistré
        ExecutionContext execCtx = new ExecutionContext(
                UUID.randomUUID(),
                "pipeline-test",
                eventManager,
                null, // ResourceFactory mocké/absent ici
                null,  // PipelineExecutionManager mocké/absent ici
                null
        );
        registry.register(execCtx);

        // 3) On simule une opération qui va consommer bigStuff via param
        OperationExecutionRecord record = OperationExecutionRecord.start(
                execCtx.getExecutionId().toString(),
                "step-op",
                null
        );
        OperationExecutionContext opCtx =
                new DefaultOperationExecutionContext("step-op", OperationKind.PROCESSING, execCtx, record);

        // 4) On publie l'événement de fin d'opération
        OperationCompletedEvent event = new OperationCompletedEvent(
                "pipeline-test",
                execCtx.getExecutionId().toString(),
                "step-op",
                "in",
                "OUT"
        );
        eventManager.publish(event);

        // 5) On attend un tout petit peu que le bus traite l'événement
        Thread.sleep(100);

        // 6) Waiter : on unwrap dans le contexte
        SideComputeWaitProcessor waiter = SideComputeWaitProcessor.builder("bigStuff")
                .timeout(Duration.ofSeconds(1))
                .onTimeoutFail()
                .build();

        waiter.beforeExecution("input", opCtx);

        // 7) Param resolver context-aware
        OperationParamsInjector.InterpretationContext<String> paramCtx =
                new OperationParamsInjector.InterpretationContext<>(
                        "input",
                        execCtx,
                        opCtx
                );

        String value = paramCtx.getSideCompute().get("bigStuff", String.class);

        assertThat(value).isEqualTo("call-OUT");
    }
}
