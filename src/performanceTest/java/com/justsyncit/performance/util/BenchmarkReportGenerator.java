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
    * Provides methods to create detailed performance analysis and visualizations.
    */
public class BenchmarkReportGenerator {

    private final List<PerformanceMetrics> allMetrics;
    private final Path reportDirectory;

    /**
        * Creates a new benchmark report generator.
        *
        * @param allMetrics list of all performance metrics from benchmarks
        * @param reportDirectory directory to save reports to
        */
    public BenchmarkReportGenerator(List<PerformanceMetrics> allMetrics, Path reportDirectory) {
        this.allMetrics = new ArrayList<>(allMetrics);
        this.reportDirectory = reportDirectory;
    }

    /**
        * Generates a comprehensive HTML report with all benchmark results.
        *
        * @return path to the generated HTML report
        */
    public Path generateHtmlReport() throws IOException {
        Path reportFile = reportDirectory.resolve("benchmark-report-"
                +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".html");

        String htmlContent = generateHtmlContent();
        Files.write(reportFile, htmlContent.getBytes());

        return reportFile;
    }

    /**
        * Generates a comprehensive JSON report with all benchmark results.
        *
        * @return path to the generated JSON report
        */
    public Path generateJsonReport() throws IOException {
        Path reportFile = reportDirectory.resolve("benchmark-report-"
                +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".json");

        String jsonContent = generateJsonContent();
        Files.write(reportFile, jsonContent.getBytes());

        return reportFile;
    }

    /**
        * Generates a CSV report with benchmark results for data analysis.
        *
        * @return path to the generated CSV report
        */
    public Path generateCsvReport() throws IOException {
        Path reportFile = reportDirectory.resolve("benchmark-report-"
                +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".csv");

        String csvContent = generateCsvContent();
        Files.write(reportFile, csvContent.getBytes());

        return reportFile;
    }

    /**
        * Generates a text summary report with key findings.
        *
        * @return path to the generated text report
        */
    public Path generateTextSummaryReport() throws IOException {
        Path reportFile = reportDirectory.resolve("benchmark-summary-"
                +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".txt");

        String textContent = generateTextSummaryContent();
        Files.write(reportFile, textContent.getBytes());

        return reportFile;
    }

    /**
        * Generates performance comparison charts data.
        *
        * @return path to the generated chart data file
        */
    public Path generateChartDataFile() throws IOException {
        Path chartFile = reportDirectory.resolve("benchmark-chart-data-"
                +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".json");

        String chartContent = generateChartDataContent();
        Files.write(chartFile, chartContent.getBytes());

        return chartFile;
    }

