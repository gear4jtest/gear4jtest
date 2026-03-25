//package io.github.gear4jtest.core.model;
//
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
//import io.github.gear4jtest.core.api.context.ExecutionContext;
//import io.github.gear4jtest.core.api.station.StationKind;
//import org.junit.jupiter.api.Test;
//
//import io.github.gear4jtest.core.persistence.StationLog;
//
//class DefaultStationExecutionContextTest {
//
//    @Test
//    void constructor_shouldExposeOperationAndGlobalContext() {
//        ExecutionContext global =
//                new ExecutionContext(UUID.randomUUID(), "pipeline-1", null, null, null, null);
//        StationLog record =
//                StationLog.start("exec-1", "op-1", null);
//
//        DefaultStationExecutionContext ctx =
//                new DefaultStationExecutionContext("op-1", StationKind.PROCESSING, global, record);
//
//        assertThat(ctx.getOperationId()).isEqualTo("op-1");
//        assertThat(ctx.getKind()).isEqualTo(StationKind.PROCESSING);
//        assertThat(ctx.getGlobalContext()).isSameAs(global);
//        assertThat(ctx.getRecord()).isSameAs(record);
//    }
//
//    @Test
//    void capabilities_shouldBeEmptyByDefaultAndReturnValueWhenAdded() {
//        ExecutionContext global =
//                new ExecutionContext(UUID.randomUUID(), "pipeline-1", null, null, null, null);
//        StationLog record =
//                StationLog.start("exec-1", "op-1", null);
//
//        DefaultStationExecutionContext ctx =
//                new DefaultStationExecutionContext("op-1", StationKind.PROCESSING, global, record);
//
//        assertThat(ctx.getCapability(String.class)).isEmpty();
//
//        ctx.addCapability(String.class, "value");
//        ctx.addCapability(Integer.class, 42);
//
//        assertThat(ctx.getCapability(String.class)).contains("value");
//        assertThat(ctx.getCapability(Integer.class)).contains(42);
//        assertThat(ctx.getCapability(Long.class)).isEmpty();
//    }
//}
