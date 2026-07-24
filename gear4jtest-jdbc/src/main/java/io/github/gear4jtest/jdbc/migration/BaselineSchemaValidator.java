package io.github.gear4jtest.jdbc.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates an existing schema before an explicit baseline is recorded. */
final class BaselineSchemaValidator {
    private static final Pattern CREATE_TABLE_PATTERN = Pattern
            .compile("(?is)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Z0-9_]+)");

    private final String moduleId;
    private final String baselineTableName;
    private final Map<String, Set<String>> requiredColumns;
    private final Map<String, Set<String>> requiredIndexes;

    BaselineSchemaValidator(String moduleId,
                            String baselineTableName,
                            Map<String, Set<String>> requiredColumns,
                            Map<String, Set<String>> requiredIndexes) {
        this.moduleId = moduleId;
        this.baselineTableName = baselineTableName;
        this.requiredColumns = requiredColumns;
        this.requiredIndexes = requiredIndexes;
    }

    void validate(Connection connection, String migrationVersion, String baselineContent) throws SQLException {
        Set<String> requiredTables = extractCreatedTableNames(baselineContent);
        requiredTables.add(baselineTableName);
        requiredTables.addAll(requiredColumns.keySet());
        requiredTables.addAll(requiredIndexes.keySet());

        List<String> missingTables = new ArrayList<>();
        for (String table : requiredTables) {
            if (!tableExists(connection, table)) {
                missingTables.add(table);
            }
        }
        rejectMissing(migrationVersion, "table(s)", missingTables);

        List<String> missingColumns = new ArrayList<>();
        for (Map.Entry<String, Set<String>> requirement : requiredColumns.entrySet()) {
            for (String column : requirement.getValue()) {
                if (!columnExists(connection, requirement.getKey(), column)) {
                    missingColumns.add(requirement.getKey() + "." + column);
                }
            }
        }
        rejectMissing(migrationVersion, "column(s)", missingColumns);

        List<String> missingIndexes = new ArrayList<>();
        for (Map.Entry<String, Set<String>> requirement : requiredIndexes.entrySet()) {
            for (String index : requirement.getValue()) {
                if (!indexExists(connection, requirement.getKey(), index)) {
                    missingIndexes.add(requirement.getKey() + "." + index);
                }
            }
        }
        rejectMissing(migrationVersion, "index(es)", missingIndexes);
    }

    boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return tableExists(metadata, tableName) || tableExists(metadata, tableName.toLowerCase(Locale.ROOT))
                || tableExists(metadata, tableName.toUpperCase(Locale.ROOT));
    }

    private void rejectMissing(String version, String kind, List<String> missing) {
        if (!missing.isEmpty()) {
            throw new SchemaMigrationException("Cannot baseline Gear4J schema for module " + moduleId
                    + " at migration " + version + ". Missing expected " + kind + ": " + missing);
        }
    }

    private Set<String> extractCreatedTableNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private boolean tableExists(DatabaseMetaData metadata, String tableName) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(null, null, tableName, null)) {
            return resultSet.next();
        }
    }

    boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return columnExists(metadata, tableName, columnName)
                || columnExists(metadata, tableName.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT))
                || columnExists(metadata, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT));
    }

    private boolean columnExists(DatabaseMetaData metadata, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = metadata.getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return indexExists(metadata, tableName, indexName)
                || indexExists(metadata, tableName.toLowerCase(Locale.ROOT), indexName)
                || indexExists(metadata, tableName.toUpperCase(Locale.ROOT), indexName);
    }

    private boolean indexExists(DatabaseMetaData metadata, String tableName, String indexName) throws SQLException {
        try (ResultSet resultSet = metadata.getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                String actualName = resultSet.getString("INDEX_NAME");
                if (actualName != null && actualName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
            return false;
        }
    }

}