    /**
        * Generates the complete HTML report content.
        */
    private String generateHtmlContent() {
        StringBuilder html = new StringBuilder();

        // HTML header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>JustSyncIt Performance Benchmark Report</title>\n");
        html.append("    <style>\n");
        html.append(getReportStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        // Report header
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>JustSyncIt Performance Benchmark Report</h1>\n");
        html.append("        <p class=\"timestamp\">Generated on: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("</p>\n");
        html.append("    </div>\n");

        // Executive summary
        html.append(generateExecutiveSummary());

        // Detailed results by category
        html.append(generateDetailedResults());

        // Performance analysis
        html.append(generatePerformanceAnalysis());

        // Recommendations
        html.append(generateRecommendations());

        // Charts section
        html.append(generateChartsSection());

        // Raw data
        html.append(generateRawDataSection());

        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
        * Generates JSON report content.
        */
    private String generateJsonContent() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"reportInfo\": {\n");
        json.append("    \"generatedAt\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        json.append("    \"version\": \"1.0\",\n");
        json.append("    \"totalBenchmarks\": ").append(allMetrics.size()).append("\n");
        json.append("  },\n");

        // Executive summary
        json.append("  \"executiveSummary\": {\n");
        json.append(generateJsonExecutiveSummary());
        json.append("  },\n");

        // Detailed metrics
        json.append("  \"benchmarks\": [\n");
        for (int i = 0; i < allMetrics.size(); i++) {
            PerformanceMetrics metrics = allMetrics.get(i);
            json.append("    {\n");
            json.append("      \"name\": \"").append(escapeJson(metrics.getBenchmarkName())).append("\",\n");
            json.append("      \"duration\": ").append(metrics.getDurationMs()).append(",\n");
            json.append("      \"metrics\": {\n");

            for (Map.Entry<String, Object> entry : metrics.getMetrics().entrySet()) {
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
            if (i < allMetrics.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        return json.toString();
    }

    /**
        * Generates CSV report content.
        */
    private String generateCsvContent() {
        StringBuilder csv = new StringBuilder();

        // CSV header
        csv.append("Benchmark Name,Duration (ms),Throughput (MB/s),Files/Second,Chunks/Second,");
        csv.append("Memory Used (MB),CPU Usage (%),Dataset Size (MB),File Count,Chunk Count\n");

        // Data rows
        for (PerformanceMetrics metrics : allMetrics) {
            csv.append("\"").append(metrics.getBenchmarkName()).append("\",");
            csv.append(metrics.getDurationMs()).append(",");

            Double throughput = (Double) metrics.getMetrics().get("throughput_mbps");
            csv.append(throughput != null ? String.format("%.2f", throughput) : "").append(",");

            Double filesPerSecond = (Double) metrics.getMetrics().get("files_per_second");
            csv.append(filesPerSecond != null ? String.format("%.2f", filesPerSecond) : "").append(",");

            Double chunksPerSecond = (Double) metrics.getMetrics().get("chunks_per_second");
            csv.append(chunksPerSecond != null ? String.format("%.2f", chunksPerSecond) : "").append(",");

            Double memoryUsed = (Double) metrics.getMetrics().get("memory_used_mb");
            csv.append(memoryUsed != null ? String.format("%.2f", memoryUsed) : "").append(",");

            Double cpuUsage = (Double) metrics.getMetrics().get("cpu_usage_percent");
            csv.append(cpuUsage != null ? String.format("%.2f", cpuUsage) : "").append(",");

            Object datasetSizeObj = metrics.getMetrics().get("dataset_size_mb");
            String datasetSizeStr = "";
            if (datasetSizeObj != null) {
                if (datasetSizeObj instanceof Long) {
                    datasetSizeStr = ((Long) datasetSizeObj).toString();
                } else if (datasetSizeObj instanceof Integer) {
                    datasetSizeStr = ((Integer) datasetSizeObj).toString();
                } else {
                    datasetSizeStr = datasetSizeObj.toString();
                }
            }
            csv.append(datasetSizeStr).append(",");

            Object fileCountObj = metrics.getMetrics().get("file_count");
            String fileCountStr = "";
            if (fileCountObj != null) {
                if (fileCountObj instanceof Long) {
                    fileCountStr = ((Long) fileCountObj).toString();
                } else if (fileCountObj instanceof Integer) {
                    fileCountStr = ((Integer) fileCountObj).toString();
                } else {
                    fileCountStr = fileCountObj.toString();
                }
            }
            csv.append(fileCountStr).append(",");

            Object chunkCountObj = metrics.getMetrics().get("chunks_created");
            String chunkCountStr = "";
            if (chunkCountObj != null) {
                if (chunkCountObj instanceof Long) {
                    chunkCountStr = ((Long) chunkCountObj).toString();
                } else if (chunkCountObj instanceof Integer) {
                    chunkCountStr = ((Integer) chunkCountObj).toString();
                } else {
                    chunkCountStr = chunkCountObj.toString();
                }
            }
            csv.append(chunkCountStr).append("\n");
        }

        return csv.toString();
    }

    /**
        * Generates text summary content.
        */
    private String generateTextSummaryContent() {
        StringBuilder text = new StringBuilder();

        text.append("JUSTSYNCIT PERFORMANCE BENCHMARK SUMMARY\n");
        text.append("=====================================\n\n");

        text.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        text.append("Total Benchmarks: ").append(allMetrics.size()).append("\n\n");

        // Key findings
        text.append("KEY FINDINGS:\n");
        text.append("------------\n");

        Map<String, List<PerformanceMetrics>> groupedMetrics = groupMetricsByType();

        for (Map.Entry<String, List<PerformanceMetrics>> entry : groupedMetrics.entrySet()) {
            String category = entry.getKey();
            List<PerformanceMetrics> metrics = entry.getValue();

            text.append("\n").append(category.toUpperCase()).append(" PERFORMANCE:\n");

            // Calculate averages
            double avgThroughput = metrics.stream()
                    .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                    .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                    .average()
                    .orElse(0.0);

            double avgMemory = metrics.stream()
                    .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
                    .mapToDouble(m -> (Double) m.getMetrics().get("memory_used_mb"))
                    .average()
                    .orElse(0.0);

            text.append(String.format("  Average Throughput: %.2f MB/s\n", avgThroughput));
            text.append(String.format("  Average Memory Usage: %.2f MB\n", avgMemory));
            text.append(String.format("  Tests Run: %d\n", metrics.size()));
        }

        // Performance targets assessment
        text.append("\nPERFORMANCE TARGETS ASSESSMENT:\n");
        text.append("----------------------------\n");

        int targetsMet = 0;
        int totalTargets = 0;

        // Check backup throughput target (>50 MB/s)
        List<PerformanceMetrics> backupMetrics = allMetrics.stream()
                .filter(m -> m.getBenchmarkName().contains("Backup"))
                .collect(java.util.stream.Collectors.toList());

        if (!backupMetrics.isEmpty()) {
            totalTargets++;
            double avgBackupThroughput = backupMetrics.stream()
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("throughput_mbps", 0.0))
                    .average()
                    .orElse(0.0);

            if (avgBackupThroughput >= 50.0) {
                targetsMet++;
                text.append("✓ Backup throughput target met (>50 MB/s)\n");
            } else {
                text.append("✗ Backup throughput target not met (").append(String.format("%.2f", avgBackupThroughput)).append(" MB/s)\n");
            }
        }

        // Check memory usage target (<500MB)
        List<PerformanceMetrics> memoryMetrics = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
                .collect(java.util.stream.Collectors.toList());

        if (!memoryMetrics.isEmpty()) {
            totalTargets++;
            double avgMemoryUsage = memoryMetrics.stream()
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("memory_used_mb", 0.0))
                    .average()
                    .orElse(0.0);

            if (avgMemoryUsage <= 500.0) {
                targetsMet++;
                text.append("✓ Memory usage target met (<500 MB)\n");
            } else {
                text.append("✗ Memory usage target not met (").append(String.format("%.2f", avgMemoryUsage)).append(" MB)\n");
            }
        }

        text.append(String.format("\nOverall: %d/%d targets met (%.1f%%)\n",
                targetsMet, totalTargets, (double) targetsMet / totalTargets * 100.0));

        // Recommendations
        text.append("\nRECOMMENDATIONS:\n");
        text.append("---------------\n");
        text.append(generateTextRecommendations());

        return text.toString();
    }

    /**
        * Generates chart data for visualization.
        */
    private String generateChartDataContent() {
        StringBuilder chartData = new StringBuilder();
        chartData.append("{\n");
        chartData.append("  \"throughputChart\": {\n");
        chartData.append("    \"labels\": [");

        List<String> benchmarkNames = allMetrics.stream()
                .map(PerformanceMetrics::getBenchmarkName)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        for (int i = 0; i < benchmarkNames.size(); i++) {
            chartData.append("\"").append(escapeJson(benchmarkNames.get(i))).append("\"");
            if (i < benchmarkNames.size() - 1) {
                chartData.append(",");
            }
        }

        chartData.append("],\n");
        chartData.append("    \"datasets\": [\n");

        // Throughput dataset
        chartData.append("      {\n");
        chartData.append("        \"label\": \"Throughput (MB/s)\",\n");
        chartData.append("        \"data\": [");

        for (int i = 0; i < benchmarkNames.size(); i++) {
            final String benchmarkName = benchmarkNames.get(i);
            double throughput = allMetrics.stream()
                    .filter(m -> m.getBenchmarkName().equals(benchmarkName))
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("throughput_mbps", 0.0))
                    .findFirst()
                    .orElse(0.0);

            chartData.append(String.format("%.2f", throughput));
            if (i < benchmarkNames.size() - 1) {
                chartData.append(",");
            }
        }

        chartData.append("]\n");
        chartData.append("      }\n");

        // Memory usage dataset
        chartData.append("      ,{\n");
        chartData.append("        \"label\": \"Memory Usage (MB)\",\n");
        chartData.append("        \"data\": [");

        for (int i = 0; i < benchmarkNames.size(); i++) {
            final String benchmarkName = benchmarkNames.get(i);
            double memoryUsage = allMetrics.stream()
                    .filter(m -> m.getBenchmarkName().equals(benchmarkName))
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("memory_used_mb", 0.0))
                    .findFirst()
                    .orElse(0.0);

            chartData.append(String.format("%.2f", memoryUsage));
            if (i < benchmarkNames.size() - 1) {
                chartData.append(",");
            }
        }

        chartData.append("]\n");
        chartData.append("      }\n");
        chartData.append("    ]\n");
        chartData.append("  }\n");
        chartData.append("}\n");

        return chartData.toString();
    }

