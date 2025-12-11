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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Master test suite for all async components.
 * Coordinates execution of all async tests and provides comprehensive
 * reporting.
 */
public final class AsyncTestSuite {

    private static final List<Class<?>> UNIT_TEST_CLASSES = Arrays.asList(
            AsyncByteBufferPoolTest.class,
            AsyncFileChunkerTest.class,
            AsyncChunkHandlerComprehensiveTest.class,
            AsyncByteBufferPoolComprehensiveTest.class,
            ThreadPoolManagerTest.class,
            AsyncBatchProcessorTest.class,
            CompletionHandlerTest.class);

    private static final List<Class<?>> INTEGRATION_TEST_CLASSES = Arrays.asList(
    // Integration tests - add actual integration test classes when they exist
    // AsyncIntegrationTestSuite.class,
    // AsyncComponentCoordinationTest.class,
    // AsyncResourceManagementTest.class,
    // AsyncErrorPropagationTest.class
    );

    private static final List<Class<?>> PERFORMANCE_TEST_CLASSES = Arrays.asList(
    // Performance tests - add actual performance test classes when they exist
    // AsyncPerformanceTestSuite.class,
    // AsyncConcurrencyTest.class,
    // AsyncScalabilityTest.class,
    // AsyncResourceExhaustionTest.class
    );

    private static final List<Class<?>> ERROR_HANDLING_TEST_CLASSES = Arrays.asList(
    // Error handling tests - add actual error handling test classes when they exist
    // AsyncErrorHandlingTest.class,
    // AsyncTimeoutTest.class,
    // AsyncResourceLeakTest.class,
    // AsyncBoundaryConditionTest.class
    );

    private final AsyncTestRunner testRunner;
    private final AsyncTestReporter testReporter;
    private final AsyncTestValidator testValidator;
    private final TestSuiteConfiguration configuration;

    private AsyncTestSuite(TestSuiteConfiguration configuration) {
        this.configuration = configuration;
        this.testRunner = new AsyncTestRunner(configuration.getRunnerConfiguration());
        this.testReporter = new AsyncTestReporter(configuration.getReporterConfiguration());
        this.testValidator = new AsyncTestValidator(configuration.getValidatorConfiguration());
    }

    /**
     * Creates a new test suite with default configuration.
     */
    public static AsyncTestSuite create() {
        return new AsyncTestSuite(TestSuiteConfiguration.defaultConfiguration());
    }

    /**
     * Creates a new test suite with custom configuration.
     */
    public static AsyncTestSuite create(TestSuiteConfiguration configuration) {
        return new AsyncTestSuite(configuration);
    }

