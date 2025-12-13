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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for validating the benchmark environment.
 * Ensures that the system meets minimum requirements for accurate benchmarking.
 */
public class BenchmarkEnvironmentValidator {

    private final List<String> validationResults;
    private final RuntimeMXBean runtimeBean;
    private final MemoryMXBean memoryBean;
    private final OperatingSystemMXBean osBean;

    /**
     * Creates a new benchmark environment validator.
     */
    public BenchmarkEnvironmentValidator() {
        this.validationResults = new ArrayList<>();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
    }

    /**
     * Validates the benchmark environment.
     *
     * @return true if environment is suitable for benchmarking
     */
    public boolean validateEnvironment() {
        validationResults.clear();

        boolean isValid = true;

        // Check minimum memory requirements
        if (!validateMemoryRequirements()) {
            isValid = false;
        }

        // Check CPU requirements
        if (!validateCpuRequirements()) {
            isValid = false;
        }

        // Check disk space
        if (!validateDiskSpace()) {
            isValid = false;
        }

        // Check system load
        if (!validateSystemLoad()) {
            isValid = false;
        }

        // Check Java version
        if (!validateJavaVersion()) {
            isValid = false;
        }

        // Print validation results
        printValidationResults();

        return isValid;
    }

    /**
     * Gets validation results.
     *
     * @return list of validation results
     */
    public List<String> getValidationResults() {
        return new ArrayList<>(validationResults);
    }

    /**
     * Validates memory requirements.
     *
     * @return true if memory requirements are met
     */
    private boolean validateMemoryRequirements() {
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        long requiredMemory = 1024 * 1024 * 1024; // 1GB minimum

        if (maxMemory < requiredMemory) {
            validationResults.add(String.format(
                "❌ Insufficient memory: %d MB available, %d MB required (minimum 1GB)",
                maxMemory / (1024 * 1024), requiredMemory / (1024 * 1024)));
            return false;
        }

        validationResults.add(String.format(
            "✅ Sufficient memory: %d MB available",
            maxMemory / (1024 * 1024)));
        return true;
    }

    /**
     * Validates CPU requirements.
     *
     * @return true if CPU requirements are met
     */
    private boolean validateCpuRequirements() {
        int availableProcessors = osBean.getAvailableProcessors();
        int requiredProcessors = 2; // Minimum 2 cores for meaningful async testing

        if (availableProcessors < requiredProcessors) {
            validationResults.add(String.format(
                "❌ Insufficient CPU cores: %d available, %d required (minimum 2 cores)",
                availableProcessors, requiredProcessors));
            return false;
        }

        validationResults.add(String.format(
            "✅ Sufficient CPU cores: %d available",
            availableProcessors));
        return true;
    }

    /**
     * Validates disk space requirements.
     *
     * @return true if disk space requirements are met
     */
    private boolean validateDiskSpace() {
        try {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"));
            long freeSpace = tempDir.getFreeSpace();
            long requiredSpace = 2L * 1024 * 1024 * 1024; // 2GB minimum (reduced from 10GB)

            if (freeSpace < requiredSpace) {
                validationResults.add(String.format(
                    "❌ Insufficient disk space: %d GB available, %d GB required (minimum 2GB)",
                    freeSpace / (1024 * 1024 * 1024), requiredSpace / (1024 * 1024 * 1024)));
                return false;
            }

            validationResults.add(String.format(
                "✅ Sufficient disk space: %d GB available",
                freeSpace / (1024 * 1024 * 1024)));
            return true;
        } catch (Exception e) {
            validationResults.add("❌ Unable to check disk space: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates system load.
     *
     * @return true if system load is acceptable
     */
    private boolean validateSystemLoad() {
        try {
            double systemLoadAverage = osBean.getSystemLoadAverage();

            if (systemLoadAverage < 0) {
                validationResults.add("⚠️  Unable to determine system load average");
                return true; // Continue anyway
            }

            double maxAcceptableLoad = osBean.getAvailableProcessors() * 0.8; // 80% of CPU capacity

            if (systemLoadAverage > maxAcceptableLoad) {
                validationResults.add(String.format(
                    "❌ High system load: %.2f, maximum acceptable: %.2f",
                    systemLoadAverage, maxAcceptableLoad));
                return false;
            }

            validationResults.add(String.format(
                "✅ Acceptable system load: %.2f",
                systemLoadAverage));
            return true;
        } catch (Exception e) {
            validationResults.add("❌ Unable to check system load: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates Java version.
     *
     * @return true if Java version is supported
     */
    private boolean validateJavaVersion() {
        String javaVersion = System.getProperty("java.version");

        if (javaVersion == null) {
            validationResults.add("❌ Unable to determine Java version");
            return false;
        }

        // Check for Java 11 or higher
        if (javaVersion.startsWith("1.") || javaVersion.startsWith("9.") ||
            javaVersion.startsWith("10.")) {
            validationResults.add(String.format(
                "❌ Unsupported Java version: %s (requires Java 11 or higher)",
                javaVersion));
            return false;
        }

        validationResults.add(String.format(
            "✅ Java version: %s",
            javaVersion));
        return true;
    }

    /**
     * Prints validation results.
     */
    private void printValidationResults() {
        System.out.println("\n=== BENCHMARK ENVIRONMENT VALIDATION ===");

        for (String result : validationResults) {
            System.out.println(result);
        }

        System.out.println("\n=== SYSTEM INFORMATION ===");
        System.out.println("Operating System: " + osBean.getName() + " " + osBean.getVersion());
        System.out.println("Architecture: " + osBean.getArch());
        System.out.println("Available Processors: " + osBean.getAvailableProcessors());
        System.out.println("Max Memory: " + (memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024)) + " MB");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Java Home: " + System.getProperty("java.home"));
        System.out.println("Temp Directory: " + System.getProperty("java.io.tmpdir"));

        System.out.println("===============================");
    }

    /**
     * Gets environment summary.
     *
     * @return formatted environment summary
     */
    public String getEnvironmentSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("System: ").append(osBean.getName()).append("\n");
        summary.append("Cores: ").append(osBean.getAvailableProcessors()).append("\n");
        summary.append("Memory: ").append(memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024)).append(" MB\n");
        summary.append("Java: ").append(System.getProperty("java.version"));
        return summary.toString();
    }
}