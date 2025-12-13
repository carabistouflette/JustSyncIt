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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.performance.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for generating comprehensive benchmark reports.
 * Provides comparative analysis, trend analysis, and visualization data generation.
 */
public class BenchmarkReporter {

    private final Path reportDirectory;

    /**
     * Creates a new benchmark reporter.
     *
     * @param reportDirectory directory to save reports to
     */
    public BenchmarkReporter(Path reportDirectory) {
        this.reportDirectory = reportDirectory;
    }

    /**
     * Generates a comparative report between async and sync benchmarks.
     *
     * @param asyncMetrics list of async performance metrics
     * @param syncMetrics list of sync performance metrics
     * @throws IOException if report generation fails
     */
    public void generateComparativeReport(List<PerformanceMetrics> asyncMetrics,
                                     List<PerformanceMetrics> syncMetrics) throws IOException {
        Path reportFile = reportDirectory.resolve("comparative-report-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".html");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <title>Async vs Sync Performance Comparison</title>\n");
        html.append("    <style>\n");
        html.append(getReportStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        // Header
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>Async vs Sync Performance Comparison</h1>\n");
        html.append("        <p class=\"timestamp\">Generated on: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("</p>\n");
        html.append("    </div>\n");

        // Executive Summary
        html.append(generateExecutiveSummary(asyncMetrics, syncMetrics));

        // Detailed Comparison Table
        html.append(generateComparisonTable(asyncMetrics, syncMetrics));

        // Performance Improvements
        html.append(generatePerformanceImprovements(asyncMetrics, syncMetrics));

        // Recommendations
        html.append(generateRecommendations(asyncMetrics, syncMetrics));

        html.append("</body>\n");
        html.append("</html>\n");

        Files.write(reportFile, html.toString().getBytes());
        System.out.println("Comparative report generated: " + reportFile);
    }

    /**
     * Generates trend analysis for performance metrics over time.
     *
     * @param asyncMetrics list of async performance metrics
     * @param syncMetrics list of sync performance metrics
     * @throws IOException if report generation fails
     */
    public void generateTrendAnalysis(List<PerformanceMetrics> asyncMetrics,
                                  List<PerformanceMetrics> syncMetrics) throws IOException {
        Path reportFile = reportDirectory.resolve("trend-analysis-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"reportType\": \"trendAnalysis\",\n");
        json.append("  \"generatedAt\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        json.append("  \"asyncMetrics\": [\n");

        for (int i = 0; i < asyncMetrics.size(); i++) {
            PerformanceMetrics metric = asyncMetrics.get(i);
            json.append("    {\n");
            json.append("      \"name\": \"").append(escapeJson(metric.getBenchmarkName())).append("\",\n");
            json.append("      \"duration\": ").append(metric.getDurationMs()).append(",\n");
            json.append("      \"metrics\": {\n");

            for (Map.Entry<String, Object> entry : metric.getMetrics().entrySet()) {
                json.append("        \"").append(escapeJson(entry.getKey())).append("\": ");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(escapeJson((String) entry.getValue())).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                json.append(",\n");
            }

            json.append("      }\n");
            json.append("    }");
            if (i < asyncMetrics.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ],\n");
        json.append("  \"syncMetrics\": [\n");

        for (int i = 0; i < syncMetrics.size(); i++) {
            PerformanceMetrics metric = syncMetrics.get(i);
            json.append("    {\n");
            json.append("      \"name\": \"").append(escapeJson(metric.getBenchmarkName())).append("\",\n");
            json.append("      \"duration\": ").append(metric.getDurationMs()).append(",\n");
            json.append("      \"metrics\": {\n");

            for (Map.Entry<String, Object> entry : metric.getMetrics().entrySet()) {
                json.append("        \"").append(escapeJson(entry.getKey())).append("\": ");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(escapeJson((String) entry.getValue())).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                json.append(",\n");
            }

            json.append("      }\n");
            json.append("    }");
            if (i < syncMetrics.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        Files.write(reportFile, json.toString().getBytes());
        System.out.println("Trend analysis generated: " + reportFile);
    }

    /**
     * Generates performance regression report.
     *
     * @param asyncMetrics list of async performance metrics
     * @param syncMetrics list of sync performance metrics
     * @throws IOException if report generation fails
     */
    public void generateRegressionReport(List<PerformanceMetrics> asyncMetrics,
                                   List<PerformanceMetrics> syncMetrics) throws IOException {
        Path reportFile = reportDirectory.resolve("regression-report-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");

        StringBuilder text = new StringBuilder();
        text.append("PERFORMANCE REGRESSION REPORT\n");
        text.append("=========================\n\n");
        text.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");

        // Calculate regression indicators
        Map<String, RegressionInfo> regressions = calculateRegressions(asyncMetrics, syncMetrics);

        text.append("REGRESSION ANALYSIS:\n");
        text.append("-------------------\n");

        for (Map.Entry<String, RegressionInfo> entry : regressions.entrySet()) {
            RegressionInfo info = entry.getValue();
            text.append("Metric: ").append(entry.getKey()).append("\n");
            text.append("  Expected Improvement: ").append(String.format("%.1f%%", info.expectedImprovement)).append("\n");
            text.append("  Actual Improvement: ").append(String.format("%.1f%%", info.actualImprovement)).append("\n");
            text.append("  Status: ").append(info.isRegression ? "REGRESSION DETECTED" : "WITHIN EXPECTATIONS").append("\n");
            text.append("  Severity: ").append(info.severity).append("\n\n");
        }

        // Summary
        long regressionCount = regressions.values().stream().mapToLong(r -> r.isRegression ? 1 : 0).sum();
        text.append("SUMMARY:\n");
        text.append("--------\n");
        text.append("Total metrics analyzed: ").append(regressions.size()).append("\n");
        text.append("Regressions detected: ").append(regressionCount).append("\n");
        text.append("Overall status: ").append(regressionCount > 0 ? "ACTION REQUIRED" : "PASS").append("\n");

        Files.write(reportFile, text.toString().getBytes());
        System.out.println("Regression report generated: " + reportFile);
    }

    /**
     * Generates visualization data for charts.
     *
     * @param asyncMetrics list of async performance metrics
     * @param syncMetrics list of sync performance metrics
     * @throws IOException if data generation fails
     */
    public void generateVisualizationData(List<PerformanceMetrics> asyncMetrics,
                                   List<PerformanceMetrics> syncMetrics) throws IOException {
        Path dataFile = reportDirectory.resolve("visualization-data-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"chartData\": {\n");

        // Throughput chart data
        json.append(generateChartData("throughput", asyncMetrics, syncMetrics, "throughput_mbps"));

        // Latency chart data
        json.append(generateChartData("latency", asyncMetrics, syncMetrics, "average_latency_ms"));

        // CPU usage chart data
        json.append(generateChartData("cpu", asyncMetrics, syncMetrics, "cpu_usage_percent"));

        // Memory usage chart data
        json.append(generateChartData("memory", asyncMetrics, syncMetrics, "peak_memory_mb"));

        json.append("  }\n");
        json.append("}\n");

        Files.write(dataFile, json.toString().getBytes());
        System.out.println("Visualization data generated: " + dataFile);
    }

    /**
     * Generates CSS styles for reports.
     */
    private String getReportStyles() {
        return """
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f5f5f5;
        }

            .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 30px;
            text-align: center;
        }

            .header h1 {
            margin: 0;
            font-size: 2.5em;
            font-weight: 300;
        }

            .timestamp {
            margin: 10px 0 0 0;
            opacity: 0.8;
            font-size: 0.9em;
        }

            .section {
            background: white;
            border-radius: 8px;
            padding: 25px;
            margin-bottom: 30px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }

            .section h2 {
            color: #667eea;
            border-bottom: 2px solid #667eea;
            padding-bottom: 10px;
            margin-top: 0;
        }

            .metric-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin: 20px 0;
        }

            .metric-card {
            background: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 6px;
            padding: 20px;
        }

            .metric-card h3 {
            margin: 0 0 10px 0;
            color: #495057;
            font-size: 1.1em;
        }

            .metric-value {
            font-size: 1.8em;
            font-weight: bold;
            color: #28a745;
            margin: 10px 0;
        }

            .metric-label {
            color: #6c757d;
            font-size: 0.9em;
        }

            .improvement-positive { color: #28a745; font-weight: bold; }
            .improvement-negative { color: #dc3545; font-weight: bold; }
            .improvement-neutral { color: #ffc107; font-weight: bold; }

        table {
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;
        }

        th, td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #dee2e6;
        }

        th {
            background-color: #f8f9fa;
            font-weight: 600;
            color: #495057;
        }

            .regression-warning { color: #dc3545; font-weight: bold; }
            .regression-ok { color: #28a745; font-weight: bold; }
        """;
    }

    /**
     * Generates executive summary section.
     */
    private String generateExecutiveSummary(List<PerformanceMetrics> asyncMetrics,
                                    List<PerformanceMetrics> syncMetrics) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"section\">\n");
        html.append("        <h2>Executive Summary</h2>\n");
        html.append("        <div class=\"metric-grid\">\n");

        // Calculate overall improvements
        double avgThroughputImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "throughput_mbps");
        double avgLatencyImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "average_latency_ms");
        double avgCpuReduction = calculateAverageReduction(asyncMetrics, syncMetrics, "cpu_usage_percent");
        double avgMemoryImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "peak_memory_mb");

        html.append("            <div class=\"metric-card\">\n");
        html.append("                <h3>Average Throughput Improvement</h3>\n");
        html.append("                <div class=\"metric-value\">").append(String.format("%.1f%%", avgThroughputImprovement)).append("</div>\n");
        html.append("                <div class=\"metric-label\">Higher is better</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"metric-card\">\n");
        html.append("                <h3>Average Latency Improvement</h3>\n");
        html.append("                <div class=\"metric-value\">").append(String.format("%.1f%%", avgLatencyImprovement)).append("</div>\n");
        html.append("                <div class=\"metric-label\">Higher is better</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"metric-card\">\n");
        html.append("                <h3>Average CPU Reduction</h3>\n");
        html.append("                <div class=\"metric-value\">").append(String.format("%.1f%%", avgCpuReduction)).append("</div>\n");
        html.append("                <div class=\"metric-label\">Higher is better</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"metric-card\">\n");
        html.append("                <h3>Average Memory Improvement</h3>\n");
        html.append("                <div class=\"metric-value\">").append(String.format("%.1f%%", avgMemoryImprovement)).append("</div>\n");
        html.append("                <div class=\"metric-label\">Higher is better</div>\n");
        html.append("            </div>\n");

        html.append("        </div>\n");
        html.append("    </div>\n");

        return html.toString();
    }

