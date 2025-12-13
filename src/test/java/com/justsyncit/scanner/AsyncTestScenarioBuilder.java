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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builder for creating complex async test scenarios.
 * Provides fluent API for building comprehensive test scenarios with concurrent
 * operations,
 * timing constraints, and result validation.
 */
public final class AsyncTestScenarioBuilder {

    private final List<ConcurrentOperation> concurrentOperations = new ArrayList<>();
    private final List<SequentialStep> sequentialSteps = new ArrayList<>();
    private final List<TimingConstraint> timingConstraints = new ArrayList<>();
    private final List<ValidationRule> validationRules = new ArrayList<>();
    private Duration globalTimeout = Duration.ofMinutes(5);
    private boolean enableMetrics = true;
    private boolean enableDetailedLogging = false;
    private int maxConcurrentThreads = Runtime.getRuntime().availableProcessors();

    private AsyncTestScenarioBuilder() {
        // Private constructor for builder pattern
    }

    /**
     * Creates a new scenario builder.
     */
    public static AsyncTestScenarioBuilder create() {
        return new AsyncTestScenarioBuilder();
    }

    /**
     * Adds a concurrent operation to the scenario.
     */
    public AsyncTestScenarioBuilder withConcurrentOperation(String name, Runnable operation) {
        concurrentOperations.add(new ConcurrentOperation(name, operation));
        return this;
    }

    /**
     * Adds multiple concurrent operations.
     */
    public AsyncTestScenarioBuilder withConcurrentOperations(ConcurrentOperation... operations) {
        concurrentOperations.addAll(Arrays.asList(operations));
        return this;
    }

    /**
     * Adds a sequential step to the scenario.
     */
    public AsyncTestScenarioBuilder withSequentialStep(String name, Runnable step) {
        sequentialSteps.add(new SequentialStep(name, step));
        return this;
    }

    /**
     * Adds multiple sequential steps.
     */
    public AsyncTestScenarioBuilder withSequentialSteps(SequentialStep... steps) {
        sequentialSteps.addAll(Arrays.asList(steps));
        return this;
    }

    /**
     * Adds a timing constraint to the scenario.
     */
    public AsyncTestScenarioBuilder withTimingConstraint(String name, Duration maxDuration) {
        timingConstraints.add(new TimingConstraint(name, maxDuration));
        return this;
    }

    /**
     * Adds a validation rule to the scenario.
     */
    public AsyncTestScenarioBuilder withValidationRule(String name, ValidationFunction validator) {
        validationRules.add(new ValidationRule(name, validator));
        return this;
    }

    /**
     * Sets the global timeout for the scenario.
     */
    public AsyncTestScenarioBuilder withGlobalTimeout(Duration timeout) {
        this.globalTimeout = timeout;
        return this;
    }

    /**
     * Enables or disables metrics collection.
     */
    public AsyncTestScenarioBuilder withMetrics(boolean enable) {
        this.enableMetrics = enable;
        return this;
    }

    /**
     * Enables or disables detailed logging.
     */
    public AsyncTestScenarioBuilder withDetailedLogging(boolean enable) {
        this.enableDetailedLogging = enable;
        return this;
    }

    /**
     * Sets the maximum number of concurrent threads.
     */
    public AsyncTestScenarioBuilder withMaxConcurrentThreads(int maxThreads) {
        this.maxConcurrentThreads = maxThreads;
        return this;
    }

    /**
     * Builds the async test scenario.
     */
    public AsyncTestScenario build() {
        return new AsyncTestScenario(
                concurrentOperations,
                sequentialSteps,
                timingConstraints,
                validationRules,
                globalTimeout,
                enableMetrics,
                enableDetailedLogging,
                maxConcurrentThreads);
    }

    /**
     * Represents a concurrent operation in the scenario.
     */
    public static class ConcurrentOperation {
        private final String name;
        private final Runnable operation;
        private volatile boolean completed = false;
        private volatile Exception error = null;
        private final AtomicLong executionTimeMs = new AtomicLong(0);

        public ConcurrentOperation(String name, Runnable operation) {
            this.name = name;
            this.operation = operation;
        }

        public String getName() {
            return name;
        }

        public Runnable getOperation() {
            return operation;
        }

        public boolean isCompleted() {
            return completed;
        }

        public Exception getError() {
            return error;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs.get();
        }

        void markCompleted() {
            this.completed = true;
        }

        void setError(Exception error) {
            this.error = error;
        }

