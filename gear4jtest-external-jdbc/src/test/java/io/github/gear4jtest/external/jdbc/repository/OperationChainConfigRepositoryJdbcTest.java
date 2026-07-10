package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationChainConfigRepositoryJdbcTest {
    @Test
    void findByAssemblyLineId_shouldParseJsonStorePropsWithCommaInValue() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("pipeline");
        when(resultSet.getBoolean(2)).thenReturn(true);
        when(resultSet.getString(3)).thenReturn("FILESYSTEM");
        when(resultSet.getString(4)).thenReturn("{\"path\":\"hello, world\",\"quoted\":\"a\\\"b\"}");

        OperationChainConfigRepositoryJdbc repository = OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
                .build();

        // When
        var result = repository.findByAssemblyLineId("pipeline").orElseThrow();

        // Then
        assertThat(result.storeType()).isEqualTo(StoreType.FILESYSTEM);
        assertThat(result.storeProps()).containsEntry("path", "hello, world")
                .containsEntry("quoted", "a\"b");
    }

    @Test
    void dialect_shouldProvideProviderSpecificUpsertSyntax() {
        // When / Then
        assertThat(ExternalRepositorySqlDialect.upsertOperationChainConfigSql(Gear4jDatabaseDialect.POSTGRESQL))
                .contains("ON CONFLICT").contains("JSONB");
        assertThat(ExternalRepositorySqlDialect.upsertOperationChainConfigSql(Gear4jDatabaseDialect.MYSQL))
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(ExternalRepositorySqlDialect.upsertOperationChainConfigSql(Gear4jDatabaseDialect.MARIADB))
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(ExternalRepositorySqlDialect.upsertOperationChainConfigSql(Gear4jDatabaseDialect.ORACLE))
                .contains("MERGE INTO").contains("FROM dual");
        assertThat(ExternalRepositorySqlDialect.upsertOperationChainConfigSql(Gear4jDatabaseDialect.H2))
                .contains("MERGE INTO").contains("KEY(al_id)");
        assertThat(ExternalRepositorySqlDialect.updateOperationChainStoreSql(Gear4jDatabaseDialect.POSTGRESQL))
                .contains("CAST(? AS JSONB)");
        assertThat(ExternalRepositorySqlDialect.updateOperationChainStoreSql(Gear4jDatabaseDialect.ORACLE))
                .doesNotContain("JSONB");
    }

}
