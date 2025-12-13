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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Custom test runner for async test scenarios.
 * Provides enhanced execution capabilities for async tests with proper timeout
 * handling
 * and resource management.
 */
public final class AsyncTestRunner {

    private final RunnerConfiguration configuration;
    private final ExecutorService executorService;

    public AsyncTestRunner(RunnerConfiguration configuration) {
        this.configuration = configuration;
        this.executorService = Executors.newFixedThreadPool(configuration.getMaxConcurrentTests());
    }

    /**
     * Creates a test runner with default configuration.
     */
    public static AsyncTestRunner create() {
        return new AsyncTestRunner(RunnerConfiguration.defaultConfiguration());
    }

    /**
     * Creates a test runner with custom configuration.
     */
    public static AsyncTestRunner create(RunnerConfiguration configuration) {
        return new AsyncTestRunner(configuration);
    }

    /**
     * Runs all tests in a test class.
     */
    public AsyncTestSuite.ClassTestResult runTestClass(Class<?> testClass) {
        Instant startTime = Instant.now();
        String className = testClass.getSimpleName();

        try {
            System.out.println("Running test class: " + className);

            // Find test methods
            List<Method> testMethods = findTestMethods(testClass);

            // Create test instance
            Object testInstance = createTestInstance(testClass);

            // Run test methods
            List<TestMethodResult> methodResults = new ArrayList<>();

            for (Method testMethod : testMethods) {
                TestMethodResult methodResult = runTestMethod(testInstance, testMethod);
                methodResults.add(methodResult);
            }

            // Calculate class-level results
            int passedCount = methodResults.stream().mapToInt(r -> r.isPassed() ? 1 : 0).sum();
            int failedCount = methodResults.stream().mapToInt(r -> r.isFailed() ? 1 : 0).sum();
            int skippedCount = methodResults.stream().mapToInt(r -> r.isSkipped() ? 1 : 0).sum();

            boolean classSuccess = failedCount == 0;
            Duration executionTime = Duration.between(startTime, Instant.now());

            System.out.println("Completed test class: " + className + " - "
                    + (classSuccess ? "PASSED" : "FAILED")
                    + " (" + passedCount + " passed, " + failedCount + " failed, " + skippedCount + " skipped)");

            return new AsyncTestSuite.ClassTestResult(
                    className, classSuccess, testMethods.size(),
                    passedCount, failedCount, skippedCount, executionTime, null);

        } catch (Exception e) {
            System.err.println("Failed to run test class: " + className + " - " + e.getMessage());
            Duration executionTime = Duration.between(startTime, Instant.now());
            return new AsyncTestSuite.ClassTestResult(
                    className, false, 0, 0, 0, 0, executionTime, e.getMessage());
        }
    }