    /**
        * Groups metrics by benchmark type.
        */
    private Map<String, List<PerformanceMetrics>> groupMetricsByType() {
        Map<String, List<PerformanceMetrics>> grouped = new HashMap<>();

        for (PerformanceMetrics metrics : allMetrics) {
            String name = metrics.getBenchmarkName().toLowerCase();
            String category;

            if (name.contains("throughput")) {
                category = "Throughput";
            } else if (name.contains("scalability")) {
                category = "Scalability";
            } else if (name.contains("network")) {
                category = "Network";
            } else if (name.contains("deduplication")) {
                category = "Deduplication";
            } else if (name.contains("concurrency")) {
                category = "Concurrency";
            } else {
                category = "Other";
            }

            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(metrics);
        }

        return grouped;
    }

    /**
        * Generates CSS styles for the HTML report.
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

            .status-good { color: #28a745; }
            .status-warning { color: #ffc107; }
            .status-error { color: #dc3545; }

            .recommendations {
            background: #fff3cd;
            border-left: 4px solid #ffc107;
            padding: 20px;
        }

            .recommendations h3 {
            color: #856404;
            margin-top: 0;
        }

            .recommendations ul {
            margin: 15px 0;
        }

            .recommendations li {
            margin: 8px 0;
            line-height: 1.5;
        }

            .raw-data {
            background: #f8f9fa;
            border: 1px solid #dee2e6;
            border-radius: 6px;
            padding: 20px;
            font-family: 'Courier New', Courier, monospace;
            font-size: 0.9em;
            overflow-x: auto;
        }

            .chart-container {
            background: white;
            border-radius: 8px;
            padding: 25px;
            margin-bottom: 30px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }

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

            .target-met { color: #28a745; font-weight: bold; }
            .target-not-met { color: #dc3545; font-weight: bold; }
        """;
    }

    /**
        * Generates executive summary section for HTML.
        */
    private String generateExecutiveSummary() {
        StringBuilder summary = new StringBuilder();

        summary.append("    <div class=\"section\">\n");
        summary.append("        <h2>Executive Summary</h2>\n");

        // Calculate overall statistics
        double avgThroughput = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                .average()
                .orElse(0.0);

        double avgMemory = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
                .mapToDouble(m -> (Double) m.getMetrics().get("memory_used_mb"))
                .average()
                .orElse(0.0);

        summary.append("        <div class=\"metric-grid\">\n");
        summary.append("            <div class=\"metric-card\">\n");
        summary.append("                <h3>Average Throughput</h3>\n");
        summary.append("                <div class=\"metric-value\">").append(String.format("%.2f", avgThroughput)).append(" MB/s</div>\n");
        summary.append("                <div class=\"metric-label\">Across all benchmarks</div>\n");
        summary.append("            </div>\n");

        summary.append("            <div class=\"metric-card\">\n");
        summary.append("                <h3>Average Memory Usage</h3>\n");
        summary.append("                <div class=\"metric-value\">").append(String.format("%.2f", avgMemory)).append(" MB</div>\n");
        summary.append("                <div class=\"metric-label\">Peak usage during operations</div>\n");
        summary.append("            </div>\n");

        summary.append("            <div class=\"metric-card\">\n");
        summary.append("                <h3>Total Benchmarks</h3>\n");
        summary.append("                <div class=\"metric-value\">").append(allMetrics.size()).append("</div>\n");
        summary.append("                <div class=\"metric-label\">Tests completed</div>\n");
        summary.append("            </div>\n");
        summary.append("        </div>\n");
        summary.append("    </div>\n");

        return summary.toString();
    }

