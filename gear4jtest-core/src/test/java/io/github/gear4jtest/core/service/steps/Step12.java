package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.service.CoreRuntimeTestSupport;

public class Step12 implements Operator<CoreRuntimeTestSupport.TestPayload<String>, Integer> {
    @Override
    public Integer transform(CoreRuntimeTestSupport.TestPayload<String> object,
                             StationExecutionContext operationExecution) {
        return 2;
    }
}