    /**
     * Generates comparison table.
     */
    private String generateComparisonTable(List<PerformanceMetrics> asyncMetrics,
                                   List<PerformanceMetrics> syncMetrics) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"section\">\n");
        html.append("        <h2>Detailed Comparison</h2>\n");
        html.append("        <table>\n");
        html.append("            <tr><th>Benchmark</th><th>Async Result</th><th>Sync Result</th><th>Improvement</th></tr>\n");

        for (int i = 0; i < Math.min(asyncMetrics.size(), syncMetrics.size()); i++) {
            PerformanceMetrics asyncMetric = asyncMetrics.get(i);
            PerformanceMetrics syncMetric = syncMetrics.get(i);

            html.append("            <tr>\n");
            html.append("                <td>").append(asyncMetric.getBenchmarkName()).append("</td>\n");

            // Compare specific metrics
            if (asyncMetric.getMetrics().containsKey("throughput_mbps") &&
                syncMetric.getMetrics().containsKey("throughput_mbps")) {
                double asyncValue = (Double) asyncMetric.getMetrics().get("throughput_mbps");
                double syncValue = (Double) syncMetric.getMetrics().get("throughput_mbps");
                double improvement = ((asyncValue - syncValue) / syncValue) * 100.0;

                html.append("                <td>").append(String.format("%.2f MB/s", asyncValue)).append("</td>\n");
                html.append("                <td>").append(String.format("%.2f MB/s", syncValue)).append("</td>\n");
                html.append("                <td class=\"").append(getImprovementClass(improvement)).append("\">")
                        .append(String.format("%.1f%%", improvement)).append("</td>\n");
            } else {
                html.append("                <td>N/A</td>\n");
                html.append("                <td>N/A</td>\n");
                html.append("                <td>N/A</td>\n");
            }

            html.append("            </tr>\n");
        }

        html.append("        </table>\n");
        html.append("    </div>\n");

        return html.toString();
    }

    /**
     * Generates performance improvements section.
     */
    private String generatePerformanceImprovements(List<PerformanceMetrics> asyncMetrics,
                                          List<PerformanceMetrics> syncMetrics) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"section\">\n");
        html.append("        <h2>Performance Improvements</h2>\n");

        // Calculate improvements for different metrics
        Map<String, Double> improvements = new HashMap<>();
        improvements.put("throughput", calculateAverageImprovement(asyncMetrics, syncMetrics, "throughput_mbps"));
        improvements.put("latency", calculateAverageImprovement(asyncMetrics, syncMetrics, "average_latency_ms"));
        improvements.put("cpu", calculateAverageReduction(asyncMetrics, syncMetrics, "cpu_usage_percent"));
        improvements.put("memory", calculateAverageImprovement(asyncMetrics, syncMetrics, "peak_memory_mb"));

        html.append("        <div class=\"metric-grid\">\n");

        for (Map.Entry<String, Double> entry : improvements.entrySet()) {
            String metricName = entry.getKey();
            double improvement = entry.getValue();
            String description = getMetricDescription(metricName);

            html.append("            <div class=\"metric-card\">\n");
            html.append("                <h3>").append(description).append("</h3>\n");
            html.append("                <div class=\"metric-value ").append(getImprovementClass(improvement)).append("\">")
                    .append(String.format("%.1f%%", improvement)).append("</div>\n");
            html.append("                <div class=\"metric-label\">").append(getMetricInterpretation(metricName, improvement)).append("</div>\n");
            html.append("            </div>\n");
        }

        html.append("        </div>\n");
        html.append("    </div>\n");

        return html.toString();
    }

    /**
     * Generates recommendations section.
     */
    private String generateRecommendations(List<PerformanceMetrics> asyncMetrics,
                                   List<PerformanceMetrics> syncMetrics) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"section\">\n");
        html.append("        <h2>Recommendations</h2>\n");
        html.append("        <div class=\"recommendations\">\n");

        // Generate recommendations based on performance data
        List<String> recommendations = generateRecommendationList(asyncMetrics, syncMetrics);

        for (String recommendation : recommendations) {
            html.append("            <li>").append(recommendation).append("</li>\n");
        }

        html.append("        </div>\n");
        html.append("    </div>\n");

        return html.toString();
    }

    /**
     * Calculates average improvement for a specific metric.
     */
    private double calculateAverageImprovement(List<PerformanceMetrics> asyncMetrics,
                                       List<PerformanceMetrics> syncMetrics,
                                       String metricKey) {
        double totalImprovement = 0.0;
        int count = 0;

        for (int i = 0; i < Math.min(asyncMetrics.size(), syncMetrics.size()); i++) {
            PerformanceMetrics asyncMetric = asyncMetrics.get(i);
            PerformanceMetrics syncMetric = syncMetrics.get(i);

            if (asyncMetric.getMetrics().containsKey(metricKey) &&
                syncMetric.getMetrics().containsKey(metricKey)) {

                Object asyncValueObj = asyncMetric.getMetrics().get(metricKey);
                Object syncValueObj = syncMetric.getMetrics().get(metricKey);

                double asyncValue = (asyncValueObj instanceof Long)
                    ? ((Long) asyncValueObj).doubleValue()
                    : ((Double) asyncValueObj);
                double syncValue = (syncValueObj instanceof Long)
                    ? ((Long) syncValueObj).doubleValue()
                    : ((Double) syncValueObj);

                if (syncValue > 0) {
                    totalImprovement += ((asyncValue - syncValue) / syncValue) * 100.0;
                    count++;
                }
            }
        }

        return count > 0 ? totalImprovement / count : 0.0;
    }

    /**
     * Calculates average reduction for a specific metric (where lower is better).
     */
    private double calculateAverageReduction(List<PerformanceMetrics> asyncMetrics,
                                     List<PerformanceMetrics> syncMetrics,
                                     String metricKey) {
        double totalReduction = 0.0;
        int count = 0;

        for (int i = 0; i < Math.min(asyncMetrics.size(), syncMetrics.size()); i++) {
            PerformanceMetrics asyncMetric = asyncMetrics.get(i);
            PerformanceMetrics syncMetric = syncMetrics.get(i);

            if (asyncMetric.getMetrics().containsKey(metricKey) &&
                syncMetric.getMetrics().containsKey(metricKey)) {

                Object asyncValueObj = asyncMetric.getMetrics().get(metricKey);
                Object syncValueObj = syncMetric.getMetrics().get(metricKey);

                double asyncValue = (asyncValueObj instanceof Long)
                    ? ((Long) asyncValueObj).doubleValue()
                    : ((Double) asyncValueObj);
                double syncValue = (syncValueObj instanceof Long)
                    ? ((Long) syncValueObj).doubleValue()
                    : ((Double) syncValueObj);

                if (syncValue > 0) {
                    totalReduction += ((syncValue - asyncValue) / syncValue) * 100.0;
                    count++;
                }
            }
        }

        return count > 0 ? totalReduction / count : 0.0;
    }

    /**
     * Calculates regressions.
     */
    private Map<String, RegressionInfo> calculateRegressions(List<PerformanceMetrics> asyncMetrics,
                                                     List<PerformanceMetrics> syncMetrics) {
        Map<String, RegressionInfo> regressions = new HashMap<>();

        // Define expected improvements
        double expectedThroughputImprovement = 15.0;
        double expectedLatencyImprovement = 20.0;
        double expectedCpuReduction = 10.0;
        double expectedMemoryImprovement = 5.0;

        // Calculate actual improvements
        double actualThroughputImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "throughput_mbps");
        double actualLatencyImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "average_latency_ms");
        double actualCpuReduction = calculateAverageReduction(asyncMetrics, syncMetrics, "cpu_usage_percent");
        double actualMemoryImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "peak_memory_mb");

        regressions.put("throughput", new RegressionInfo(
            expectedThroughputImprovement, actualThroughputImprovement,
            actualThroughputImprovement < expectedThroughputImprovement, "medium"));

        regressions.put("latency", new RegressionInfo(
            expectedLatencyImprovement, actualLatencyImprovement,
            actualLatencyImprovement < expectedLatencyImprovement, "high"));

        regressions.put("cpu", new RegressionInfo(
            expectedCpuReduction, actualCpuReduction,
            actualCpuReduction < expectedCpuReduction, "medium"));

        regressions.put("memory", new RegressionInfo(
            expectedMemoryImprovement, actualMemoryImprovement,
            actualMemoryImprovement < expectedMemoryImprovement, "low"));

        return regressions;
    }

    /**
     * Generates recommendation list based on performance data.
     */
    private List<String> generateRecommendationList(List<PerformanceMetrics> asyncMetrics,
                                               List<PerformanceMetrics> syncMetrics) {
        List<String> recommendations = new ArrayList<>();

        double throughputImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "throughput_mbps");
        double latencyImprovement = calculateAverageImprovement(asyncMetrics, syncMetrics, "average_latency_ms");
        double cpuReduction = calculateAverageReduction(asyncMetrics, syncMetrics, "cpu_usage_percent");

        if (throughputImprovement < 10.0) {
            recommendations.add("Consider optimizing async I/O patterns for better throughput");
        }

        if (latencyImprovement < 15.0) {
            recommendations.add("Review async chunking strategies to reduce latency");
        }

        if (cpuReduction < 5.0) {
            recommendations.add("Investigate CPU usage patterns in async implementation");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Performance targets are being met - continue monitoring");
            recommendations.add("Consider running benchmarks under different load conditions");
        }

        return recommendations;
    }

    /**
     * Gets CSS class for improvement display.
     */
    private String getImprovementClass(double improvement) {
        if (improvement > 20.0) return "improvement-positive";
        if (improvement > 5.0) return "improvement-neutral";
        return "improvement-negative";
    }

    /**
     * Gets metric description.
     */
    private String getMetricDescription(String metricKey) {
        switch (metricKey) {
            case "throughput": return "Throughput Improvement";
            case "latency": return "Latency Improvement";
            case "cpu": return "CPU Usage Reduction";
            case "memory": return "Memory Efficiency Improvement";
            default: return "Performance Improvement";
        }
    }

    /**
     * Gets metric interpretation.
     */
    private String getMetricInterpretation(String metricKey, double improvement) {
        if (improvement > 20.0) return "Excellent performance gains";
        if (improvement > 10.0) return "Good performance gains";
        if (improvement > 5.0) return "Modest performance gains";
        return "Minimal performance impact";
    }

    /**
     * Generates chart data for a specific metric.
     */
    private String generateChartData(String metricName,
                              List<PerformanceMetrics> asyncMetrics,
                              List<PerformanceMetrics> syncMetrics,
                              String metricKey) {
        StringBuilder json = new StringBuilder();
        json.append("    \"").append(metricName).append("\": {\n");
        json.append("      \"labels\": [");

        // Extract labels from benchmark names
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < Math.min(asyncMetrics.size(), syncMetrics.size()); i++) {
            String name = asyncMetrics.get(i).getBenchmarkName();
            // Extract size/type from name
            if (name.contains("1MB")) labels.add("\"1MB\"");
            else if (name.contains("10MB")) labels.add("\"10MB\"");
            else if (name.contains("100MB")) labels.add("\"100MB\"");
            else if (name.contains("1000MB")) labels.add("\"1GB\"");
            else labels.add("\"" + name + "\"");
        }

        for (int i = 0; i < labels.size(); i++) {
            json.append(labels.get(i));
            if (i < labels.size() - 1) json.append(", ");
        }
        json.append("],\n");

        json.append("      \"asyncData\": [");
        for (int i = 0; i < asyncMetrics.size(); i++) {
            PerformanceMetrics metric = asyncMetrics.get(i);
            if (metric.getMetrics().containsKey(metricKey)) {
                Object valueObj = metric.getMetrics().get(metricKey);
                double value = (valueObj instanceof Long)
                    ? ((Long) valueObj).doubleValue()
                    : ((Double) valueObj);
                json.append(String.format("%.2f", value));
                if (i < asyncMetrics.size() - 1) json.append(", ");
            }
        }
        json.append("],\n");

        json.append("      \"syncData\": [");
        for (int i = 0; i < syncMetrics.size(); i++) {
            PerformanceMetrics metric = syncMetrics.get(i);
            if (metric.getMetrics().containsKey(metricKey)) {
                Object valueObj = metric.getMetrics().get(metricKey);
                double value = (valueObj instanceof Long)
                    ? ((Long) valueObj).doubleValue()
                    : ((Double) valueObj);
                json.append(String.format("%.2f", value));
                if (i < syncMetrics.size() - 1) json.append(", ");
            }
        }
        json.append("]\n");
        json.append("    },\n");

        return json.toString();
    }

    /**
     * Escapes JSON strings.
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * Regression information.
     */
    private static class RegressionInfo {
        public final double expectedImprovement;
        public final double actualImprovement;
        public final boolean isRegression;
        public final String severity;

        public RegressionInfo(double expectedImprovement, double actualImprovement,
                          boolean isRegression, String severity) {
            this.expectedImprovement = expectedImprovement;
            this.actualImprovement = actualImprovement;
            this.isRegression = isRegression;
            this.severity = severity;
        }
    }
}