    /**
     * Runs all async tests and returns a comprehensive result.
     */
    public CompletableFuture<TestSuiteResult> runAllTestsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return runAllTests();
            } catch (Exception e) {
                throw new RuntimeException("Test suite execution failed", e);
            }
        });
    }

    /**
     * Runs all async tests synchronously.
     */
    public TestSuiteResult runAllTests() {
        Instant startTime = Instant.now();
        TestSuiteResult result = new TestSuiteResult(startTime);

        try {
            System.out.println("Starting AsyncTestSuite execution...");
            System.out.println("Configuration: " + configuration.getName());

            // Run unit tests
            if (configuration.isRunUnitTests()) {
                TestCategoryResult unitResult = runTestCategory("Unit Tests", UNIT_TEST_CLASSES);
                result.addCategoryResult(unitResult);
            }

            // Run integration tests
            if (configuration.isRunIntegrationTests()) {
                TestCategoryResult integrationResult = runTestCategory("Integration Tests", INTEGRATION_TEST_CLASSES);
                result.addCategoryResult(integrationResult);
            }

            // Run performance tests
            if (configuration.isRunPerformanceTests()) {
                TestCategoryResult performanceResult = runTestCategory("Performance Tests", PERFORMANCE_TEST_CLASSES);
                result.addCategoryResult(performanceResult);
            }

            // Run error handling tests
            if (configuration.isRunErrorHandlingTests()) {
                TestCategoryResult errorHandlingResult = runTestCategory("Error Handling Tests",
                        ERROR_HANDLING_TEST_CLASSES);
                result.addCategoryResult(errorHandlingResult);
            }

            // Validate test coverage and quality
            if (configuration.isRunValidation()) {
                ValidationResult validationResult = testValidator.validateSuite(result);
                result.setValidationResult(validationResult);
            }

            result.setSuccess(true);
            System.out.println("AsyncTestSuite execution completed successfully.");

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            System.err.println("AsyncTestSuite execution failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            result.setEndTime(Instant.now());
            result.setDuration(Duration.between(startTime, result.getEndTime()));

            // Generate and display report
            testReporter.generateReport(result);
        }

        return result;
    }

    /**
     * Runs a specific category of tests.
     */
    private TestCategoryResult runTestCategory(String categoryName, List<Class<?>> testClasses) {
        System.out.println("Running " + categoryName + "...");
        Instant startTime = Instant.now();
        TestCategoryResult categoryResult = new TestCategoryResult(categoryName, startTime);

        ExecutorService executor = Executors.newFixedThreadPool(configuration.getMaxConcurrentTestClasses());

        try {
            List<CompletableFuture<ClassTestResult>> futures = new ArrayList<>();

            for (Class<?> testClass : testClasses) {
                CompletableFuture<ClassTestResult> future = CompletableFuture.supplyAsync(() -> {
                    return testRunner.runTestClass(testClass);
                }, executor);
                futures.add(future);
            }

            // Wait for all test classes to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(configuration.getCategoryTimeout().toMillis(), TimeUnit.MILLISECONDS);

            // Collect results
            for (CompletableFuture<ClassTestResult> future : futures) {
                ClassTestResult classResult = future.get();
                categoryResult.addClassResult(classResult);
            }

        } catch (Exception e) {
            categoryResult.setSuccess(false);
            categoryResult.setErrorMessage(e.getMessage());
            System.err.println("Failed to run " + categoryName + ": " + e.getMessage());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        categoryResult.setEndTime(Instant.now());
        categoryResult.setDuration(Duration.between(startTime, categoryResult.getEndTime()));

        System.out.println("Completed " + categoryName + " - " +
                (categoryResult.isSuccess() ? "SUCCESS" : "FAILED"));

        return categoryResult;
    }

    /**
     * Configuration for the test suite.
     */
    public static class TestSuiteConfiguration {
        private final String name;
        private final boolean runUnitTests;
        private final boolean runIntegrationTests;
        private final boolean runPerformanceTests;
        private final boolean runErrorHandlingTests;
        private final boolean runValidation;
        private final int maxConcurrentTestClasses;
        private final Duration categoryTimeout;
        private final AsyncTestRunner.RunnerConfiguration runnerConfiguration;
        private final AsyncTestReporter.ReporterConfiguration reporterConfiguration;
        private final AsyncTestValidator.ValidatorConfiguration validatorConfiguration;

        private TestSuiteConfiguration(Builder builder) {
            this.name = builder.name;
            this.runUnitTests = builder.runUnitTests;
            this.runIntegrationTests = builder.runIntegrationTests;
            this.runPerformanceTests = builder.runPerformanceTests;
            this.runErrorHandlingTests = builder.runErrorHandlingTests;
            this.runValidation = builder.runValidation;
            this.maxConcurrentTestClasses = builder.maxConcurrentTestClasses;
            this.categoryTimeout = builder.categoryTimeout;
            this.runnerConfiguration = builder.runnerConfiguration;
            this.reporterConfiguration = builder.reporterConfiguration;
            this.validatorConfiguration = builder.validatorConfiguration;
        }

        public static TestSuiteConfiguration defaultConfiguration() {
            return new Builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getName() {
            return name;
        }

        public boolean isRunUnitTests() {
            return runUnitTests;
        }

        public boolean isRunIntegrationTests() {
            return runIntegrationTests;
        }

        public boolean isRunPerformanceTests() {
            return runPerformanceTests;
        }

        public boolean isRunErrorHandlingTests() {
            return runErrorHandlingTests;
        }

        public boolean isRunValidation() {
            return runValidation;
        }

        public int getMaxConcurrentTestClasses() {
            return maxConcurrentTestClasses;
        }

        public Duration getCategoryTimeout() {
            return categoryTimeout;
        }

        public AsyncTestRunner.RunnerConfiguration getRunnerConfiguration() {
            return runnerConfiguration;
        }

        public AsyncTestReporter.ReporterConfiguration getReporterConfiguration() {
            return reporterConfiguration;
        }

        public AsyncTestValidator.ValidatorConfiguration getValidatorConfiguration() {
            return validatorConfiguration;
        }

        /**
         * Builder for TestSuiteConfiguration.
         */
        public static class Builder {
            private String name = "AsyncTestSuite";
            private boolean runUnitTests = true;
            private boolean runIntegrationTests = true;
            private boolean runPerformanceTests = true;
            private boolean runErrorHandlingTests = true;
            private boolean runValidation = true;
            private int maxConcurrentTestClasses = Runtime.getRuntime().availableProcessors();
            private Duration categoryTimeout = Duration.ofMinutes(5);
            private AsyncTestRunner.RunnerConfiguration runnerConfiguration = AsyncTestRunner.RunnerConfiguration
                    .defaultConfiguration();
            private AsyncTestReporter.ReporterConfiguration reporterConfiguration = AsyncTestReporter.ReporterConfiguration
                    .defaultConfiguration();
            private AsyncTestValidator.ValidatorConfiguration validatorConfiguration = AsyncTestValidator.ValidatorConfiguration
                    .defaultConfiguration();

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder runUnitTests(boolean runUnitTests) {
                this.runUnitTests = runUnitTests;
                return this;
            }

            public Builder runIntegrationTests(boolean runIntegrationTests) {
                this.runIntegrationTests = runIntegrationTests;
                return this;
            }

            public Builder runPerformanceTests(boolean runPerformanceTests) {
                this.runPerformanceTests = runPerformanceTests;
                return this;
            }

            public Builder runErrorHandlingTests(boolean runErrorHandlingTests) {
                this.runErrorHandlingTests = runErrorHandlingTests;
                return this;
            }

            public Builder runValidation(boolean runValidation) {
                this.runValidation = runValidation;
                return this;
            }

            public Builder maxConcurrentTestClasses(int maxConcurrentTestClasses) {
                this.maxConcurrentTestClasses = maxConcurrentTestClasses;
                return this;
            }

            public Builder categoryTimeout(Duration categoryTimeout) {
                this.categoryTimeout = categoryTimeout;
                return this;
            }

            public Builder runnerConfiguration(AsyncTestRunner.RunnerConfiguration runnerConfiguration) {
                this.runnerConfiguration = runnerConfiguration;
                return this;
            }

            public Builder reporterConfiguration(AsyncTestReporter.ReporterConfiguration reporterConfiguration) {
                this.reporterConfiguration = reporterConfiguration;
                return this;
            }

            public Builder validatorConfiguration(AsyncTestValidator.ValidatorConfiguration validatorConfiguration) {
                this.validatorConfiguration = validatorConfiguration;
                return this;
            }

            public TestSuiteConfiguration build() {
                return new TestSuiteConfiguration(this);
            }
        }
    }

    /**
     * Result of the entire test suite execution.
     */
    public static class TestSuiteResult {
        private final Instant startTime;
        private Instant endTime;
        private Duration duration;
        private boolean success = false;
        private String errorMessage;
        private final List<TestCategoryResult> categoryResults = new ArrayList<>();
        private ValidationResult validationResult;

        public TestSuiteResult(Instant startTime) {
            this.startTime = startTime;
        }

        // Getters and setters
        public Instant getStartTime() {
            return startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public Duration getDuration() {
            return duration;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<TestCategoryResult> getCategoryResults() {
            return categoryResults;
        }

        public ValidationResult getValidationResult() {
            return validationResult;
        }

        void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }

        void setDuration(Duration duration) {
            this.duration = duration;
        }

        void setSuccess(boolean success) {
            this.success = success;
        }

        void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        void setValidationResult(ValidationResult validationResult) {
            this.validationResult = validationResult;
        }

        void addCategoryResult(TestCategoryResult categoryResult) {
            categoryResults.add(categoryResult);
        }

        /**
         * Gets the total number of test classes executed.
         */
        public int getTotalTestClasses() {
            return categoryResults.stream()
                    .mapToInt(category -> category.getClassResults().size())
                    .sum();
        }

        /**
         * Gets the total number of test methods executed.
         */
        public int getTotalTestMethods() {
            return categoryResults.stream()
                    .flatMap(category -> category.getClassResults().stream())
                    .mapToInt(classResult -> classResult.getTestMethodCount())
                    .sum();
        }

        /**
         * Gets the total number of passed tests.
         */
        public int getTotalPassedTests() {
            return categoryResults.stream()
                    .flatMap(category -> category.getClassResults().stream())
                    .mapToInt(ClassTestResult::getPassedTestCount)
                    .sum();
        }

        /**
         * Gets the total number of failed tests.
         */
        public int getTotalFailedTests() {
            return categoryResults.stream()
                    .flatMap(category -> category.getClassResults().stream())
                    .mapToInt(ClassTestResult::getFailedTestCount)
                    .sum();
        }

        /**
         * Gets the total number of skipped tests.
         */
        public int getTotalSkippedTests() {
            return categoryResults.stream()
                    .flatMap(category -> category.getClassResults().stream())
                    .mapToInt(ClassTestResult::getSkippedTestCount)
                    .sum();
        }

        /**
         * Calculates the overall success rate.
         */
        public double getSuccessRate() {
            int total = getTotalTestMethods();
            return total > 0 ? (double) getTotalPassedTests() / total : 0.0;
        }

        /**
         * Checks if all categories passed.
         */
        public boolean allCategoriesPassed() {
            return categoryResults.stream().allMatch(TestCategoryResult::isSuccess);
        }

        /**
         * Checks if validation passed.
         */
        public boolean validationPassed() {
            return validationResult == null || validationResult.isValid();
        }
    }

    /**
     * Result of a test category execution.
     */
    public static class TestCategoryResult {
        private final String categoryName;
        private final Instant startTime;
        private Instant endTime;
        private Duration duration;
        private boolean success = false;
        private String errorMessage;
        private final List<ClassTestResult> classResults = new ArrayList<>();

        public TestCategoryResult(String categoryName, Instant startTime) {
            this.categoryName = categoryName;
            this.startTime = startTime;
        }

        // Getters and setters
        public String getCategoryName() {
            return categoryName;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public Duration getDuration() {
            return duration;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<ClassTestResult> getClassResults() {
            return classResults;
        }

        void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }

        void setDuration(Duration duration) {
            this.duration = duration;
        }

        void setSuccess(boolean success) {
            this.success = success;
        }

        void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        void addClassResult(ClassTestResult classResult) {
            classResults.add(classResult);
        }

        /**
         * Gets the total number of test methods in this category.
         */
        public int getTotalTestMethods() {
            return classResults.stream()
                    .mapToInt(ClassTestResult::getTestMethodCount)
                    .sum();
        }

        /**
         * Gets the total number of passed tests in this category.
         */
        public int getTotalPassedTests() {
            return classResults.stream()
                    .mapToInt(ClassTestResult::getPassedTestCount)
                    .sum();
        }

        /**
         * Gets the total number of failed tests in this category.
         */
        public int getTotalFailedTests() {
            return classResults.stream()
                    .mapToInt(ClassTestResult::getFailedTestCount)
                    .sum();
        }

        /**
         * Gets the total number of skipped tests in this category.
         */
        public int getTotalSkippedTests() {
            return classResults.stream()
                    .mapToInt(ClassTestResult::getSkippedTestCount)
                    .sum();
        }
    }

    /**
     * Result of a single test class execution.
     */
    public static class ClassTestResult {
        private final String className;
        private final boolean success;
        private final int testMethodCount;
        private final int passedTestCount;
        private final int failedTestCount;
        private final int skippedTestCount;
        private final Duration executionTime;
        private final String errorMessage;

        public ClassTestResult(String className, boolean success, int testMethodCount,
                int passedTestCount, int failedTestCount, int skippedTestCount,
                Duration executionTime, String errorMessage) {
            this.className = className;
            this.success = success;
            this.testMethodCount = testMethodCount;
            this.passedTestCount = passedTestCount;
            this.failedTestCount = failedTestCount;
            this.skippedTestCount = skippedTestCount;
            this.executionTime = executionTime;
            this.errorMessage = errorMessage;
        }

        // Getters
        public String getClassName() {
            return className;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getTestMethodCount() {
            return testMethodCount;
        }

        public int getPassedTestCount() {
            return passedTestCount;
        }

        public int getFailedTestCount() {
            return failedTestCount;
        }

        public int getSkippedTestCount() {
            return skippedTestCount;
        }

        public Duration getExecutionTime() {
            return executionTime;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Result of validation operations.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final List<String> warnings;

        public ValidationResult(boolean valid, String message, List<String> warnings) {
            this.valid = valid;
            this.message = message;
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, new ArrayList<>());
        }

        public static ValidationResult valid(String message) {
            return new ValidationResult(true, message, new ArrayList<>());
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, new ArrayList<>());
        }

        public static ValidationResult invalid(String message, List<String> warnings) {
            return new ValidationResult(false, message, warnings);
        }
    }
}