        void setExecutionTime(long timeMs) {
            this.executionTimeMs.set(timeMs);
        }
    }

    /**
     * Represents a sequential step in the scenario.
     */
    public static class SequentialStep {
        private final String name;
        private final Runnable step;
        private volatile boolean completed = false;
        private volatile Exception error = null;
        private final AtomicLong executionTimeMs = new AtomicLong(0);

        public SequentialStep(String name, Runnable step) {
            this.name = name;
            this.step = step;
        }

        public String getName() {
            return name;
        }

        public Runnable getStep() {
            return step;
        }

        public boolean isCompleted() {
            return completed;
        }

        public Exception getError() {
            return error;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs.get();
        }

        void markCompleted() {
            this.completed = true;
        }

        void setError(Exception error) {
            this.error = error;
        }

        void setExecutionTime(long timeMs) {
            this.executionTimeMs.set(timeMs);
        }
    }

    /**
     * Represents a timing constraint for the scenario.
     */
    public static class TimingConstraint {
        private final String name;
        private final Duration maxDuration;
        private volatile boolean satisfied = false;
        private final AtomicLong actualDurationMs = new AtomicLong(0);

        public TimingConstraint(String name, Duration maxDuration) {
            this.name = name;
            this.maxDuration = maxDuration;
        }

        public String getName() {
            return name;
        }

        public Duration getMaxDuration() {
            return maxDuration;
        }

        public boolean isSatisfied() {
            return satisfied;
        }

        public long getActualDurationMs() {
            return actualDurationMs.get();
        }

        void setSatisfied(boolean satisfied) {
            this.satisfied = satisfied;
        }

        void setActualDuration(long durationMs) {
            this.actualDurationMs.set(durationMs);
        }
    }

    /**
     * Represents a validation rule for the scenario.
     */
    public static class ValidationRule {
        private final String name;
        private final ValidationFunction validator;
        private volatile boolean satisfied = false;
        private volatile String errorMessage = null;

        public ValidationRule(String name, ValidationFunction validator) {
            this.name = name;
            this.validator = validator;
        }

        public String getName() {
            return name;
        }

        public ValidationFunction getValidator() {
            return validator;
        }

