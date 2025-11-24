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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validates test coverage and quality requirements.
 * Provides comprehensive validation of test results against predefined criteria.
 */
public final class AsyncTestValidator {

    private final ValidatorConfiguration configuration;

    public AsyncTestValidator(ValidatorConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Creates a test validator with default configuration.
     */
    public static AsyncTestValidator create() {
        return new AsyncTestValidator(ValidatorConfiguration.defaultConfiguration());
    }

    /**
     * Creates a test validator with custom configuration.
     */
    public static AsyncTestValidator create(ValidatorConfiguration configuration) {
        return new AsyncTestValidator(configuration);
    }

    /**
     * Validates the entire test suite result.
     */
    public AsyncTestSuite.ValidationResult validateSuite(AsyncTestSuite.TestSuiteResult result) {
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;
        StringBuilder message = new StringBuilder();

        // Validate overall success rate
        double successRate = result.getSuccessRate();
        if (successRate < configuration.getMinSuccessRate()) {
            isValid = false;
            message.append(String.format("Success rate %.2f%% is below minimum %.2f%%. ", 
                successRate * 100, configuration.getMinSuccessRate() * 100));
        }

        // Validate test coverage
        if (configuration.isValidateCoverage()) {
            CoverageValidationResult coverageResult = validateTestCoverage(result);
            warnings.addAll(coverageResult.getWarnings());
            if (!coverageResult.isValid()) {
                isValid = false;
                message.append("Test coverage validation failed. ");
            }
        }

        // Validate performance requirements
        if (configuration.isValidatePerformance()) {
            PerformanceValidationResult performanceResult = validatePerformanceRequirements(result);
            warnings.addAll(performanceResult.getWarnings());
            if (!performanceResult.isValid()) {
                isValid = false;
                message.append("Performance requirements not met. ");
            }
        }

        // Validate error handling
        if (configuration.isValidateErrorHandling()) {
            ErrorHandlingValidationResult errorResult = validateErrorHandling(result);
            warnings.addAll(errorResult.getWarnings());
            if (!errorResult.isValid()) {
                isValid = false;
                message.append("Error handling validation failed. ");
            }
        }

        // Validate resource management
        if (configuration.isValidateResourceManagement()) {
            ResourceManagementValidationResult resourceResult = validateResourceManagement(result);
            warnings.addAll(resourceResult.getWarnings());
            if (!resourceResult.isValid()) {
                isValid = false;
                message.append("Resource management validation failed. ");
            }
        }

        String finalMessage = message.length() > 0 ? message.toString().trim() : 
            (isValid ? "All validation criteria met" : "Validation failed");

        return new AsyncTestSuite.ValidationResult(isValid, finalMessage, warnings);
    }

    /**
     * Validates test coverage requirements.
     */
    private CoverageValidationResult validateTestCoverage(AsyncTestSuite.TestSuiteResult result) {
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;
        StringBuilder message = new StringBuilder();

        // Check if all required test categories are present
        List<String> presentCategories = result.getCategoryResults().stream()
            .map(AsyncTestSuite.TestCategoryResult::getCategoryName)
            .toList();

        for (String requiredCategory : configuration.getRequiredTestCategories()) {
            if (!presentCategories.contains(requiredCategory)) {
                isValid = false;
                message.append("Missing required test category: ").append(requiredCategory).append(". ");
            }
        }

        // Validate minimum test count per category
        for (AsyncTestSuite.TestCategoryResult category : result.getCategoryResults()) {
            int testCount = category.getTotalTestMethods();
            int minTests = configuration.getMinTestsPerCategory();
            
            if (testCount < minTests) {
                warnings.add(String.format("Category '%s' has only %d tests (minimum: %d)", 
                    category.getCategoryName(), testCount, minTests));
            }
        }

        // Validate async component coverage
        if (configuration.isValidateAsyncComponentCoverage()) {
            AsyncComponentCoverageResult componentResult = validateAsyncComponentCoverage(result);
            warnings.addAll(componentResult.getWarnings());
            if (!componentResult.isValid()) {
                isValid = false;
                message.append("Async component coverage incomplete. ");
            }
        }

        String finalMessage = message.length() > 0 ? message.toString().trim() : 
            (isValid ? "Test coverage validation passed" : "Test coverage validation failed");

        return new CoverageValidationResult(isValid, finalMessage, warnings);
    }

    /**
     * Validates async component test coverage.
     */
    private AsyncComponentCoverageResult validateAsyncComponentCoverage(AsyncTestSuite.TestSuiteResult result) {
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;

        // Check if core async components have tests
        List<String> coreComponents = Arrays.asList(
            "AsyncByteBufferPool",
            "AsyncFileChunker", 
            "ThreadPoolManager",
            "AsyncBatchProcessor"
        );

        for (String component : coreComponents) {
            boolean hasTests = result.getCategoryResults().stream()
                .anyMatch(category -> 
                    category.getClassResults().stream()
                        .anyMatch(classResult -> 
                            classResult.getClassName().contains(component)));

            if (!hasTests) {
                warnings.add("Missing tests for core async component: " + component);
            }
        }

        return new AsyncComponentCoverageResult(warnings.isEmpty(), warnings);
    }

    /**
     * Validates performance requirements.
     */
    private PerformanceValidationResult validatePerformanceRequirements(AsyncTestSuite.TestSuiteResult result) {
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;

        // Check execution time limits
        if (result.getDuration().toMillis() > configuration.getMaxSuiteExecutionTimeMs()) {
            isValid = false;
            warnings.add(String.format("Suite execution time %dms exceeds maximum %dms", 
                result.getDuration().toMillis(), configuration.getMaxSuiteExecutionTimeMs()));
        }

        // Check individual category performance
        for (AsyncTestSuite.TestCategoryResult category : result.getCategoryResults()) {
            if (category.getDuration().toMillis() > configuration.getMaxCategoryExecutionTimeMs()) {
                warnings.add(String.format("Category '%s' execution time %dms exceeds maximum %dms", 
                    category.getCategoryName(), category.getDuration().toMillis(), 
                    configuration.getMaxCategoryExecutionTimeMs()));
            }
        }

        return new PerformanceValidationResult(isValid, warnings);
    }

    /**
     * Validates error handling requirements.
     */
    private ErrorHandlingValidationResult validateErrorHandling(AsyncTestSuite.TestSuiteResult result) {
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;

        // Check if error handling tests are present
        boolean hasErrorHandlingTests = result.getCategoryResults().stream()
            .anyMatch(category -> category.getCategoryName().contains("Error Handling"));

        if (!hasErrorHandlingTests) {
            warnings.add("Missing error handling test category");
        }

        // Check for proper test failure handling (no uncaught exceptions)
        for (AsyncTestSuite.TestCategoryResult category : result.getCategoryResults()) {
            for (AsyncTestSuite.ClassTestResult classResult : category.getClassResults()) {
                if (classResult.getErrorMessage() != null && 
                    classResult.getErrorMessage().contains("uncaught")) {
                    warnings.add("Uncaught exception detected in test: " + classResult.getClassName());
                }
            }
        }

        return new ErrorHandlingValidationResult(isValid, warnings);
    }

    /**
     * Validates resource management requirements.
     */
    private ResourceManagementValidationResult validateResourceManagement(AsyncTestSuite.TestSuiteResult result) {
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;

        // Check if resource management tests are present
        boolean hasResourceTests = result.getCategoryResults().stream()
            .anyMatch(category -> 
                category.getCategoryName().contains("Resource") ||
                category.getCategoryName().contains("Leak"));

        if (!hasResourceTests) {
            warnings.add("Missing resource management test category");
        }

        return new ResourceManagementValidationResult(isValid, warnings);
    }

    /**
     * Configuration for test validator.
     */
    public static class ValidatorConfiguration {
        private final double minSuccessRate;
        private final boolean validateCoverage;
        private final boolean validatePerformance;
        private final boolean validateErrorHandling;
        private final boolean validateResourceManagement;
        private final boolean validateAsyncComponentCoverage;
        private final List<String> requiredTestCategories;
        private final int minTestsPerCategory;
        private final long maxSuiteExecutionTimeMs;
        private final long maxCategoryExecutionTimeMs;

        private ValidatorConfiguration(Builder builder) {
            this.minSuccessRate = builder.minSuccessRate;
            this.validateCoverage = builder.validateCoverage;
            this.validatePerformance = builder.validatePerformance;
            this.validateErrorHandling = builder.validateErrorHandling;
            this.validateResourceManagement = builder.validateResourceManagement;
            this.validateAsyncComponentCoverage = builder.validateAsyncComponentCoverage;
            this.requiredTestCategories = new ArrayList<>(builder.requiredTestCategories);
            this.minTestsPerCategory = builder.minTestsPerCategory;
            this.maxSuiteExecutionTimeMs = builder.maxSuiteExecutionTimeMs;
            this.maxCategoryExecutionTimeMs = builder.maxCategoryExecutionTimeMs;
        }

        public static ValidatorConfiguration defaultConfiguration() {
            return new Builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public double getMinSuccessRate() { return minSuccessRate; }
        public boolean isValidateCoverage() { return validateCoverage; }
        public boolean isValidatePerformance() { return validatePerformance; }
        public boolean isValidateErrorHandling() { return validateErrorHandling; }
        public boolean isValidateResourceManagement() { return validateResourceManagement; }
        public boolean isValidateAsyncComponentCoverage() { return validateAsyncComponentCoverage; }
        public List<String> getRequiredTestCategories() { return requiredTestCategories; }
        public int getMinTestsPerCategory() { return minTestsPerCategory; }
        public long getMaxSuiteExecutionTimeMs() { return maxSuiteExecutionTimeMs; }
        public long getMaxCategoryExecutionTimeMs() { return maxCategoryExecutionTimeMs; }

        /**
         * Builder for ValidatorConfiguration.
         */
        public static class Builder {
            private double minSuccessRate = 0.9; // 90% minimum success rate
            private boolean validateCoverage = true;
            private boolean validatePerformance = true;
            private boolean validateErrorHandling = true;
            private boolean validateResourceManagement = true;
            private boolean validateAsyncComponentCoverage = true;
            private List<String> requiredTestCategories = Arrays.asList(
                "Unit Tests", "Integration Tests", "Performance Tests", "Error Handling Tests");
            private int minTestsPerCategory = 5;
            private long maxSuiteExecutionTimeMs = 30 * 60 * 1000; // 30 minutes
            private long maxCategoryExecutionTimeMs = 10 * 60 * 1000; // 10 minutes

            public Builder minSuccessRate(double minSuccessRate) {
                this.minSuccessRate = minSuccessRate;
                return this;
            }

            public Builder validateCoverage(boolean validateCoverage) {
                this.validateCoverage = validateCoverage;
                return this;
            }

            public Builder validatePerformance(boolean validatePerformance) {
                this.validatePerformance = validatePerformance;
                return this;
            }

            public Builder validateErrorHandling(boolean validateErrorHandling) {
                this.validateErrorHandling = validateErrorHandling;
                return this;
            }

            public Builder validateResourceManagement(boolean validateResourceManagement) {
                this.validateResourceManagement = validateResourceManagement;
                return this;
            }

            public Builder validateAsyncComponentCoverage(boolean validateAsyncComponentCoverage) {
                this.validateAsyncComponentCoverage = validateAsyncComponentCoverage;
                return this;
            }

            public Builder requiredTestCategories(List<String> requiredTestCategories) {
                this.requiredTestCategories = new ArrayList<>(requiredTestCategories);
                return this;
            }

            public Builder minTestsPerCategory(int minTestsPerCategory) {
                this.minTestsPerCategory = minTestsPerCategory;
                return this;
            }

            public Builder maxSuiteExecutionTimeMs(long maxSuiteExecutionTimeMs) {
                this.maxSuiteExecutionTimeMs = maxSuiteExecutionTimeMs;
                return this;
            }

            public Builder maxCategoryExecutionTimeMs(long maxCategoryExecutionTimeMs) {
                this.maxCategoryExecutionTimeMs = maxCategoryExecutionTimeMs;
                return this;
            }

            public ValidatorConfiguration build() {
                return new ValidatorConfiguration(this);
            }
        }
    }

    /**
     * Result of coverage validation.
     */
    private static class CoverageValidationResult {
        private final boolean valid;
        private final String message;
        private final List<String> warnings;

        public CoverageValidationResult(boolean valid, String message, List<String> warnings) {
            this.valid = valid;
            this.message = message;
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Result of async component coverage validation.
     */
    private static class AsyncComponentCoverageResult {
        private final boolean valid;
        private final List<String> warnings;

        public AsyncComponentCoverageResult(boolean valid, List<String> warnings) {
            this.valid = valid;
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isValid() { return valid; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Result of performance validation.
     */
    private static class PerformanceValidationResult {
        private final boolean valid;
        private final List<String> warnings;

        public PerformanceValidationResult(boolean valid, List<String> warnings) {
            this.valid = valid;
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isValid() { return valid; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Result of error handling validation.
     */
    private static class ErrorHandlingValidationResult {
        private final boolean valid;
        private final List<String> warnings;

        public ErrorHandlingValidationResult(boolean valid, List<String> warnings) {
            this.valid = valid;
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isValid() { return valid; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Result of resource management validation.
     */
    private static class ResourceManagementValidationResult {
        private final boolean valid;
        private final List<String> warnings;

        public ResourceManagementValidationResult(boolean valid, List<String> warnings) {
            this.valid = valid;
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isValid() { return valid; }
        public List<String> getWarnings() { return warnings; }
    }
}