/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced reporting for async test results.
 * Provides comprehensive reporting capabilities including console output,
 * HTML reports, and JSON exports.
 */
public final class AsyncTestReporter {

    private final ReporterConfiguration configuration;

    public AsyncTestReporter(ReporterConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Creates a test reporter with default configuration.
     */
    public static AsyncTestReporter create() {
        return new AsyncTestReporter(ReporterConfiguration.defaultConfiguration());
    }

    /**
     * Creates a test reporter with custom configuration.
     */
    public static AsyncTestReporter create(ReporterConfiguration configuration) {
        return new AsyncTestReporter(configuration);
    }

    /**
     * Generates and displays a comprehensive test report.
     */
    public void generateReport(AsyncTestSuite.TestSuiteResult result) {
        try {
            if (configuration.isEnableConsoleOutput()) {
                generateConsoleReport(result);
            }

            if (configuration.isEnableHtmlReport()) {
                generateHtmlReport(result);
            }

            if (configuration.isEnableJsonReport()) {
                generateJsonReport(result);
            }

            if (configuration.isEnableCsvReport()) {
                generateCsvReport(result);
            }

        } catch (Exception e) {
            System.err.println("Failed to generate test report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generates console report.
     */
    private void generateConsoleReport(AsyncTestSuite.TestSuiteResult result) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ASYNC TEST SUITE REPORT");
        System.out.println("=".repeat(80));

        System.out.println("Overall Result: " + (result.isSuccess() ? "PASSED" : "FAILED"));
        System.out.println("Execution Time: " + formatDuration(result.getDuration()));
        System.out.println("Started: " + result.getStartTime());
        System.out.println("Completed: " + result.getEndTime());

        if (result.getErrorMessage() != null) {
            System.out.println("Error: " + result.getErrorMessage());
        }

        System.out.println("\nSUMMARY:");
        System.out.println("Total Test Classes: " + result.getTotalTestClasses());
        System.out.println("Total Test Methods: " + result.getTotalTestMethods());
        System.out.println("Passed: " + result.getTotalPassedTests());
        System.out.println("Failed: " + result.getTotalFailedTests());
        System.out.println("Skipped: " + result.getTotalSkippedTests());
        System.out.println("Success Rate: " + String.format("%.2f%%", result.getSuccessRate() * 100));

        System.out.println("\nCATEGORY RESULTS:");
        for (AsyncTestSuite.TestCategoryResult category : result.getCategoryResults()) {
            System.out.println("  " + category.getCategoryName() + ": " +
                    (category.isSuccess() ? "PASSED" : "FAILED") +
                    " (" + category.getTotalPassedTests() + "/" + category.getTotalTestMethods() + " passed)");
        }

        if (result.getValidationResult() != null) {
            System.out.println("\nVALIDATION: "
                    + (result.getValidationResult().isValid() ? "PASSED" : "FAILED"));
            if (result.getValidationResult().getMessage() != null) {
                System.out.println("Message: " + result.getValidationResult().getMessage());
            }
            if (!result.getValidationResult().getWarnings().isEmpty()) {
                System.out.println("Warnings:");
                result.getValidationResult().getWarnings().forEach(warning -> System.out.println("  - " + warning));
            }
        }

        System.out.println("=".repeat(80));
    }

    /**
     * Generates HTML report.
     */
    private void generateHtmlReport(AsyncTestSuite.TestSuiteResult result) throws IOException {
        Path reportPath = Paths.get(configuration.getOutputDirectory(), "test-report.html");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("  <title>Async Test Suite Report</title>\n");
        html.append("  <style>\n");
        html.append(getHtmlStyles());
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        html.append("  <div class=\"header\">\n");
        html.append("    <h1>Async Test Suite Report</h1>\n");
        html.append("    <p>Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("</p>\n");
        html.append("  </div>\n");

        html.append("  <div class=\"summary\">\n");
        html.append("    <h2>Summary</h2>\n");
        html.append("    <div class=\"summary-grid\">\n");
        html.append("      <div class=\"summary-item ").append(result.isSuccess() ? "success" : "failure")
                .append("\">\n");
        html.append("        <span class=\"label\">Overall Result</span>\n");
        html.append("        <span class=\"value\">").append(result.isSuccess() ? "PASSED" : "FAILED")
                .append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"summary-item\">\n");
        html.append("        <span class=\"label\">Execution Time</span>\n");
        html.append("        <span class=\"value\">").append(formatDuration(result.getDuration())).append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"summary-item\">\n");
        html.append("        <span class=\"label\">Total Tests</span>\n");
        html.append("        <span class=\"value\">").append(result.getTotalTestMethods()).append("</span>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"summary-item\">\n");
        html.append("        <span class=\"label\">Success Rate</span>\n");
        html.append("        <span class=\"value\">").append(String.format("%.2f%%", result.getSuccessRate() * 100))
                .append("</span>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");

        html.append("  <div class=\"categories\">\n");
        html.append("    <h2>Test Categories</h2>\n");
        for (AsyncTestSuite.TestCategoryResult category : result.getCategoryResults()) {
            html.append("    <div class=\"category\">\n");
            html.append("      <h3>").append(category.getCategoryName()).append("</h3>\n");
            html.append("      <div class=\"category-stats\">\n");
            html.append("        <span class=\"stat\">Total: ").append(category.getTotalTestMethods())
                    .append("</span>\n");
            html.append("        <span class=\"stat passed\">Passed: ").append(category.getTotalPassedTests())
                    .append("</span>\n");
            html.append("        <span class=\"stat failed\">Failed: ").append(category.getTotalFailedTests())
                    .append("</span>\n");
            html.append("        <span class=\"stat skipped\">Skipped: ").append(category.getTotalSkippedTests())
                    .append("</span>\n");
            html.append("        <span class=\"stat\">Time: ").append(formatDuration(category.getDuration()))
                    .append("</span>\n");
            html.append("      </div>\n");
            html.append("    </div>\n");
        }
        html.append("  </div>\n");

        html.append("</body>\n");
        html.append("</html>\n");

        Files.write(reportPath, html.toString().getBytes());
        System.out.println("HTML report generated: " + reportPath.toAbsolutePath());
    }

    /**
     * Generates JSON report.
     */
    private void generateJsonReport(AsyncTestSuite.TestSuiteResult result) throws IOException {
        Path reportPath = Paths.get(configuration.getOutputDirectory(), "test-report.json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\",\n");
        json.append("  \"overallResult\": \"").append(result.isSuccess() ? "PASSED" : "FAILED").append("\",\n");
        json.append("  \"startTime\": \"").append(result.getStartTime()).append("\",\n");
        json.append("  \"endTime\": \"").append(result.getEndTime()).append("\",\n");
        json.append("  \"durationMs\": ").append(result.getDuration().toMillis()).append(",\n");
        json.append("  \"summary\": {\n");
        json.append("    \"totalTestClasses\": ").append(result.getTotalTestClasses()).append(",\n");
        json.append("    \"totalTestMethods\": ").append(result.getTotalTestMethods()).append(",\n");
        json.append("    \"passedTests\": ").append(result.getTotalPassedTests()).append(",\n");
        json.append("    \"failedTests\": ").append(result.getTotalFailedTests()).append(",\n");
        json.append("    \"skippedTests\": ").append(result.getTotalSkippedTests()).append(",\n");
        json.append("    \"successRate\": ").append(result.getSuccessRate()).append("\n");
        json.append("  },\n");
        json.append("  \"categories\": [\n");

        for (int i = 0; i < result.getCategoryResults().size(); i++) {
            AsyncTestSuite.TestCategoryResult category = result.getCategoryResults().get(i);
            json.append("    {\n");
            json.append("      \"name\": \"").append(category.getCategoryName()).append("\",\n");
            json.append("      \"success\": ").append(category.isSuccess()).append(",\n");
            json.append("      \"durationMs\": ").append(category.getDuration().toMillis()).append(",\n");
            json.append("      \"totalTests\": ").append(category.getTotalTestMethods()).append(",\n");
            json.append("      \"passedTests\": ").append(category.getTotalPassedTests()).append(",\n");
            json.append("      \"failedTests\": ").append(category.getTotalFailedTests()).append(",\n");
            json.append("      \"skippedTests\": ").append(category.getTotalSkippedTests()).append("\n");
            json.append("    }");
            if (i < result.getCategoryResults().size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        Files.write(reportPath, json.toString().getBytes());
        System.out.println("JSON report generated: " + reportPath.toAbsolutePath());
    }

    /**
     * Generates CSV report.
     */
    private void generateCsvReport(AsyncTestSuite.TestSuiteResult result) throws IOException {
        Path reportPath = Paths.get(configuration.getOutputDirectory(), "test-report.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(reportPath)) {
            // Write header
            writer.write("Category,TestClass,TotalTests,PassedTests,FailedTests,SkippedTests,DurationMs,Success\n");

            // Write category data
            for (AsyncTestSuite.TestCategoryResult category : result.getCategoryResults()) {
                for (AsyncTestSuite.ClassTestResult classResult : category.getClassResults()) {
                    writer.write(String.format("\"%s\",\"%s\",%d,%d,%d,%d,%d,%b\n",
                            category.getCategoryName(),
                            classResult.getClassName(),
                            classResult.getTestMethodCount(),
                            classResult.getPassedTestCount(),
                            classResult.getFailedTestCount(),
                            classResult.getSkippedTestCount(),
                            classResult.getExecutionTime().toMillis(),
                            classResult.isSuccess()));
                }
            }
        }

        System.out.println("CSV report generated: " + reportPath.toAbsolutePath());
    }

    /**
     * Returns CSS styles for HTML report.
     */
    private String getHtmlStyles() {
        return """
                body {
                    font-family: Arial, sans-serif;
                    margin: 20px;
                    background-color: #f5f5f5;
                }
                .header {
                    background-color: #2c3e50;
                    color: white;
                    padding: 20px;
                    border-radius: 5px;
                    margin-bottom: 20px;
                }
                .summary {
                    background-color: white;
                    padding: 20px;
                    border-radius: 5px;
                    margin-bottom: 20px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }
                .summary-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 15px;
                    margin-top: 15px;
                }
                .summary-item {
                    background-color: #ecf0f1;
                    padding: 15px;
                    border-radius: 5px;
                    text-align: center;
                }
                .summary-item.success {
                    background-color: #d5f4e6;
                    border: 1px solid #27ae60;
                }
                .summary-item.failure {
                    background-color: #fadbd8;
                    border: 1px solid #e74c3c;
                }
                .label {
                    display: block;
                    font-size: 12px;
                    color: #7f8c8d;
                    margin-bottom: 5px;
                }
                .value {
                    display: block;
                    font-size: 18px;
                    font-weight: bold;
                    color: #2c3e50;
                }
                .categories {
                    background-color: white;
                    padding: 20px;
                    border-radius: 5px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }
                .category {
                    margin-bottom: 20px;
                    padding-bottom: 20px;
                    border-bottom: 1px solid #ecf0f1;
                }
                .category:last-child {
                    border-bottom: none;
                    margin-bottom: 0;
                    padding-bottom: 0;
                }
                .category-stats {
                    margin-top: 10px;
                }
                .stat {
                    display: inline-block;
                    margin-right: 20px;
                    padding: 5px 10px;
                    background-color: #ecf0f1;
                    border-radius: 3px;
                    font-size: 12px;
                }
                .stat.passed {
                    background-color: #d5f4e6;
                    color: #27ae60;
                }
                .stat.failed {
                    background-color: #fadbd8;
                    color: #e74c3c;
                }
                .stat.skipped {
                    background-color: #fef9e7;
                    color: #f39c12;
                }
                h1, h2, h3 {
                    color: #2c3e50;
                    margin-top: 0;
                }
                """;
    }

    /**
     * Formats duration for display.
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds %dms", hours, minutes, seconds, millis);
        } else if (minutes > 0) {
            return String.format("%dm %ds %dms", minutes, seconds, millis);
        } else if (seconds > 0) {
            return String.format("%ds %dms", seconds, millis);
        } else {
            return String.format("%dms", millis);
        }
    }

    /**
     * Configuration for test reporter.
     */
    public static class ReporterConfiguration {
        private final String outputDirectory;
        private final boolean enableConsoleOutput;
        private final boolean enableHtmlReport;
        private final boolean enableJsonReport;
        private final boolean enableCsvReport;

        private ReporterConfiguration(Builder builder) {
            this.outputDirectory = builder.outputDirectory;
            this.enableConsoleOutput = builder.enableConsoleOutput;
            this.enableHtmlReport = builder.enableHtmlReport;
            this.enableJsonReport = builder.enableJsonReport;
            this.enableCsvReport = builder.enableCsvReport;
        }

        public static ReporterConfiguration defaultConfiguration() {
            return new Builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getOutputDirectory() {
            return outputDirectory;
        }

        public boolean isEnableConsoleOutput() {
            return enableConsoleOutput;
        }

        public boolean isEnableHtmlReport() {
            return enableHtmlReport;
        }

        public boolean isEnableJsonReport() {
            return enableJsonReport;
        }

        public boolean isEnableCsvReport() {
            return enableCsvReport;
        }

        /**
         * Builder for ReporterConfiguration.
         */
        public static class Builder {
            private String outputDirectory = "build/test-reports";
            private boolean enableConsoleOutput = true;
            private boolean enableHtmlReport = true;
            private boolean enableJsonReport = true;
            private boolean enableCsvReport = false;

            public Builder outputDirectory(String outputDirectory) {
                this.outputDirectory = outputDirectory;
                return this;
            }

            public Builder enableConsoleOutput(boolean enableConsoleOutput) {
                this.enableConsoleOutput = enableConsoleOutput;
                return this;
            }

            public Builder enableHtmlReport(boolean enableHtmlReport) {
                this.enableHtmlReport = enableHtmlReport;
                return this;
            }

            public Builder enableJsonReport(boolean enableJsonReport) {
                this.enableJsonReport = enableJsonReport;
                return this;
            }

            public Builder enableCsvReport(boolean enableCsvReport) {
                this.enableCsvReport = enableCsvReport;
                return this;
            }

            public ReporterConfiguration build() {
                return new ReporterConfiguration(this);
            }
        }
    }
}