    /**
        * Generates detailed results section for HTML.
        */
    private String generateDetailedResults() {
        StringBuilder results = new StringBuilder();

        Map<String, List<PerformanceMetrics>> groupedMetrics = groupMetricsByType();

        for (Map.Entry<String, List<PerformanceMetrics>> entry : groupedMetrics.entrySet()) {
            String category = entry.getKey();
            List<PerformanceMetrics> metrics = entry.getValue();

            results.append("    <div class=\"section\">\n");
            results.append("        <h2>").append(category).append(" Performance Details</h2>\n");

            for (PerformanceMetrics metric : metrics) {
                results.append("        <div class=\"metric-card\">\n");
                results.append("            <h3>").append(metric.getBenchmarkName()).append("</h3>\n");
                results.append("            <div class=\"metric-summary\">").append(metric.generateSummary()).append("</div>\n");
                results.append("        </div>\n");
            }

            results.append("    </div>\n");
        }

        return results.toString();
    }

    /**
        * Generates performance analysis section for HTML.
        */
    private String generatePerformanceAnalysis() {
        StringBuilder analysis = new StringBuilder();

        analysis.append("    <div class=\"section\">\n");
        analysis.append("        <h2>Performance Analysis</h2>\n");

        // Performance targets assessment
        analysis.append("        <h3>Performance Targets Assessment</h3>\n");
        analysis.append("        <table>\n");
        analysis.append("            <tr><th>Target</th><th>Required</th><th>Achieved</th><th>Status</th></tr>\n");

        // Backup throughput target
        double avgBackupThroughput = allMetrics.stream()
                .filter(m -> m.getBenchmarkName().contains("Backup"))
                .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("throughput_mbps", 0.0))
                .average()
                .orElse(0.0);

