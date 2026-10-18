package io.github.gear4jtest.studio.management;

/**
 * Permissions are independent of the capabilities advertised by the runtime.
 * READ_RUNS covers actual runtime runs; READ_SIMULATIONS covers persisted
 * declarative simulation reports. Neither grants the other or permission to
 * execute. Payload access additionally requires READ_OBSERVABILITY.
 */
public enum Permission {
    DISCOVER, READ, EDIT, CREATE_CHAIN, CREATE_DRAFT, MANAGE_ACCESS, READ_AUDIT, VALIDATE, TEST, ARCHIVE, PUBLISH,
    PROMOTE, READ_RUNS, READ_OBSERVABILITY, READ_SCENARIOS, MANAGE_SCENARIOS, SIMULATE, READ_SIMULATIONS
}
