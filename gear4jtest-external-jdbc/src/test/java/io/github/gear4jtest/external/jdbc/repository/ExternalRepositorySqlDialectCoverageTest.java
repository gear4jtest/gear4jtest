package io.github.gear4jtest.external.jdbc.repository;

import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExternalRepositorySqlDialectCoverageTest {
    @Test
    void pagedSql_shouldUseOracleSyntaxOnlyForOracle() {
        assertThat(ExternalRepositorySqlDialect.pagedSql(Gear4jDatabaseDialect.ORACLE, "SELECT * FROM t"))
                .isEqualTo("SELECT * FROM t OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        assertThat(ExternalRepositorySqlDialect.pagedSql(Gear4jDatabaseDialect.POSTGRESQL, "SELECT * FROM t"))
                .isEqualTo("SELECT * FROM t LIMIT ? OFFSET ?");
    }

    @Test
    void bindPage_shouldBindLimitAndOffsetByDialect() throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);

        ExternalRepositorySqlDialect.bindPage(Gear4jDatabaseDialect.POSTGRESQL, statement, 3,
                                              new PageRequest(40, 20));
        verify(statement).setInt(3, 20);
        verify(statement).setInt(4, 40);

        PreparedStatement oracleStatement = mock(PreparedStatement.class);
        ExternalRepositorySqlDialect.bindPage(Gear4jDatabaseDialect.ORACLE, oracleStatement, 1,
                                              new PageRequest(40, 20));
        verify(oracleStatement).setInt(1, 40);
        verify(oracleStatement).setInt(2, 20);
    }

    @Test
    void prepareGeneratedKeyInsert_shouldUseOracleColumnNameOnlyForOracle() throws SQLException {
        Connection connection = mock(Connection.class);

        ExternalRepositorySqlDialect.prepareGeneratedKeyInsert(Gear4jDatabaseDialect.ORACLE, connection,
                                                               "INSERT INTO t VALUES (?)");
        ArgumentCaptor<String[]> generatedKeyColumns = ArgumentCaptor.forClass(String[].class);
        verify(connection).prepareStatement(eq("INSERT INTO t VALUES (?)"), generatedKeyColumns.capture());
        assertThat(generatedKeyColumns.getValue()).containsExactly("ID");

        Connection postgresConnection = mock(Connection.class);
        ExternalRepositorySqlDialect.prepareGeneratedKeyInsert(Gear4jDatabaseDialect.POSTGRESQL, postgresConnection,
                                                               "INSERT INTO t VALUES (?)");
        verify(postgresConnection).prepareStatement("INSERT INTO t VALUES (?)",
                                                    java.sql.Statement.RETURN_GENERATED_KEYS);
    }

    @Test
    void sqlFactories_shouldReturnDialectSpecificStatements() {
        assertThat(ExternalRepositorySqlDialect.insertTagIfAbsentSql(Gear4jDatabaseDialect.POSTGRESQL))
                .contains("ON CONFLICT");
        assertThat(ExternalRepositorySqlDialect.insertTagIfAbsentSql(Gear4jDatabaseDialect.MYSQL))
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(ExternalRepositorySqlDialect.insertTagIfAbsentSql(Gear4jDatabaseDialect.ORACLE))
                .startsWith("MERGE INTO operation_chain_tag");
        assertThat(ExternalRepositorySqlDialect.insertTagIfAbsentSql(Gear4jDatabaseDialect.H2))
                .startsWith("MERGE INTO operation_chain_tag");

        assertThat(ExternalRepositorySqlDialect.upsertOperationChainConfigSql(Gear4jDatabaseDialect.POSTGRESQL))
                .contains("CAST(? AS JSONB)");
        assertThat(ExternalRepositorySqlDialect.updateOperationChainStoreSql(Gear4jDatabaseDialect.ORACLE))
                .contains("store_props=?");
    }

    @Test
    void setBooleanAndJson_shouldBindValuesByDialect() throws SQLException {
        PreparedStatement oracle = mock(PreparedStatement.class);

        ExternalRepositorySqlDialect.setBoolean(Gear4jDatabaseDialect.ORACLE, oracle, 1, true);
        ExternalRepositorySqlDialect.setJsonText(Gear4jDatabaseDialect.ORACLE, oracle, 2, "{}");

        verify(oracle).setInt(1, 1);
        verify(oracle).setCharacterStream(eq(2), any(Reader.class), eq(2));

        PreparedStatement postgres = mock(PreparedStatement.class);
        ExternalRepositorySqlDialect.setBoolean(Gear4jDatabaseDialect.POSTGRESQL, postgres, 1, false);
        ExternalRepositorySqlDialect.setJsonText(Gear4jDatabaseDialect.POSTGRESQL, postgres, 2, "{}");

        verify(postgres).setBoolean(1, false);
        verify(postgres).setString(2, "{}");
    }

    @Test
    void isUniqueViolation_shouldRecognizeStatesCodesAndDuplicateMessages() {
        assertThat(ExternalRepositorySqlDialect.isUniqueViolation(Gear4jDatabaseDialect.POSTGRESQL,
                                                                  new SQLException("duplicate", "23505", 0)))
                .isTrue();
        assertThat(ExternalRepositorySqlDialect.isUniqueViolation(Gear4jDatabaseDialect.MYSQL,
                                                                  new SQLException("Duplicate entry", "23000", 1062)))
                .isTrue();
        assertThat(ExternalRepositorySqlDialect.isUniqueViolation(Gear4jDatabaseDialect.MARIADB,
                                                                  new SQLException("Duplicate entry", "23000", 1062)))
                .isTrue();
        assertThat(ExternalRepositorySqlDialect.isUniqueViolation(Gear4jDatabaseDialect.ORACLE,
                                                                  new SQLException("ORA-00001", "23000", 1)))
                .isTrue();
        assertThat(ExternalRepositorySqlDialect.isUniqueViolation(Gear4jDatabaseDialect.H2,
                                                                  new SQLException("contains duplicate value", "23000",
                                                                          0)))
                .isTrue();
        assertThat(ExternalRepositorySqlDialect.isUniqueViolation(Gear4jDatabaseDialect.H2,
                                                                  new SQLException("different", "42000", 0)))
                .isFalse();
    }

    @Test
    void methods_shouldRejectNullDialect() {
        assertThatNullPointerException()
                .isThrownBy(() -> ExternalRepositorySqlDialect.pagedSql(null, "SELECT 1"))
                .withMessage("databaseDialect must not be null");
    }
}
