package io.github.gear4jtest.external.api;

/** Finite phases used to observe generated assembly-line loading. */
public enum GeneratedLoadingPhase {
    ARTIFACT_READ("artifact_read"),
    TRANSLATION("translation"),
    COMPILATION("compilation"),
    CLASS_LOADING("class_loading"),
    CONSTRUCTION("construction"),
    INJECTION("injection");

    private final String metricTagValue;

    GeneratedLoadingPhase(String metricTagValue) {
        this.metricTagValue = metricTagValue;
    }

    /** Returns the stable low-cardinality value used by metrics integrations. */
    public String metricTagValue() {
        return metricTagValue;
    }
}
