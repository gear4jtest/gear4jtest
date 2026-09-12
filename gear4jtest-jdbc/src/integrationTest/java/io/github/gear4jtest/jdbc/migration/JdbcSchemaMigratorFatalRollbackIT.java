package io.github.gear4jtest.jdbc.migration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
class JdbcSchemaMigratorFatalRollbackIT {
    @Test
    void fatalFailureAfterDdl_shouldRollbackTheOwnedPostgresqlTransaction() throws Exception {
        // Given: the first statement executes against PostgreSQL before a JVM Error.
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(),
                                                                     database.getUsername(), database.getPassword())) {
                var fatal = new AssertionError("fatal failure after DDL");
                Connection faultConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                                                                 new Class<?>[] { Connection.class },
                                                                                 (proxy, method, args) -> {
                                                                                     Object result = invoke(connection,
                                                                                                            method,
                                                                                                            args);
                                                                                     if (method.getName()
                                                                                             .equals("createStatement")) {
                                                                                         Statement statement = (Statement) result;
                                                                                         return Proxy
                                                                                                 .newProxyInstance(Statement.class
                                                                                                         .getClassLoader(),
                                                                                                                   new Class<?>[] {
                                                                                                                           Statement.class },
                                                                                                                   (ignored,
                                                                                                                    statementMethod,
                                                                                                                    statementArgs) -> {
                                                                                                                       if (statementMethod
                                                                                                                               .getName()
                                                                                                                               .equals("execute")
                                                                                                                               && "SELECT 42"
                                                                                                                                       .equals(statementArgs[0])) {
                                                                                                                           throw fatal;
                                                                                                                       }
                                                                                                                       return invoke(statement,
                                                                                                                                     statementMethod,
                                                                                                                                     statementArgs);
                                                                                                                   });
                                                                                     }
                                                                                     return result;
                                                                                 });
                Map<String, String> resources = Map.of(
                                                       "fault/migrations.list", "V1__fatal.sql\n",
                                                       "fault/V1__fatal.sql",
                                                       "CREATE TABLE gear4j_fatal_probe(id INTEGER); SELECT 42;");
                var loader = new ClassLoader(getClass().getClassLoader()) {
                    @Override
                    public InputStream getResourceAsStream(String name) {
                        String resource = resources.get(name);
                        return resource == null ? super.getResourceAsStream(name)
                                : new ByteArrayInputStream(resource.getBytes(StandardCharsets.UTF_8));
                    }
                };
                var migrator = JdbcSchemaMigrator.builder().moduleId("fatal-rollback")
                        .dialect(Gear4jDatabaseDialect.POSTGRESQL).baselineTableName("gear4j_fatal_probe")
                        .migrationListResource("fault/migrations.list").classLoader(loader).build();

                // When / Then
                assertThatThrownBy(() -> migrator.migrate(faultConnection)).isSameAs(fatal);
                assertThat(connection.getAutoCommit()).isTrue();
                assertThat(connection.isClosed()).isFalse();
                try (Statement statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT to_regclass('gear4j_fatal_probe')")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNull();
                }
            }
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