        analysis.append("            <tr>\n");
        analysis.append("                <td>Backup Throughput</td>\n");
        analysis.append("                <td>>50 MB/s</td>\n");
        analysis.append("                <td>").append(String.format("%.2f MB/s", avgBackupThroughput)).append("</td>\n");
        analysis.append("                <td class=\"").append(avgBackupThroughput >= 50.0 ? "target-met" : "target-not-met").append("\">");
        analysis.append(avgBackupThroughput >= 50.0 ? "✓ MET" : "✗ NOT MET").append("</td>\n");
        analysis.append("            </tr>\n");

        // Memory usage target
        double avgMemoryUsage = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
                .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("memory_used_mb", 0.0))
                .average()
                .orElse(0.0);

        analysis.append("            <tr>\n");
        analysis.append("                <td>Memory Usage</td>\n");
        analysis.append("                <td><500 MB</td>\n");
        analysis.append("                <td>").append(String.format("%.2f MB", avgMemoryUsage)).append("</td>\n");
        analysis.append("                <td class=\"").append(avgMemoryUsage <= 500.0 ? "target-met" : "target-not-met").append("\">");
        analysis.append(avgMemoryUsage <= 500.0 ? "✓ MET" : "✗ NOT MET").append("</td>\n");
        analysis.append("            </tr>\n");

        analysis.append("        </table>\n");
        analysis.append("    </div>\n");

        return analysis.toString();
    }

    /**
        * Generates recommendations section for HTML.
        */
    private String generateRecommendations() {
        StringBuilder recommendations = new StringBuilder();

        recommendations.append("    <div class=\"recommendations\">\n");
        recommendations.append("        <h3>Performance Recommendations</h3>\n");
        recommendations.append("        <ul>\n");
        recommendations.append(generateHtmlRecommendations());
        recommendations.append("        </ul>\n");
        recommendations.append("    </div>\n");

        return recommendations.toString();
    }

    /**
        * Generates recommendations text.
        */
    private String generateTextRecommendations() {
        StringBuilder recs = new StringBuilder();

        // Analyze performance and generate recommendations
        double avgThroughput = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                .average()
                .orElse(0.0);

        double avgMemory = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
                .mapToDouble(m -> (Double) m.getMetrics().get("memory_used_mb"))
                .average()
                .orElse(0.0);

        if (avgThroughput < 50.0) {
            recs.append("• Consider optimizing chunk sizes for better throughput\n");
            recs.append("• Review I/O patterns and potential bottlenecks\n");
        }

        if (avgMemory > 500.0) {
            recs.append("• Memory usage is high, consider implementing memory pooling\n");
            recs.append("• Review memory allocation patterns in backup operations\n");
        }

        // Check deduplication efficiency
        boolean hasDeduplicationTests = allMetrics.stream()
                .anyMatch(m -> m.getBenchmarkName().contains("Deduplication"));

        if (hasDeduplicationTests) {
            double avgDeduplicationRatio = allMetrics.stream()
                    .filter(m -> m.getBenchmarkName().contains("Deduplication"))
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("deduplication_ratio", 1.0))
                    .average()
                    .orElse(1.0);

            if (avgDeduplicationRatio < 2.0) {
                recs.append("• Deduplication efficiency is low, review chunking strategy\n");
                recs.append("• Consider smaller chunk sizes for better deduplication\n");
            }
        }

        // Check concurrency performance
        boolean hasConcurrencyTests = allMetrics.stream()
                .anyMatch(m -> m.getBenchmarkName().contains("Concurrency"));

        if (hasConcurrencyTests) {
            double avgConcurrencyEfficiency = allMetrics.stream()
                    .filter(m -> m.getBenchmarkName().contains("Concurrency"))
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("concurrency_efficiency", 1.0))
                    .average()
                    .orElse(1.0);

            if (avgConcurrencyEfficiency < 0.6) {
                recs.append("• Concurrency efficiency is low, review resource contention\n");
                recs.append("• Consider optimizing lock contention and thread coordination\n");
            }
        }

        if (recs.length() == 0) {
            recs.append("• All performance targets are being met successfully\n");
            recs.append("• Continue monitoring for performance regression\n");
        }

        return recs.toString();
    }

    /**
        * Generates recommendations HTML.
        */
    private String generateHtmlRecommendations() {
        StringBuilder recs = new StringBuilder();

        // Analyze performance and generate recommendations
        double avgThroughput = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                .average()
                .orElse(0.0);

        double avgMemory = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
                .mapToDouble(m -> (Double) m.getMetrics().get("memory_used_mb"))
                .average()
                .orElse(0.0);

        if (avgThroughput < 50.0) {
            recs.append("<li>Consider optimizing chunk sizes for better throughput</li>\n");
            recs.append("<li>Review I/O patterns and potential bottlenecks</li>\n");
        }

        if (avgMemory > 500.0) {
            recs.append("<li>Memory usage is high, consider implementing memory pooling</li>\n");
            recs.append("<li>Review memory allocation patterns in backup operations</li>\n");
        }

        // Check deduplication efficiency
        boolean hasDeduplicationTests = allMetrics.stream()
                .anyMatch(m -> m.getBenchmarkName().contains("Deduplication"));

        if (hasDeduplicationTests) {
            double avgDeduplicationRatio = allMetrics.stream()
                    .filter(m -> m.getBenchmarkName().contains("Deduplication"))
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("deduplication_ratio", 1.0))
                    .average()
                    .orElse(1.0);

            if (avgDeduplicationRatio < 2.0) {
                recs.append("<li>Deduplication efficiency is low, review chunking strategy</li>\n");
                recs.append("<li>Consider smaller chunk sizes for better deduplication</li>\n");
            }
        }

        // Check concurrency performance
        boolean hasConcurrencyTests = allMetrics.stream()
                .anyMatch(m -> m.getBenchmarkName().contains("Concurrency"));

        if (hasConcurrencyTests) {
            double avgConcurrencyEfficiency = allMetrics.stream()
                    .filter(m -> m.getBenchmarkName().contains("Concurrency"))
                    .mapToDouble(m -> (Double) m.getMetrics().getOrDefault("concurrency_efficiency", 1.0))
                    .average()
                    .orElse(1.0);

            if (avgConcurrencyEfficiency < 0.6) {
                recs.append("<li>Concurrency efficiency is low, review resource contention</li>\n");
                recs.append("<li>Consider optimizing lock contention and thread coordination</li>\n");
            }
        }

        if (recs.length() == 0) {
            recs.append("<li>All performance targets are being met successfully</li>\n");
            recs.append("<li>Continue monitoring for performance regression</li>\n");
        }

        return recs.toString();
    }

    /**
        * Generates JSON executive summary.
        */
    private String generateJsonExecutiveSummary() {
        StringBuilder summary = new StringBuilder();

        // Calculate overall statistics
        double avgThroughput = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("throughput_mbps"))
                .mapToDouble(m -> (Double) m.getMetrics().get("throughput_mbps"))
                .average()
                .orElse(0.0);

        double avgMemory = allMetrics.stream()
                .filter(m -> m.getMetrics().containsKey("memory_used_mb"))
                .mapToDouble(m -> (Double) m.getMetrics().get("memory_used_mb"))
                .average()
                .orElse(0.0);

        summary.append("    \"averageThroughput\": ").append(String.format("%.2f", avgThroughput)).append(",\n");
        summary.append("    \"averageMemoryUsage\": ").append(String.format("%.2f", avgMemory)).append(",\n");
        summary.append("    \"totalBenchmarks\": ").append(allMetrics.size()).append(",\n");

        // Performance targets
        summary.append("    \"targets\": {\n");
        summary.append("      \"backupThroughput\": {\n");
        summary.append("        \"required\": 50.0,\n");
        summary.append("        \"achieved\": ").append(String.format("%.2f", avgThroughput)).append(",\n");
        summary.append("        \"met\": ").append(avgThroughput >= 50.0).append("\n");
        summary.append("      },\n");
        summary.append("      \"memoryUsage\": {\n");
        summary.append("        \"required\": 500.0,\n");
        summary.append("        \"achieved\": ").append(String.format("%.2f", avgMemory)).append(",\n");
        summary.append("        \"met\": ").append(avgMemory <= 500.0).append("\n");
        summary.append("      }\n");
        summary.append("    }\n");

        return summary.toString();
    }

    /**
        * Generates charts section for HTML.
        */
    private String generateChartsSection() {
        StringBuilder charts = new StringBuilder();

        charts.append("    <div class=\"chart-container\">\n");
        charts.append("        <h2>Performance Charts</h2>\n");
        charts.append("        <p>Interactive charts can be generated from the chart data file.</p>\n");
        charts.append("        <div id=\"chart-placeholder\">\n");
        charts.append("            <p>Chart visualization would be rendered here with JavaScript library</p>\n");
        charts.append("        </div>\n");
        charts.append("    </div>\n");

        return charts.toString();
    }

    /**
        * Generates raw data section for HTML.
        */
    private String generateRawDataSection() {
        StringBuilder rawData = new StringBuilder();

        rawData.append("    <div class=\"raw-data\">\n");
        rawData.append("        <h2>Raw Benchmark Data</h2>\n");
        rawData.append("        <table>\n");
        rawData.append("            <tr><th>Benchmark</th><th>Duration (ms)</th><th>Throughput (MB/s)</th><th>Memory (MB)</th><th>Files</th><th>Chunks</th></tr>\n");

        for (PerformanceMetrics metrics : allMetrics) {
            rawData.append("            <tr>\n");
            rawData.append("                <td>").append(metrics.getBenchmarkName()).append("</td>\n");
            rawData.append("                <td>").append(metrics.getDurationMs()).append("</td>\n");

            Double throughput = (Double) metrics.getMetrics().get("throughput_mbps");
            rawData.append("                <td>").append(throughput != null ? String.format("%.2f", throughput) : "").append("</td>\n");

            Double memory = (Double) metrics.getMetrics().get("memory_used_mb");
            rawData.append("                <td>").append(memory != null ? String.format("%.2f", memory) : "").append("</td>\n");

            Object filesObj = metrics.getMetrics().get("file_count");
            String filesStr = "";
            if (filesObj != null) {
                if (filesObj instanceof Long) {
                    filesStr = ((Long) filesObj).toString();
                } else if (filesObj instanceof Integer) {
                    filesStr = ((Integer) filesObj).toString();
                } else {
                    filesStr = filesObj.toString();
                }
            }
            rawData.append("                <td>").append(filesStr).append("</td>\n");

            Object chunksObj = metrics.getMetrics().get("chunks_created");
            String chunksStr = "";
            if (chunksObj != null) {
                if (chunksObj instanceof Long) {
                    chunksStr = ((Long) chunksObj).toString();
                } else if (chunksObj instanceof Integer) {
                    chunksStr = ((Integer) chunksObj).toString();
                } else {
                    chunksStr = chunksObj.toString();
                }
            }
            rawData.append("                <td>").append(chunksStr).append("</td>\n");

            rawData.append("            </tr>\n");
        }

        rawData.append("        </table>\n");
        rawData.append("    </div>\n");

        return rawData.toString();
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
}