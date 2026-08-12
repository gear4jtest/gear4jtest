package io.github.gear4jtest.jdbc.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlPlanObservationTest {
    @Test
    void inspect_shouldAcceptPostgresqlAlternativeIndex() {
        String plan = "Index Scan using idx_ar_status on assembly_run\n"
                + "Sort Key: start_time DESC, id DESC";

        SqlPlanObservation observation = SqlPlanObservation.inspect(Gear4jDatabaseDialect.POSTGRESQL,
                                                                    "idx_ar_status_start", plan);

        assertThat(observation.referenceIndexSelected()).isFalse();
        assertThat(observation.fullScanObserved()).isFalse();
    }

    @Test
    void inspect_shouldAcceptMySqlForeignKeyIndex() {
        String plan = "Index lookup on station_log using fk_parent_op (parent_log_id='parent')";

        SqlPlanObservation observation = SqlPlanObservation.inspect(Gear4jDatabaseDialect.MYSQL,
                                                                    "idx_station_log_exec_parent", plan);

        assertThat(observation.referenceIndexSelected()).isFalse();
        assertThat(observation.fullScanObserved()).isFalse();
    }

    @Test
    void inspect_shouldRecordOracleFullScanWithoutRejectingThePlan() {
        String plan = "| 3 | TABLE ACCESS FULL | ASSEMBLY_RUN |";

        SqlPlanObservation observation = SqlPlanObservation.inspect(Gear4jDatabaseDialect.ORACLE,
                                                                    "idx_ar_assembly_line_start", plan);

        assertThat(observation.referenceIndexSelected()).isFalse();
        assertThat(observation.fullScanObserved()).isTrue();
    }

    @Test
    void inspect_shouldRejectMissingPlanEvidence() {
        assertThatThrownBy(() -> SqlPlanObservation.inspect(Gear4jDatabaseDialect.MARIADB,
                                                            "idx_ar_start", " "))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Empty SQL qualification plan");
    }
}
