package io.github.gear4jtest.studio.standalone;

import java.util.Set;

/** Closed management route allowlist, not an arbitrary HTTP proxy. */
final class ManagementPath {
    private ManagementPath() {
    }

    static boolean allowed(String method, String path) {
        if (!path.startsWith("/api/v1/"))
            return false;
        var parts = path.substring(8).split("/", -1);
        if (parts.length == 1)
            return method.equals("GET")
                    && Set.of("context", "catalog", "domains", "chains", "drafts").contains(parts[0])
                    || method.equals("POST") && Set.of("domains", "chains", "drafts", "import").contains(parts[0]);
        if (parts.length > 3
                || !parts[1].matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
            return false;
        if (parts.length == 2)
            return method.equals("GET")
                    && Set.of("resources", "drafts", "versions", "runs", "tests", "scenarios").contains(parts[0])
                    || method.equals("PUT") && Set.of("resources", "drafts", "scenarios").contains(parts[0]);
        String key = parts[0] + "/" + parts[2];
        return switch (method) {
            case "GET" -> Set.of("resources/access", "resources/audit", "resources/runs", "resources/metrics",
                                 "resources/scenarios", "drafts/revisions", "chains/versions", "scenarios/revisions")
                    .contains(key);
            case "PUT" -> Set.of("resources/access", "drafts/source").contains(key);
            case "POST" ->
                Set.of("resources/archive", "resources/validate", "resources/diagnostics", "resources/xml-preview",
                       "resources/export", "resources/scenarios", "drafts/fork", "drafts/compare", "drafts/diff",
                       "drafts/tests",
                       "drafts/versions", "versions/publish", "versions/promote", "versions/refresh",
                       "versions/compare",
                       "versions/tests", "scenarios/archive", "scenarios/tests").contains(key);
            default -> false;
        };
    }
}
