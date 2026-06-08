package io.github.gear4jtest.core.persistence.migration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSchemaMigratorTest {
    @Test
    void splitSqlStatements_shouldKeepSemicolonsInsideLiteralsAndCommentsOut() {
        // Given
        String script = """
                -- create first table; comment semicolon must be ignored
                CREATE TABLE sample(id INT, label VARCHAR(255) DEFAULT 'a;b');
                /* block comment; must be ignored */
                INSERT INTO sample(id, label) VALUES (1, 'it''s; fine');
                """;

        // When
        var statements = JdbcSchemaMigrator.splitSqlStatements(script);

        // Then
        assertThat(statements)
                .as("SQL semicolons inside comments and quoted literals must not split statements")
                .containsExactly(
                                 "CREATE TABLE sample(id INT, label VARCHAR(255) DEFAULT 'a;b')",
                                 "INSERT INTO sample(id, label) VALUES (1, 'it''s; fine')");
    }

    @Test
    void splitSqlStatements_shouldKeepPostgresqlDollarQuotedBlocksTogether() {
        // Given
        String script = """
                CREATE FUNCTION demo() RETURNS void AS $$
                BEGIN
                    RAISE NOTICE 'a;b';
                END;
                $$ LANGUAGE plpgsql;
                CREATE TABLE after_function(id INT);
                """;

        // When
        var statements = JdbcSchemaMigrator.splitSqlStatements(script);

        // Then
        assertThat(statements)
                .as("PostgreSQL dollar-quoted bodies may contain semicolons")
                .hasSize(2);
        assertThat(statements.get(0))
                .as("function body should remain a single statement")
                .contains("RAISE NOTICE 'a;b';")
                .endsWith("LANGUAGE plpgsql");
        assertThat(statements.get(1))
                .as("statement after the function should still be parsed")
                .isEqualTo("CREATE TABLE after_function(id INT)");
    }
}
