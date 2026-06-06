package io.github.gear4jtest.external.api.repository.jdbc;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationChainTagRepositoryJdbcTest {
    @Test
    void insertTagSql_shouldUsePostgresConflictSyntax() {
        assertThat(OperationChainTagRepositoryJdbc.insertTagSql(Gear4jDatabaseDialect.POSTGRESQL))
                .contains("ON CONFLICT (al_id, tag) DO NOTHING")
                .doesNotContain("ON DUPLICATE KEY")
                .doesNotContain("MERGE INTO");
    }

    @Test
    void insertTagSql_shouldUseMySqlCompatibleConflictSyntax() {
        assertThat(OperationChainTagRepositoryJdbc.insertTagSql(Gear4jDatabaseDialect.MYSQL))
                .contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("ON CONFLICT");
        assertThat(OperationChainTagRepositoryJdbc.insertTagSql(Gear4jDatabaseDialect.MARIADB))
                .contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("ON CONFLICT");
    }

    @Test
    void insertTagSql_shouldUseOracleMergeSyntax() {
        assertThat(OperationChainTagRepositoryJdbc.insertTagSql(Gear4jDatabaseDialect.ORACLE))
                .contains("MERGE INTO operation_chain_tag")
                .contains("FROM dual")
                .doesNotContain("ON CONFLICT")
                .doesNotContain("ON DUPLICATE KEY");
    }

    @Test
    void insertTagSql_shouldUseH2MergeSyntax() {
        assertThat(OperationChainTagRepositoryJdbc.insertTagSql(Gear4jDatabaseDialect.H2))
                .contains("MERGE INTO operation_chain_tag")
                .contains("KEY(al_id, tag)");
    }
}
