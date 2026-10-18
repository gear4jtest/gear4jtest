package io.github.gear4jtest.studio.http;

import java.util.Optional;

/**
 * Exhaustive HTTP route contract. Adding a route requires an explicit exposure
 * decision and a handler in the exhaustive ManagementRoutes switch. Federation
 * consumes this same contract; it cannot proxy arbitrary paths.
 */
public enum ManagementEndpoint {
    CONTEXT("GET", "/api/v1/context", Exposure.FEDERATED),
    CATALOG("GET", "/api/v1/catalog", Exposure.FEDERATED),
    DOMAINS_LIST("GET", "/api/v1/domains", Exposure.FEDERATED),
    DOMAINS_CREATE("POST", "/api/v1/domains", Exposure.FEDERATED),
    CHAINS_LIST("GET", "/api/v1/chains", Exposure.FEDERATED),
    CHAINS_CREATE("POST", "/api/v1/chains", Exposure.FEDERATED),
    DRAFTS_LIST("GET", "/api/v1/drafts", Exposure.FEDERATED),
    DRAFTS_CREATE("POST", "/api/v1/drafts", Exposure.FEDERATED),
    IMPORT("POST", "/api/v1/import", Exposure.FEDERATED),
    RESOURCES_GET("GET", "/api/v1/resources/{id}", Exposure.FEDERATED),
    RESOURCES_PUT("PUT", "/api/v1/resources/{id}", Exposure.FEDERATED),
    DRAFTS_GET("GET", "/api/v1/drafts/{id}", Exposure.FEDERATED),
    DRAFTS_PUT("PUT", "/api/v1/drafts/{id}", Exposure.FEDERATED),
    VERSIONS_GET("GET", "/api/v1/versions/{id}", Exposure.FEDERATED),
    RUNS_GET("GET", "/api/v1/runs/{id}", Exposure.FEDERATED),
    TESTS_GET("GET", "/api/v1/tests/{id}", Exposure.FEDERATED),
    SCENARIOS_GET("GET", "/api/v1/scenarios/{id}", Exposure.FEDERATED),
    SCENARIOS_PUT("PUT", "/api/v1/scenarios/{id}", Exposure.FEDERATED),
    RESOURCES_ACCESS_GET("GET", "/api/v1/resources/{id}/access", Exposure.FEDERATED),
    RESOURCES_AUDIT_GET("GET", "/api/v1/resources/{id}/audit", Exposure.FEDERATED),
    RESOURCES_RUNS_GET("GET", "/api/v1/resources/{id}/runs", Exposure.FEDERATED),
    RESOURCES_METRICS_GET("GET", "/api/v1/resources/{id}/metrics", Exposure.FEDERATED),
    RESOURCES_SCENARIOS_GET("GET", "/api/v1/resources/{id}/scenarios", Exposure.FEDERATED),
    RESOURCES_ACCESS_PUT("PUT", "/api/v1/resources/{id}/access", Exposure.FEDERATED),
    RESOURCES_ARCHIVE_POST("POST", "/api/v1/resources/{id}/archive", Exposure.FEDERATED),
    RESOURCES_XML_PREVIEW_POST("POST", "/api/v1/resources/{id}/xml-preview", Exposure.FEDERATED),
    RESOURCES_VALIDATE_POST("POST", "/api/v1/resources/{id}/validate", Exposure.FEDERATED),
    RESOURCES_EXPORT_POST("POST", "/api/v1/resources/{id}/export", Exposure.FEDERATED),
    RESOURCES_DIAGNOSTICS_POST("POST", "/api/v1/resources/{id}/diagnostics", Exposure.FEDERATED),
    RESOURCES_SCENARIOS_POST("POST", "/api/v1/resources/{id}/scenarios", Exposure.FEDERATED),
    DRAFTS_REVISIONS_GET("GET", "/api/v1/drafts/{id}/revisions", Exposure.FEDERATED),
    DRAFTS_SOURCE_PUT("PUT", "/api/v1/drafts/{id}/source", Exposure.FEDERATED),
    DRAFTS_VERSIONS_POST("POST", "/api/v1/drafts/{id}/versions", Exposure.FEDERATED),
    DRAFTS_DIFF_POST("POST", "/api/v1/drafts/{id}/diff", Exposure.FEDERATED),
    DRAFTS_COMPARE_POST("POST", "/api/v1/drafts/{id}/compare", Exposure.FEDERATED),
    DRAFTS_FORK_POST("POST", "/api/v1/drafts/{id}/fork", Exposure.FEDERATED),
    DRAFTS_TESTS_POST("POST", "/api/v1/drafts/{id}/tests", Exposure.FEDERATED),
    CHAINS_VERSIONS_GET("GET", "/api/v1/chains/{id}/versions", Exposure.FEDERATED),
    VERSIONS_COMPARE_POST("POST", "/api/v1/versions/{id}/compare", Exposure.FEDERATED),
    VERSIONS_TESTS_POST("POST", "/api/v1/versions/{id}/tests", Exposure.FEDERATED),
    VERSIONS_PUBLISH_POST("POST", "/api/v1/versions/{id}/publish", Exposure.FEDERATED),
    VERSIONS_PROMOTE_POST("POST", "/api/v1/versions/{id}/promote", Exposure.FEDERATED),
    VERSIONS_REFRESH_POST("POST", "/api/v1/versions/{id}/refresh", Exposure.FEDERATED),
    SCENARIOS_REVISIONS_GET("GET", "/api/v1/scenarios/{id}/revisions", Exposure.FEDERATED),
    SCENARIOS_ARCHIVE_POST("POST", "/api/v1/scenarios/{id}/archive", Exposure.FEDERATED),
    SCENARIOS_TESTS_POST("POST", "/api/v1/scenarios/{id}/tests", Exposure.FEDERATED),
    SCENARIOS_SIMULATION_PLAN_POST("POST", "/api/v1/scenarios/{id}/simulation-plan", Exposure.FEDERATED),
    SCENARIOS_SIMULATIONS_POST("POST", "/api/v1/scenarios/{id}/simulations", Exposure.FEDERATED),
    SCENARIOS_SIMULATIONS_GET("GET", "/api/v1/scenarios/{id}/simulations", Exposure.FEDERATED),
    SIMULATIONS_GET("GET", "/api/v1/simulations/{id}", Exposure.FEDERATED),
    RUN_EVENTS("GET", "/api/v1/runs/{id}/events", Exposure.LOCAL_SSE);

    public enum Exposure {
        FEDERATED, LOCAL_SSE
    }

    private final String method;
    private final String template;
    private final Exposure exposure;

    ManagementEndpoint(String method, String template, Exposure exposure) {
        this.method = method;
        this.template = template;
        this.exposure = exposure;
    }

    public String method() {
        return method;
    }

    public String template() {
        return template;
    }

    public Exposure exposure() {
        return exposure;
    }

    public boolean federated() {
        return exposure == Exposure.FEDERATED;
    }

    public static Optional<ManagementEndpoint> match(String method, String path) {
        if (method == null || path == null)
            return Optional.empty();
        for (var endpoint : values()) {
            if (!endpoint.method.equals(method))
                continue;
            String pattern = endpoint.template.replace("{id}",
                                                       "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");
            if (path.matches(pattern))
                return Optional.of(endpoint);
        }
        return Optional.empty();
    }
}
