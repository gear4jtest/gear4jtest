package io.github.gear4jtest.jdbc.persistence;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stable facts extracted from a natural optimizer plan without prescribing that
 * plan.
 */
record SqlPlanObservation(boolean referenceIndexSelected, boolean fullScanObserved) {
    private static final Pattern MARIADB_FULL_SCAN = Pattern.compile(
                                                                     "\\\"access_type\\\"\\s*:\\s*\\\"all\\\"",
                                                                     Pattern.CASE_INSENSITIVE);

    static SqlPlanObservation inspect(Gear4jDatabaseDialect dialect, String referenceIndex, String plan) {
        if (plan == null || plan.isBlank()) {
            throw new AssertionError("Empty SQL qualification plan for " + dialect);
        }
        String normalized = plan.toLowerCase(Locale.ROOT);
        boolean referenceIndexSelected = normalized.contains(referenceIndex.toLowerCase(Locale.ROOT));
        boolean fullScanObserved = switch (dialect) {
            case POSTGRESQL -> normalized.contains("seq scan on assembly_run")
                    || normalized.contains("seq scan on station_log");
            case MYSQL -> normalized.contains("table scan on assembly_run")
                    || normalized.contains("table scan on station_log");
            case MARIADB -> MARIADB_FULL_SCAN.matcher(plan).find();
            case ORACLE -> normalized.contains("table access full");
            case H2 -> throw new IllegalArgumentException("H2 is not a production SQL-plan qualification dialect");
        };
        return new SqlPlanObservation(referenceIndexSelected, fullScanObserved);
    }
}