    /**
     * Runs a single test method.
     */
    private TestMethodResult runTestMethod(Object testInstance, Method testMethod) {
        Instant startTime = Instant.now();
        String methodName = testMethod.getName();

        try {
            // Check for @Test annotation or similar
            if (!isTestMethod(testMethod)) {
                return new TestMethodResult(methodName, TestMethodResult.Status.SKIPPED,
                        Duration.ZERO, "Not a test method");
            }

            // Run the test method with timeout
            CompletableFuture<Void> testFuture = CompletableFuture.runAsync(() -> {
                try {
                    testMethod.invoke(testInstance);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService);

            try {
                testFuture.get(configuration.getTestTimeout().toMillis(), TimeUnit.MILLISECONDS);
                Duration executionTime = Duration.between(startTime, Instant.now());
                return new TestMethodResult(methodName, TestMethodResult.Status.PASSED,
                        executionTime, null);

            } catch (java.util.concurrent.TimeoutException e) {
                Duration executionTime = Duration.between(startTime, Instant.now());
                return new TestMethodResult(methodName, TestMethodResult.Status.FAILED,
                        executionTime, "Test timed out");

            } catch (Exception e) {
                Duration executionTime = Duration.between(startTime, Instant.now());
                return new TestMethodResult(methodName, TestMethodResult.Status.FAILED,
                        executionTime, e.getCause().getMessage());
            }

        } catch (Exception e) {
            Duration executionTime = Duration.between(startTime, Instant.now());
            return new TestMethodResult(methodName, TestMethodResult.Status.FAILED,
                    executionTime, "Test execution failed: " + e.getMessage());
        }
    }

    /**
     * Finds all test methods in a class.
     */
    private List<Method> findTestMethods(Class<?> testClass) {
        List<Method> testMethods = new ArrayList<>();

        for (Method method : testClass.getDeclaredMethods()) {
            if (isTestMethod(method)) {
                testMethods.add(method);
            }
        }

        return testMethods;
    }

    /**
     * Checks if a method is a test method.
     */
    private boolean isTestMethod(Method method) {
        // Simple heuristic: public methods starting with "test" or annotated with @Test
        return (method.getName().startsWith("test")
                && java.lang.reflect.Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 0)
                || method.isAnnotationPresent(org.junit.jupiter.api.Test.class);
    }

    /**
     * Creates an instance of the test class.
     */
    private Object createTestInstance(Class<?> testClass) throws Exception {
        try {
            return testClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test instance: " + testClass.getName(), e);
        }
    }

    /**
     * Shuts down the test runner.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Configuration for the test runner.
     */
    public static class RunnerConfiguration {
        private final int maxConcurrentTests;
        private final Duration testTimeout;
        private final boolean enableDetailedLogging;
        private final boolean enableMetrics;

        private RunnerConfiguration(Builder builder) {
            this.maxConcurrentTests = builder.maxConcurrentTests;
            this.testTimeout = builder.testTimeout;
            this.enableDetailedLogging = builder.enableDetailedLogging;
            this.enableMetrics = builder.enableMetrics;
        }

        public static RunnerConfiguration defaultConfiguration() {
            return new Builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getMaxConcurrentTests() {
            return maxConcurrentTests;
        }

        public Duration getTestTimeout() {
            return testTimeout;
        }

        public boolean isEnableDetailedLogging() {
            return enableDetailedLogging;
        }

        public boolean isEnableMetrics() {
            return enableMetrics;
        }

        /**
         * Builder for RunnerConfiguration.
         */
        public static class Builder {
            private int maxConcurrentTests = Runtime.getRuntime().availableProcessors();
            private Duration testTimeout = Duration.ofMinutes(2);
            private boolean enableDetailedLogging = false;
            private boolean enableMetrics = true;

            public Builder maxConcurrentTests(int maxConcurrentTests) {
                this.maxConcurrentTests = maxConcurrentTests;
                return this;
            }

            public Builder testTimeout(Duration testTimeout) {
                this.testTimeout = testTimeout;
                return this;
            }

            public Builder enableDetailedLogging(boolean enableDetailedLogging) {
                this.enableDetailedLogging = enableDetailedLogging;
                return this;
            }

            public Builder enableMetrics(boolean enableMetrics) {
                this.enableMetrics = enableMetrics;
                return this;
            }

            public RunnerConfiguration build() {
                return new RunnerConfiguration(this);
            }
        }
    }

    /**
     * Result of a single test method execution.
     */
    public static class TestMethodResult {
        private final String methodName;
        private final Status status;
        private final Duration executionTime;
        private final String errorMessage;

        public TestMethodResult(String methodName, Status status, Duration executionTime, String errorMessage) {
            this.methodName = methodName;
            this.status = status;
            this.executionTime = executionTime;
            this.errorMessage = errorMessage;
        }

        // Getters
        public String getMethodName() {
            return methodName;
        }

        public Status getStatus() {
            return status;
        }

        public Duration getExecutionTime() {
            return executionTime;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isPassed() {
            return status == Status.PASSED;
        }

        public boolean isFailed() {
            return status == Status.FAILED;
        }

        public boolean isSkipped() {
            return status == Status.SKIPPED;
        }

        /**
         * Test method execution status.
         */
        public enum Status {
            PASSED,
            FAILED,
            SKIPPED,
            TIMEOUT
        }
    }
}