package io.github.gear4jtest.studio.model;

import java.util.List;

/** Synthetic outcome, never a Gear4J run or a performance measurement. */
public record SimulationResult(String engineVersion,
                               String catalogFingerprint,
                               String outcome,
                               String failureCode,
                               CapturedValue input,
                               CapturedValue output,
                               List<Step> steps,
                               ScenarioCheck check) {
    public SimulationResult {
        steps = List.copyOf(steps);
    }

    public record Step(String nodeId,
                       String capability,
                       String status,
                       String selectedNodeId,
                       CapturedValue input,
                       CapturedValue output) {}

    @Override
    public String toString() {
        return "SimulationResult[outcome=" + outcome + ", steps=" + steps.size() + "]";
    }

    public SimulationResult withoutCaptures() {
        return new SimulationResult(engineVersion, catalogFingerprint, outcome, failureCode, CapturedValue.none(),
                CapturedValue.none(), steps.stream().map(s -> new Step(s.nodeId(), s.capability(), s.status(),
                        s.selectedNodeId(), CapturedValue.none(), CapturedValue.none())).toList(),
                check);
    }
}
