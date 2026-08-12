package io.github.gear4jtest.jdbc.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class SqlPlanQualificationReport {
    private static final String REPORT_DIRECTORY_PROPERTY = "gear4j.test.sqlPlanReportDirectory";

    private SqlPlanQualificationReport() {
    }

    static void write(SqlPlanQualificationResult result) {
        Path reportDirectory = Path.of(System.getProperty(REPORT_DIRECTORY_PROPERTY,
                                                          "build/reports/sql-plan-qualification"));
        Path report = reportDirectory.resolve(result.dialect().name().toLowerCase(Locale.ROOT) + ".md");
        try {
            Files.createDirectories(reportDirectory);
            Files.writeString(report, render(result), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Failed to write SQL-plan qualification report " + report, exception);
        }
    }

    private static String render(SqlPlanQualificationResult result) {
        StringBuilder report = new StringBuilder();
        report.append("# SQL plan qualification - ").append(result.dialect()).append("\n\n")
                .append("- Seeded assembly runs: ").append(result.assemblyRunCount()).append("\n")
                .append("- Seeded station logs: ").append(result.stationLogCount()).append("\n")
                .append("- Portable catastrophic ceiling per measured call: ")
                .append(result.maximumQueryMillis()).append(" ms (not a production SLO)\n\n")
                .append("## Verified ordered indexes\n\n")
                .append("| Table | Index | Columns |\n")
                .append("| --- | --- | --- |\n");
        for (SqlIndexEvidence index : result.indexes()) {
            report.append("| ").append(index.table()).append(" | `")
                    .append(index.name()).append("` | `")
                    .append(String.join(", ", index.columns())).append("` |\n");
        }
        report.append("\n## Natural optimizer plans\n\n")
                .append("| Query | Rows | p50 ms | p95 ms | max ms | Reference index | Selected | Full scan")
                .append(" | Plan mode |\n")
                .append("| --- | ---: | ---: | ---: | ---: | --- | --- | --- | --- |\n");
        for (SqlQueryEvidence query : result.queries()) {
            report.append("| ").append(query.name()).append(" | ")
                    .append(query.returnedRows()).append(" | ")
                    .append(decimal(query.p50Millis())).append(" | ")
                    .append(decimal(query.p95Millis())).append(" | ")
                    .append(decimal(query.maximumMillis())).append(" | `")
                    .append(query.referenceIndex()).append("` | ")
                    .append(yesNo(query.referenceIndexSelected())).append(" | ")
                    .append(yesNo(query.fullScanObserved())).append(" | ")
                    .append(query.planMode()).append(" |\n");
        }
        for (SqlQueryEvidence query : result.queries()) {
            report.append("\n## ").append(query.name()).append("\n\n")
                    .append("```text\n").append(query.plan());
            if (!query.plan().endsWith("\n")) {
                report.append('\n');
            }
            report.append("```\n");
        }
        return report.toString();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    record SqlPlanQualificationResult(Gear4jDatabaseDialect dialect,
                                      int assemblyRunCount,
                                      int stationLogCount,
                                      long maximumQueryMillis,
                                      List<SqlIndexEvidence> indexes,
                                      List<SqlQueryEvidence> queries) {}

    record SqlIndexEvidence(String table, String name, List<String> columns) {}

    record SqlQueryEvidence(String name,
                            String referenceIndex,
                            String planMode,
                            int returnedRows,
                            double p50Millis,
                            double p95Millis,
                            double maximumMillis,
                            boolean referenceIndexSelected,
                            boolean fullScanObserved,
                            String plan) {}
}
