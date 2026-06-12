package com.myorg.operation

import io.github.gear4jtest.core.api.behavior.Operator
import io.github.gear4jtest.core.api.context.StationExecutionContext

class Step11 implements Operator<String, String> {
    @Override
    String transform(String input, StationExecutionContext operationExecution) {
        return input
    }

    static String getParam(String value) {
        return value
    }
}