        public boolean isSatisfied() {
            return satisfied;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        void setSatisfied(boolean satisfied) {
            this.satisfied = satisfied;
        }

        void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Functional interface for validation functions.
     */
    @FunctionalInterface
    public interface ValidationFunction {
        ValidationResult validate(ScenarioResult result);
    }

    /**
     * Result of a validation operation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * Represents the complete async test scenario.
     */
    public static class AsyncTestScenario {
        private final List<ConcurrentOperation> concurrentOperations;
        private final List<SequentialStep> sequentialSteps;
        private final List<TimingConstraint> timingConstraints;
        private final List<ValidationRule> validationRules;
        private final Duration globalTimeout;
        private final boolean enableMetrics;
        private final boolean enableDetailedLogging;
        private final int maxConcurrentThreads;

        public AsyncTestScenario(List<ConcurrentOperation> concurrentOperations,
                List<SequentialStep> sequentialSteps,
                List<TimingConstraint> timingConstraints,
                List<ValidationRule> validationRules,
                Duration globalTimeout,
                boolean enableMetrics,
                boolean enableDetailedLogging,
                int maxConcurrentThreads) {
            this.concurrentOperations = new ArrayList<>(concurrentOperations);
            this.sequentialSteps = new ArrayList<>(sequentialSteps);
            this.timingConstraints = new ArrayList<>(timingConstraints);
            this.validationRules = new ArrayList<>(validationRules);
            this.globalTimeout = globalTimeout;
            this.enableMetrics = enableMetrics;
            this.enableDetailedLogging = enableDetailedLogging;
            this.maxConcurrentThreads = maxConcurrentThreads;
        }

        /**
         * Executes the scenario asynchronously.
         */
        public CompletableFuture<ScenarioResult> executeAsync() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return execute();
                } catch (Exception e) {
                    throw new RuntimeException("Scenario execution failed", e);
                }
            });
        }

        /**
         * Executes the scenario synchronously.
         */
        public ScenarioResult execute() {
            Instant startTime = Instant.now();
            ScenarioResult result = new ScenarioResult(startTime);

            try {
                if (enableDetailedLogging) {
                    System.out.println("Starting scenario execution with "
                            + concurrentOperations.size() + " concurrent operations and "
                            + sequentialSteps.size() + " sequential steps");
                }

                // Execute concurrent operations
                if (!concurrentOperations.isEmpty()) {
                    executeConcurrentOperations(result);
                }

                // Execute sequential steps
                if (!sequentialSteps.isEmpty()) {
                    executeSequentialSteps(result);
                }

                // Apply timing constraints
                if (!timingConstraints.isEmpty()) {
                    applyTimingConstraints(result);
                }

                // Apply validation rules
                if (!validationRules.isEmpty()) {
                    applyValidationRules(result);
                }

                result.setSuccess(true);

            } catch (Exception e) {
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
                if (enableDetailedLogging) {
                    e.printStackTrace();
                }
            } finally {
                result.setEndTime(Instant.now());
                result.setDuration(Duration.between(startTime, result.getEndTime()));
            }

            return result;
        }

        private void executeConcurrentOperations(ScenarioResult result) {
            if (concurrentOperations.isEmpty()) {
                return;
            }

            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(maxConcurrentThreads, concurrentOperations.size()));

            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (ConcurrentOperation operation : concurrentOperations) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        long startTime = System.currentTimeMillis();
                        try {
                            if (enableDetailedLogging) {
                                System.out.println("Executing concurrent operation: " + operation.getName());
                            }
                            operation.getOperation().run();
                            operation.markCompleted();
                            if (enableDetailedLogging) {
                                System.out.println("Completed concurrent operation: " + operation.getName());
                            }
                        } catch (Exception e) {
                            operation.setError(e);
                            if (enableDetailedLogging) {
                                System.err.println("Failed concurrent operation: " + operation.getName()
                                        + " - " + e.getMessage());
                            }
                        } finally {
                            operation.setExecutionTime(System.currentTimeMillis() - startTime);
                        }
                    }, executor);
                    futures.add(future);
                }

                // Wait for all concurrent operations to complete
                AsyncTestUtils.waitForAll(globalTimeout, futures);

            } catch (Exception e) {
                throw new RuntimeException("Concurrent operations execution failed", e);
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            result.setConcurrentOperations(new ArrayList<>(concurrentOperations));
        }

        private void executeSequentialSteps(ScenarioResult result) {
            for (SequentialStep step : sequentialSteps) {
                long startTime = System.currentTimeMillis();
                try {
                    if (enableDetailedLogging) {
                        System.out.println("Executing sequential step: " + step.getName());
                    }
                    step.getStep().run();
                    step.markCompleted();
                    if (enableDetailedLogging) {
                        System.out.println("Completed sequential step: " + step.getName());
                    }
                } catch (Exception e) {
                    step.setError(e);
                    if (enableDetailedLogging) {
                        System.err.println("Failed sequential step: " + step.getName()
                                + " - " + e.getMessage());
                    }
                    throw new RuntimeException("Sequential step failed: " + step.getName(), e);
                } finally {
                    step.setExecutionTime(System.currentTimeMillis() - startTime);
                }
            }

            result.setSequentialSteps(new ArrayList<>(sequentialSteps));
        }

        private void applyTimingConstraints(ScenarioResult result) {
            for (TimingConstraint constraint : timingConstraints) {
                // For simplicity, we'll use the total execution time as the constraint duration
                // In a real implementation, you might want to measure specific operations
                long actualDuration = result.getDuration().toMillis();
                constraint.setActualDuration(actualDuration);
                constraint.setSatisfied(actualDuration <= constraint.getMaxDuration().toMillis());

                if (enableDetailedLogging) {
                    System.out.println("Timing constraint '" + constraint.getName() + "': "
                            + (constraint.isSatisfied() ? "SATISFIED" : "VIOLATED")
                            + " (actual: " + actualDuration + "ms, max: "
                            + constraint.getMaxDuration().toMillis() + "ms)");
                }
            }

            result.setTimingConstraints(new ArrayList<>(timingConstraints));
        }

        private void applyValidationRules(ScenarioResult result) {
            for (ValidationRule rule : validationRules) {
                try {
                    ValidationResult validation = rule.getValidator().validate(result);
                    rule.setSatisfied(validation.isValid());
                    rule.setErrorMessage(validation.getMessage());

                    if (enableDetailedLogging) {
                        System.out.println("Validation rule '" + rule.getName() + "': " +
                                (rule.isSatisfied() ? "SATISFIED" : "VIOLATED") +
                                (validation.getMessage() != null ? " - " + validation.getMessage() : ""));
                    }
                } catch (Exception e) {
                    rule.setSatisfied(false);
                    rule.setErrorMessage("Validation failed: " + e.getMessage());
                    if (enableDetailedLogging) {
                        System.err.println("Validation rule '" + rule.getName() + "' failed: " + e.getMessage());
                    }
                }
            }

            result.setValidationRules(new ArrayList<>(validationRules));
        }

        // Getters
        public List<ConcurrentOperation> getConcurrentOperations() {
            return concurrentOperations;
        }

        public List<SequentialStep> getSequentialSteps() {
            return sequentialSteps;
        }

        public List<TimingConstraint> getTimingConstraints() {
            return timingConstraints;
        }

        public List<ValidationRule> getValidationRules() {
            return validationRules;
        }

        public Duration getGlobalTimeout() {
            return globalTimeout;
        }

        public boolean isEnableMetrics() {
            return enableMetrics;
        }

        public boolean isEnableDetailedLogging() {
            return enableDetailedLogging;
        }

        public int getMaxConcurrentThreads() {
            return maxConcurrentThreads;
        }
    }

    /**
     * Result of scenario execution.
     */
    public static class ScenarioResult {
        private final Instant startTime;
        private Instant endTime;
        private Duration duration;
        private boolean success = false;
        private String errorMessage;
        private List<ConcurrentOperation> concurrentOperations;
        private List<SequentialStep> sequentialSteps;
        private List<TimingConstraint> timingConstraints;
        private List<ValidationRule> validationRules;
        private final ConcurrentHashMap<String, Object> metrics = new ConcurrentHashMap<>();

        public ScenarioResult(Instant startTime) {
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

        public List<ConcurrentOperation> getConcurrentOperations() {
            return concurrentOperations;
        }

        public List<SequentialStep> getSequentialSteps() {
            return sequentialSteps;
        }

        public List<TimingConstraint> getTimingConstraints() {
            return timingConstraints;
        }

        public List<ValidationRule> getValidationRules() {
            return validationRules;
        }

        public ConcurrentHashMap<String, Object> getMetrics() {
            return metrics;
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

        void setConcurrentOperations(List<ConcurrentOperation> concurrentOperations) {
            this.concurrentOperations = concurrentOperations;
        }

        void setSequentialSteps(List<SequentialStep> sequentialSteps) {
            this.sequentialSteps = sequentialSteps;
        }

        void setTimingConstraints(List<TimingConstraint> timingConstraints) {
            this.timingConstraints = timingConstraints;
        }

        void setValidationRules(List<ValidationRule> validationRules) {
            this.validationRules = validationRules;
        }

        /**
         * Gets the total number of operations executed.
         */
        public int getTotalOperationCount() {
            int count = 0;
            if (concurrentOperations != null) {
                count += concurrentOperations.size();
            }
            if (sequentialSteps != null) {
                count += sequentialSteps.size();
            }
            return count;
        }

        /**
         * Gets the number of successful operations.
         */
        public int getSuccessfulOperationCount() {
            int count = 0;
            if (concurrentOperations != null) {
                count += (int) concurrentOperations.stream()
                        .filter(op -> op.isCompleted() && op.getError() == null)
                        .count();
            }
            if (sequentialSteps != null) {
                count += (int) sequentialSteps.stream()
                        .filter(step -> step.isCompleted() && step.getError() == null)
                        .count();
            }
            return count;
        }

        /**
         * Gets the number of failed operations.
         */
        public int getFailedOperationCount() {
            return getTotalOperationCount() - getSuccessfulOperationCount();
        }

        /**
         * Checks if all timing constraints were satisfied.
         */
        public boolean allTimingConstraintsSatisfied() {
            if (timingConstraints == null || timingConstraints.isEmpty()) {
                return true;
            }
            return timingConstraints.stream().allMatch(TimingConstraint::isSatisfied);
        }

        /**
         * Checks if all validation rules were satisfied.
         */
        public boolean allValidationRulesSatisfied() {
            if (validationRules == null || validationRules.isEmpty()) {
                return true;
            }
            return validationRules.stream().allMatch(ValidationRule::isSatisfied);
        }

        /**
         * Adds a custom metric to the result.
         */
        public void addMetric(String key, Object value) {
            metrics.put(key, value);
        }

        /**
         * Gets a custom metric from the result.
         */
        @SuppressWarnings("unchecked")
        public <T> T getMetric(String key, Class<T> type) {
            Object value = metrics.get(key);
            return value != null && type.isInstance(value) ? (T) value : null;
        }
    }
}