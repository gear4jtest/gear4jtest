package io.github.gear4jtest.core.service.steps;

import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public class Step4 {
    private String whatever;

    public Step4(String whatever) {
        this.whatever = whatever;
    }

    public class Step4Map implements Operator<Map<String, String>, Void> {
        @Override
        public Void transform(Map<String, String> object, StationExecutionContext operationExecution) {
            System.out.println(whatever);
            return null;
        }
    }

    public class Step4Integer implements Operator<Integer, Void> {
        @Override
        public Void transform(Integer object, StationExecutionContext operationExecution) {
            return null;
        }
    }
}
