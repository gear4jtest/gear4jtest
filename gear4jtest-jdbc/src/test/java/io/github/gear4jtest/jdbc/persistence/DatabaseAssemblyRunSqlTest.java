package io.github.gear4jtest.jdbc.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseAssemblyRunSqlTest {
    @Test
    void h2Statements_shouldBindJsonParametersAsJsonValues() {
        assertThat(DatabaseAssemblyRunSql.insertAssemblyRun(Gear4jDatabaseDialect.H2))
                .contains("VALUES (?,?,? FORMAT JSON,? FORMAT JSON,? FORMAT JSON");
        assertThat(DatabaseAssemblyRunSql.updateAssemblyRun(Gear4jDatabaseDialect.H2))
                .contains("context=? FORMAT JSON")
                .contains("result=? FORMAT JSON");
        assertThat(DatabaseAssemblyRunSql.insertStationLog(Gear4jDatabaseDialect.H2))
                .contains("? FORMAT JSON");
        assertThat(DatabaseAssemblyRunSql.updateOpenStationLog(Gear4jDatabaseDialect.H2))
                .contains("context=? FORMAT JSON");
    }

    @Test
    void nonH2Statements_shouldKeepPortableJsonPlaceholders() {
        assertThat(DatabaseAssemblyRunSql.insertAssemblyRun(Gear4jDatabaseDialect.POSTGRESQL))
                .doesNotContain("FORMAT JSON");
        assertThat(DatabaseAssemblyRunSql.insertStationLog(Gear4jDatabaseDialect.MYSQL))
                .doesNotContain("FORMAT JSON");
    }
}
