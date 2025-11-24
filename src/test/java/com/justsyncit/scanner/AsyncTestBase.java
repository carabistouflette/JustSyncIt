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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Base class for async tests providing common setup, teardown, and utilities.
 * Follows TDD principles by providing consistent test infrastructure.
 */
public abstract class AsyncTestBase {

    /** Temporary directory for test files. */
    @TempDir
    protected Path tempDir;
    
    /** Default timeout for async operations. */
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    /** Short timeout for fast operations. */
    protected static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
    
    /** Long timeout for stress operations. */
    protected static final Duration LONG_TIMEOUT = Duration.ofMinutes(5);
    
    /** Executor service for test operations. */
    protected ExecutorService testExecutor;
    
    /** Reference to track last exception for debugging. */
    protected final AtomicReference<Throwable> lastException = new AtomicReference<>();
    
    /** Flag to track if test executor should be isolated per test method. */
    protected boolean useIsolatedExecutor = true;

    @BeforeEach
    void setUp() {
        if (useIsolatedExecutor) {
            // Create isolated executor for each test method to avoid interference
            testExecutor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors() / 2));
        } else {
            // Use shared executor for tests that need coordination
            testExecutor = Executors.newCachedThreadPool();
        }
        lastException.set(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (testExecutor != null && !testExecutor.isShutdown()) {
            testExecutor.shutdown();
            try {
                if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    testExecutor.shutdownNow();
                    // Wait a bit more for forceful shutdown
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                testExecutor.shutdownNow();
            }
        }
        
        // Log any captured exceptions for debugging
        Throwable exception = lastException.get();
        if (exception != null) {
            System.err.println("Last captured exception: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    /**
     * Executes a supplier within timeout and captures any exceptions.
     *
     * @param supplier the operation to execute
     * @param timeout timeout duration
     * @param <T> result type
     * @return result of the operation
     * @throws RuntimeException if operation fails or times out
     */
    protected <T> T executeWithTimeout(Supplier<T> supplier, Duration timeout) {
        try {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier, testExecutor);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            lastException.set(e);
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException("Operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a runnable within timeout and captures any exceptions.
     *
     * @param runnable the operation to execute
     * @param timeout timeout duration
     * @throws RuntimeException if operation fails or times out
     */
    protected void executeWithTimeout(Runnable runnable, Duration timeout) {
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(runnable, testExecutor);
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            lastException.set(e);
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException("Operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a CompletableFuture and applies timeout with enhanced error handling.
     *
     * @param future the future to execute
     * @param timeout timeout duration
     * @param <T> result type
     * @return result of the future
     * @throws RuntimeException if operation fails or times out
     */
    protected <T> T getFutureResult(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            lastException.set(e);
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException("Future operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a CompletableFuture and expects it to complete successfully.
     *
     * @param future the future to execute
     * @param timeout timeout duration
     * @param <T> result type
     * @return result of the future
     */
    protected <T> T expectSuccessfulFuture(CompletableFuture<T> future, Duration timeout) {
        T result = getFutureResult(future, timeout);
        assertNotNull(result, "Future should complete successfully");
        return result;
    }

    /**
     * Executes a CompletableFuture and expects it to fail with specific exception type.
     *
     * @param future the future to execute
     * @param timeout timeout duration
     * @param expectedExceptionType expected exception type
     * @param <T> result type
     */
    protected <T> void expectFailedFuture(CompletableFuture<T> future, Duration timeout, 
                                           Class<? extends Throwable> expectedExceptionType) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            fail("Future should have completed exceptionally");
        } catch (Exception e) {
            lastException.set(e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            assertTrue(expectedExceptionType.isInstance(cause), 
                String.format("Expected %s but got %s", 
                    expectedExceptionType.getSimpleName(), cause.getClass().getSimpleName()));
        }
    }

    /**
     * Waits for multiple futures to complete with timeout.
     *
     * @param futures the futures to wait for
     * @param timeout timeout duration
     */
    protected void waitForAllFutures(CompletableFuture<?>... futures) {
        waitForAllFutures(DEFAULT_TIMEOUT, futures);
    }

    /**
     * Waits for multiple futures to complete with custom timeout.
     *
     * @param timeout timeout duration
     * @param futures the futures to wait for
     */
    protected void waitForAllFutures(Duration timeout, CompletableFuture<?>... futures) {
        try {
            CompletableFuture.allOf(futures).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            lastException.set(e);
            throw new RuntimeException("Waiting for futures failed: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that a future completes within the specified timeout.
     *
     * @param future the future to check
     * @param timeout timeout duration
     * @param message assertion message
     */
    protected <T> void assertFutureCompletesWithin(CompletableFuture<T> future, Duration timeout, String message) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            lastException.set(e);
            fail(message + ": " + e.getMessage());
        }
    }

    /**
     * Asserts that a future fails within the specified timeout.
     *
     * @param future the future to check
     * @param timeout timeout duration
     * @param message assertion message
     */
    protected <T> void assertFutureFailsWithin(CompletableFuture<T> future, Duration timeout, String message) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            fail(message + ": Future should have failed");
        } catch (Exception e) {
            lastException.set(e);
            // Expected behavior
        }
    }

    /**
     * Measures execution time of an operation.
     *
     * @param operation the operation to measure
     * @param <T> result type
     * @return result and execution time
     */
    protected <T> TimedResult<T> measureTime(Supplier<T> operation) {
        long startTime = System.nanoTime();
        T result = operation.get();
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        return new TimedResult<>(result, durationNanos);
    }

    /**
     * Measures execution time of an async operation.
     *
     * @param operation the async operation to measure
     * @param <T> result type
     * @return result and execution time
     */
    protected <T> TimedResult<T> measureAsyncTime(Supplier<CompletableFuture<T>> operation) {
        long startTime = System.nanoTime();
        CompletableFuture<T> future = operation.get();
        T result = getFutureResult(future, LONG_TIMEOUT);
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        return new TimedResult<>(result, durationNanos);
    }

    /**
     * Result wrapper for timed operations.
     *
     * @param <T> result type
     */
    protected static class TimedResult<T> {
        private final T result;
        private final long durationNanos;

        public TimedResult(T result, long durationNanos) {
            this.result = result;
            this.durationNanos = durationNanos;
        }

        public T getResult() {
            return result;
        }

        public long getDurationNanos() {
            return durationNanos;
        }

        public double getDurationMillis() {
            return durationNanos / 1_000_000.0;
        }

        public double getDurationSeconds() {
            return durationNanos / 1_000_000_000.0;
        }
    }
}