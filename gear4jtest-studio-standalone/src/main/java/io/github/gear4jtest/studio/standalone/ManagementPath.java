package io.github.gear4jtest.studio.standalone;

import io.github.gear4jtest.studio.http.ManagementEndpoint;

/** Closed shared management route contract, not an arbitrary HTTP proxy. */
final class ManagementPath {
    private ManagementPath() {
    }

    static boolean allowed(String method, String path) {
        return ManagementEndpoint.match(method, path).map(ManagementEndpoint::federated).orElse(false);
    }
}
