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

import java.nio.ByteBuffer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enhanced utility class for async testing scenarios with comprehensive timeout
 * handling.
 * Provides advanced testing patterns for async operations, error scenarios,
 * and performance validation following TDD principles.
 */
public final class AsyncTestUtils {

    private AsyncTestUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Default timeout for async operations (30 seconds).
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Short timeout for fast operations (5 seconds).
     */
    public static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Long timeout for stress operations (5 minutes).
     */
    public static final Duration LONG_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Ultra-short timeout for immediate operations (1 second).
     */
    public static final Duration ULTRA_SHORT_TIMEOUT = Duration.ofSeconds(1);

    /**
     * Executes a CompletableFuture and returns the result with enhanced error
     * handling.
     *
     * @param future  the future to execute
     * @param timeout timeout duration
     * @param <T>     result type
     * @return the result of the future
     * @throws AsyncTestException if the future fails or times out
     */
    public static <T> T getResultOrThrow(CompletableFuture<T> future, Duration timeout) throws AsyncTestException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AsyncTestException("Interrupted while waiting for result", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // Fix: Check for specific exception types correctly
            if (cause instanceof AsyncTestException) {
                throw (AsyncTestException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new AsyncTestException("Execution failed", cause);
        } catch (TimeoutException e) {
            throw new AsyncTestException("Operation timed out after " + timeout, e);
        }
    }

    /**
     * Executes a CompletableFuture with default timeout.
     *
     * @param future the future to execute
     * @param <T>    result type
     * @return the result of the future
     * @throws AsyncTestException if the future fails or times out
     */
    public static <T> T getResultOrThrow(CompletableFuture<T> future) throws AsyncTestException {
        return getResultOrThrow(future, DEFAULT_TIMEOUT);
    }

    /**
     * Executes a CompletableFuture and consumes the result with the given consumer.
     *
     * @param future   the future to execute
     * @param consumer the consumer to handle the result
     * @param timeout  timeout duration
     * @param <T>      result type
     * @throws AsyncTestException if the future fails or times out
     */
    public static <T> void consumeResult(CompletableFuture<T> future, Consumer<T> consumer, Duration timeout)
            throws AsyncTestException {
        T result = getResultOrThrow(future, timeout);
        consumer.accept(result);
    }

    /**
     * Executes a CompletableFuture and consumes the result with default timeout.
     *
     * @param future   the future to execute
     * @param consumer the consumer to handle the result
     * @param <T>      result type
     * @throws AsyncTestException if the future fails or times out
     */
    public static <T> void consumeResult(CompletableFuture<T> future, Consumer<T> consumer) throws AsyncTestException {
        consumeResult(future, consumer, DEFAULT_TIMEOUT);
    }

    /**
     * Asserts that a CompletableFuture completes successfully within the specified
     * timeout.
     *
     * @param future  the future to check
     * @param timeout timeout duration
     * @param <T>     result type
     * @throws AsyncTestAssertionError if the assertion fails
     */
    public static <T> void assertCompletesSuccessfully(CompletableFuture<T> future, Duration timeout)
            throws AsyncTestAssertionError {
        try {
            // Unused result
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            // If we get here, the future completed successfully
        } catch (Exception e) {
            throw new AsyncTestAssertionError(
                    "Future should have completed successfully, but failed: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that a CompletableFuture completes successfully with default timeout.
     *
     * @param future the future to check
     * @param <T>    result type
     * @throws AsyncTestAssertionError if the assertion fails
     */
    public static <T> void assertCompletesSuccessfully(CompletableFuture<T> future) throws AsyncTestAssertionError {
        assertCompletesSuccessfully(future, DEFAULT_TIMEOUT);
    }

    /**
     * Asserts that a CompletableFuture fails with the expected exception type.
     *
     * @param future                the future to check
     * @param expectedExceptionType the expected exception type
     * @param timeout               timeout duration
     * @param <T>                   result type
     * @throws AsyncTestAssertionError if the assertion fails
     */
    public static <T> void assertFailsWithException(CompletableFuture<T> future,
            Class<? extends Throwable> expectedExceptionType,
            Duration timeout) throws AsyncTestAssertionError {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            throw new AsyncTestAssertionError("Future completed successfully but was expected to fail");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (!expectedExceptionType.isInstance(cause)) {
                throw new AsyncTestAssertionError(
                        String.format("Future failed with %s but expected %s",
                                cause.getClass().getSimpleName(), expectedExceptionType.getSimpleName()),
                        e);
            }
            // Expected exception type - test passes
        }
    }

    /**
     * Asserts that a CompletableFuture fails with the expected exception type using
     * default timeout.
     *
     * @param future                the future to check
     * @param expectedExceptionType the expected exception type
     * @param <T>                   result type
     * @throws AsyncTestAssertionError if the assertion fails
     */
    public static <T> void assertFailsWithException(CompletableFuture<T> future,
            Class<? extends Throwable> expectedExceptionType) throws AsyncTestAssertionError {
        assertFailsWithException(future, expectedExceptionType, DEFAULT_TIMEOUT);
    }

    /**
     * Waits for multiple CompletableFuture instances to complete.
     *
     * @param timeout timeout duration
     * @param futures the futures to wait for
     * @throws AsyncTestException if any future fails or times out
     */
    public static void waitForAll(Duration timeout, CompletableFuture<?>... futures) throws AsyncTestException {
        try {
            CompletableFuture.allOf(futures).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new AsyncTestException("Waiting for futures failed: " + e.getMessage(), e);
        }
    }

    /**
     * Waits for multiple CompletableFuture instances to complete with default
     * timeout.
     *
     * @param futures the futures to wait for
     * @throws AsyncTestException if any future fails or times out
     */
    public static void waitForAll(CompletableFuture<?>... futures) throws AsyncTestException {
        waitForAll(DEFAULT_TIMEOUT, futures);
    }

    /**
     * Waits for a collection of CompletableFuture instances to complete.
     *
     * @param timeout timeout duration
     * @param futures the futures to wait for
     * @throws AsyncTestException if any future fails or times out
     */
    public static void waitForAll(Duration timeout, java.util.Collection<? extends CompletableFuture<?>> futures)
            throws AsyncTestException {
        @SuppressWarnings("rawtypes")
        CompletableFuture[] array = futures.toArray(new CompletableFuture[0]);
        CompletableFuture<?>[] typedArray = (CompletableFuture<?>[]) array;
        waitForAll(timeout, typedArray);
    }

    /**
     * Waits for a collection of CompletableFuture instances to complete with
     * default timeout.
     *
     * @param futures the futures to wait for
     * @throws AsyncTestException if any future fails or times out
     */
    public static void waitForAll(java.util.Collection<? extends CompletableFuture<?>> futures)
            throws AsyncTestException {
        waitForAll(DEFAULT_TIMEOUT, futures);
    }

    /**
     * Waits for multiple CompletableFuture instances to complete and returns
     * results.
     *
     * @param timeout timeout duration
     * @param futures the futures to wait for
     * @param <T>     result type
     * @return list of results
     * @throws AsyncTestException if any future fails or times out
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> List<T> waitForAllAndGetResults(Duration timeout, CompletableFuture<T>... futures)
            throws AsyncTestException {
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
            allFutures.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

            // Collect results into a list
            return Arrays.stream(futures)
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to get future result", e);
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            throw new AsyncTestException("Waiting for futures failed: " + e.getMessage(), e);
        }
    }

    /**
     * Waits for multiple CompletableFuture instances to complete and returns
     * results with default timeout.
     *
     * @param futures the futures to wait for
     * @param <T>     result type
     * @return list of results
     * @throws AsyncTestException if any future fails or times out
     */
    @SafeVarargs
    public static <T> List<T> waitForAllAndGetResults(CompletableFuture<T>... futures) throws AsyncTestException {
        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futuresArray = futures;
        return waitForAllAndGetResults(DEFAULT_TIMEOUT, futuresArray);
    }

    /**
     * Waits for a collection of CompletableFuture instances to complete and returns
     * results.
     *
     * @param timeout timeout duration
     * @param futures the futures to wait for
     * @param <T>     result type
     * @return list of results
     * @throws AsyncTestException if any future fails or times out
     */
    public static <T> List<T> waitForAllAndGetResults(Duration timeout,
            java.util.Collection<? extends CompletableFuture<T>> futures)
            throws AsyncTestException {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        CompletableFuture<T>[] array = futures.toArray(new CompletableFuture[0]);
        return waitForAllAndGetResults(timeout, array);
    }

    /**
     * Waits for a collection of CompletableFuture instances to complete and returns
     * results with default timeout.
     *
     * @param futures the futures to wait for
     * @param <T>     result type
     * @return list of results
     * @throws AsyncTestException if any future fails or times out
     */
    public static <T> List<T> waitForAllAndGetResults(java.util.Collection<? extends CompletableFuture<T>> futures)
            throws AsyncTestException {
        return waitForAllAndGetResults(DEFAULT_TIMEOUT, futures);
    }

    /**
     * Creates a CompletableFuture that completes after the specified delay.
     *
     * @param result the result to complete with
     * @param delay  the delay before completion
     * @param <T>    result type
     * @return a delayed CompletableFuture
     */
    public static <T> CompletableFuture<T> delayedFuture(T result, Duration delay) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> future.complete(result), delay.toMillis(), TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Creates a CompletableFuture that fails with the specified exception after a
     * delay.
     *
     * @param exception the exception to fail with
     * @param delay     the delay before failure
     * @param <T>       result type
     * @return a failing CompletableFuture
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable exception, Duration delay) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> future.completeExceptionally(exception), delay.toMillis(), TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Creates a CompletableFuture that fails immediately with the specified
     * exception.
     *
     * @param exception the exception to fail with
     * @param <T>       result type
     * @return a failing CompletableFuture
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable exception) {
        return CompletableFuture.failedFuture(exception);
    }

    /**
     * Creates a CompletableFuture that completes immediately with the specified
     * result.
     *
     * @param result the result to complete with
     * @param <T>    result type
     * @return a completed CompletableFuture
     */
    public static <T> CompletableFuture<T> completedFuture(T result) {
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Measures the execution time of an async operation.
     *
     * @param operation the operation to measure
     * @param <T>       result type
     * @return a TimedResult containing the result and execution time
     */
    public static <T> TimedResult<T> measureAsyncTime(Supplier<CompletableFuture<T>> operation)
            throws AsyncTestException {
        long startTime = System.nanoTime();
        CompletableFuture<T> future = operation.get();
        T result = getResultOrThrow(future, LONG_TIMEOUT);
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        return new TimedResult<>(result, durationNanos);
    }

    /**
     * Measures the execution time of a sync operation.
     *
     * @param operation the operation to measure
     * @param <T>       result type
     * @return a TimedResult containing the result and execution time
     */
    public static <T> TimedResult<T> measureSyncTime(Supplier<T> operation) {
        long startTime = System.nanoTime();
        T result = operation.get();
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        return new TimedResult<>(result, durationNanos);
    }

    /**
     * Executes an operation multiple times and collects performance metrics.
     *
     * @param operation        the operation to execute
     * @param iterations       number of iterations
     * @param warmupIterations number of warmup iterations
     * @param <T>              result type
     * @return PerformanceMetrics for the operation
     */
    public static <T> PerformanceMetrics measurePerformance(Supplier<T> operation, int iterations,
            int warmupIterations) {
        // Warmup
        for (int i = 0; i < warmupIterations; i++) {
            try {
                operation.get();
            } catch (Exception e) {
                // Ignore warmup exceptions
            }
        }

        // Measurement
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            try {
                operation.get();
                long endTime = System.nanoTime();
                durations.add(endTime - startTime);
            } catch (Exception e) {
                // Record failed iteration as max duration
                durations.add(Long.MAX_VALUE);
            }
        }

        return new PerformanceMetrics(durations);
    }

    /**
     * Creates a test file with the specified size and content pattern.
     *
     * @param directory the directory to create the file in
     * @param fileName  the name of the file
     * @param size      the size of the file in bytes
     * @param pattern   the content pattern to use
     * @return the path to the created file
     * @throws AsyncTestException if file creation fails
     */
    public static Path createTestFile(Path directory, String fileName, int size, byte pattern)
            throws AsyncTestException {
        try {
            Path file = directory.resolve(fileName);
            byte[] data = new byte[size];
            for (int i = 0; i < size; i++) {
                data[i] = (byte) (pattern + (i % 256));
            }
            java.nio.file.Files.write(file, data);
            return file;
        } catch (Exception e) {
            throw new AsyncTestException("Failed to create test file: " + fileName, e);
        }
    }

    /**
     * Creates a test file with the specified size and default pattern.
     *
     * @param directory the directory to create the file in
     * @param fileName  the name of the file
     * @param size      the size of the file in bytes
     * @return the path to the created file
     * @throws AsyncTestException if file creation fails
     */
    public static Path createTestFile(Path directory, String fileName, int size) throws AsyncTestException {
        return createTestFile(directory, fileName, size, (byte) 0x42);
    }

    /**
     * Creates a test file with text content.
     *
     * @param directory the directory to create the file in
     * @param fileName  the name of the file
     * @param content   the text content to write
     * @return the path to the created file
     * @throws AsyncTestException if file creation fails
     */
    public static Path createTestFile(Path directory, String fileName, String content) throws AsyncTestException {
        try {
            Path file = directory.resolve(fileName);
            java.nio.file.Files.write(file, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return file;
        } catch (Exception e) {
            throw new AsyncTestException("Failed to create test file: " + fileName, e);
        }
    }

    /**
     * Creates multiple test files with varying sizes.
     *
     * @param directory the directory to create files in
     * @param baseName  the base name for files
     * @param sizes     array of file sizes in bytes
     * @return list of created file paths
     * @throws AsyncTestException if file creation fails
     */
    public static List<Path> createTestFiles(Path directory, String baseName, int[] sizes) throws AsyncTestException {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            String fileName = baseName + "_" + i + ".dat";
            Path file = createTestFile(directory, fileName, sizes[i]);
            files.add(file);
        }
        return files;
    }

    /**
     * Creates a backpressure controller for testing.
     *
     * @param maxPermits the maximum number of permits
     * @return a TestBackpressureController
     */
    public static TestBackpressureController createBackpressureController(int maxPermits) {
        return new TestBackpressureController(maxPermits);
    }

    /**
     * Creates a mock async buffer pool for testing.
     *
     * @return a MockAsyncByteBufferPool
     */
    public static MockAsyncByteBufferPool createMockBufferPool() {
        return new MockAsyncByteBufferPool();
    }

    /**
     * Creates a mock async file chunker for testing.
     *
     * @return a MockAsyncFileChunker
     */
    public static MockAsyncFileChunker createMockFileChunker() {
        return new MockAsyncFileChunker();
    }

    /**
     * Creates a mock async chunk handler for testing.
     *
     * @return a MockAsyncChunkHandler
     */
    public static MockAsyncChunkHandler createMockChunkHandler() {
        return new MockAsyncChunkHandler();
    }

    /**
     * Custom exception for async testing.
     */
    public static class AsyncTestException extends Exception {
        private static final long serialVersionUID = 1L;

        public AsyncTestException(String message) {
            super(message);
        }

        public AsyncTestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom assertion error for async testing.
     */
    public static class AsyncTestAssertionError extends AssertionError {
        private static final long serialVersionUID = 1L;

        public AsyncTestAssertionError(String message) {
            super(message);
        }

        public AsyncTestAssertionError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Result wrapper for timed operations.
     *
     * @param <T> result type
     */
    public static class TimedResult<T> {
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

    /**
     * Performance metrics for operations.
     */
    public static class PerformanceMetrics {
        private final List<Long> durations;
        private final long totalDurationNanos;
        private final double averageDurationNanos;
        private final long minDurationNanos;
        private final long maxDurationNanos;
        private final double throughputOpsPerSecond;

        public PerformanceMetrics(List<Long> durations) {
            this.durations = new ArrayList<>(durations);
            this.totalDurationNanos = durations.stream().mapToLong(Long::longValue).sum();
            this.averageDurationNanos = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            this.minDurationNanos = durations.stream().mapToLong(Long::longValue).min().orElse(0);
            this.maxDurationNanos = durations.stream().mapToLong(Long::longValue).max().orElse(0);
            this.throughputOpsPerSecond = durations.size() / (totalDurationNanos / 1_000_000_000.0);
        }

        public List<Long> getDurations() {
            return durations;
        }

        public long getTotalDurationNanos() {
            return totalDurationNanos;
        }

        public double getAverageDurationNanos() {
            return averageDurationNanos;
        }

        public long getMinDurationNanos() {
            return minDurationNanos;
        }

        public long getMaxDurationNanos() {
            return maxDurationNanos;
        }

        public double getThroughputOpsPerSecond() {
            return throughputOpsPerSecond;
        }

        public double getAverageDurationMillis() {
            return averageDurationNanos / 1_000_000.0;
        }

        public double getMinDurationMillis() {
            return minDurationNanos / 1_000_000.0;
        }

        public double getMaxDurationMillis() {
            return maxDurationNanos / 1_000_000.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "PerformanceMetrics{count=%d, avg=%.2fμs, min=%.2fμs, max=%.2fμs, throughput=%.0f ops/s}",
                    durations.size(),
                    getAverageDurationMillis() * 1000,
                    getMinDurationMillis() * 1000,
                    getMaxDurationMillis() * 1000,
                    throughputOpsPerSecond);
        }
    }

    /**
     * Test backpressure controller implementation.
     */
    public static class TestBackpressureController {
        private final java.util.concurrent.Semaphore semaphore;
        private final AtomicInteger acquiredCount;
        private final AtomicInteger releasedCount;

        public TestBackpressureController(int maxPermits) {
            this.semaphore = new java.util.concurrent.Semaphore(maxPermits);
            this.acquiredCount = new AtomicInteger(0);
            this.releasedCount = new AtomicInteger(0);
        }

        public void acquire() throws InterruptedException {
            semaphore.acquire();
            acquiredCount.incrementAndGet();
        }

        public boolean tryAcquire() {
            boolean acquired = semaphore.tryAcquire();
            if (acquired) {
                acquiredCount.incrementAndGet();
            }
            return acquired;
        }

        public boolean tryAcquire(Duration timeout) throws InterruptedException {
            boolean acquired = semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                acquiredCount.incrementAndGet();
            }
            return acquired;
        }

        public void release() {
            semaphore.release();
            releasedCount.incrementAndGet();
        }

        public int getAvailablePermits() {
            return semaphore.availablePermits();
        }

        public int getAcquiredCount() {
            return acquiredCount.get();
        }

        public int getReleasedCount() {
            return releasedCount.get();
        }

        public double getUtilizationRatio() {
            int totalAcquired = acquiredCount.get();
            int totalReleased = releasedCount.get();
            return totalAcquired > 0 ? (double) (totalAcquired - totalReleased) / totalAcquired : 0.0;
        }
    }

    /**
     * Mock async buffer pool for testing.
     */
    public static class MockAsyncByteBufferPool implements AsyncByteBufferPool {
        private final AtomicInteger acquireCount;
        private final AtomicInteger releaseCount;
        private final AtomicInteger totalBuffers;
        private final java.util.concurrent.ConcurrentLinkedQueue<ByteBuffer> availableBuffers;

        public MockAsyncByteBufferPool() {
            this.acquireCount = new AtomicInteger(0);
            this.releaseCount = new AtomicInteger(0);
            this.totalBuffers = new AtomicInteger(0);
            this.availableBuffers = new java.util.concurrent.ConcurrentLinkedQueue<>();
        }

        @Override
        public CompletableFuture<ByteBuffer> acquireAsync(int size) {
            acquireCount.incrementAndGet();
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(size, 1024));
            availableBuffers.offer(buffer);
            totalBuffers.incrementAndGet();
            return CompletableFuture.completedFuture(buffer);
        }

        @Override
        public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
            releaseCount.incrementAndGet();
            availableBuffers.remove(buffer);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> clearAsync() {
            availableBuffers.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Integer> getAvailableCountAsync() {
            return CompletableFuture.completedFuture(availableBuffers.size());
        }

        @Override
        public CompletableFuture<Integer> getTotalCountAsync() {
            return CompletableFuture.completedFuture(totalBuffers.get());
        }

        @Override
        public CompletableFuture<Integer> getBuffersInUseAsync() {
            return CompletableFuture.completedFuture(totalBuffers.get() - availableBuffers.size());
        }

        @Override
        public CompletableFuture<String> getStatsAsync() {
            return CompletableFuture.completedFuture(String.format(
                    "MockAsyncByteBufferPool{acquired=%d, released=%d, total=%d, available=%d}",
                    acquireCount.get(), releaseCount.get(), totalBuffers.get(), availableBuffers.size()));
        }

        @Override
        public ByteBuffer acquire(int size) {
            acquireCount.incrementAndGet();
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(size, 1024));
            availableBuffers.offer(buffer);
            totalBuffers.incrementAndGet();
            return buffer;
        }

        @Override
        public void release(ByteBuffer buffer) {
            releaseCount.incrementAndGet();
            availableBuffers.remove(buffer);
        }

        @Override
        public void clear() {
            availableBuffers.clear();
        }

        @Override
        public int getAvailableCount() {
            return availableBuffers.size();
        }

        @Override
        public int getTotalCount() {
            return totalBuffers.get();
        }

        @Override
        public int getDefaultBufferSize() {
            return 1024;
        }
    }

    /**
     * Mock async file chunker for testing.
     */
    public static class MockAsyncFileChunker implements AsyncFileChunker {
        private final AtomicInteger chunkCount;
        private volatile boolean closed;

        public MockAsyncFileChunker() {
            this.chunkCount = new AtomicInteger(0);
            this.closed = false;
        }

        @Override
        public void chunkFileAsync(Path file, ChunkingOptions options,
                CompletionHandler<ChunkingResult, Exception> handler) {
            if (closed) {
                handler.failed(new IllegalStateException("Chunker is closed"));
                return;
            }
            // Simulate async chunking
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(10); // Simulate work
                    ChunkingResult result = new ChunkingResult(file, 1, 1024, 0, "mock-hash", List.of("chunk1"));
                    chunkCount.incrementAndGet();
                    handler.completed(result);
                } catch (Exception e) {
                    handler.failed(e);
                }
            }).exceptionally(ex -> {
                handler.failed(new RuntimeException("Chunking failed", ex));
                return null;
            });
        }

        @Override
        public CompletableFuture<ChunkingResult> chunkFileAsync(Path file, ChunkingOptions options) {
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("Chunker is closed"));
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(10); // Simulate work
                    ChunkingResult result = new ChunkingResult(file, 1, 1024, 0, "mock-hash", List.of("chunk1"));
                    chunkCount.incrementAndGet();
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException("Chunking failed", e);
                }
            });
        }

        @Override
        public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
            // Mock implementation
        }

        @Override
        public AsyncByteBufferPool getAsyncBufferPool() {
            return new MockAsyncByteBufferPool();
        }

        @Override
        public void setAsyncChunkHandler(AsyncChunkHandler asyncChunkHandler) {
            // Mock implementation
        }

        @Override
        public AsyncChunkHandler getAsyncChunkHandler() {
            return new MockAsyncChunkHandler();
        }

        @Override
        public CompletableFuture<String> getStatsAsync() {
            return CompletableFuture.completedFuture(String.format(
                    "MockAsyncFileChunker{chunkCount=%d, closed=%b}", chunkCount.get(), closed));
        }

        @Override
        public boolean supportsOverlappingIO() {
            return true;
        }

        @Override
        public boolean supportsBackpressure() {
            return true;
        }

        @Override
        public CompletableFuture<Void> closeAsync() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public int getActiveOperations() {
            return 0;
        }

        @Override
        public int getMaxConcurrentOperations() {
            return 4;
        }

        @Override
        public void setMaxConcurrentOperations(int maxConcurrentOperations) {
            // Mock implementation
        }

        // Legacy interface methods
        @Override
        public void setBufferPool(BufferPool bufferPool) {
            // Mock implementation
        }

        @Override
        public void setChunkSize(int chunkSize) {
            // Mock implementation
        }

        @Override
        public int getChunkSize() {
            return 65536;
        }

        @Override
        public String storeChunk(byte[] data) {
            return "mock-chunk-" + data.hashCode();
        }

        @Override
        public byte[] retrieveChunk(String hash) {
            return new byte[0];
        }

        @Override
        public boolean existsChunk(String hash) {
            return false;
        }

        @Override
        public CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options) {
            return chunkFileAsync(file, options);
        }
    }

    /**
     * Mock async chunk handler for testing.
     */
    public static class MockAsyncChunkHandler implements AsyncChunkHandler {
        private final AtomicInteger processedChunks;
        private volatile int maxConcurrentChunks;

        public MockAsyncChunkHandler() {
            this.processedChunks = new AtomicInteger(0);
            this.maxConcurrentChunks = 4;
        }

        @Override
        public CompletableFuture<String> processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks,
                Path file) {
            processedChunks.incrementAndGet();
            return CompletableFuture.completedFuture("mock-chunk-" + chunkIndex);
        }

        @Override
        public void processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file,
                CompletionHandler<String, Exception> handler) {
            processChunkAsync(chunkData, chunkIndex, totalChunks, file)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            handler.failed(throwable instanceof Exception ? (Exception) throwable
                                    : new RuntimeException(throwable));
                        } else {
                            handler.completed(result);
                        }
                    });
        }

        @Override
        public CompletableFuture<String[]> processChunksAsync(ByteBuffer[] chunks, Path file) {
            String[] results = new String[chunks.length];
            for (int i = 0; i < chunks.length; i++) {
                results[i] = "mock-chunk-" + i;
            }
            processedChunks.addAndGet(chunks.length);
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public void processChunksAsync(ByteBuffer[] chunks, Path file, CompletionHandler<String[], Exception> handler) {
            processChunksAsync(chunks, file)
                    .whenComplete((results, throwable) -> {
                        if (throwable != null) {
                            handler.failed(throwable instanceof Exception ? (Exception) throwable
                                    : new RuntimeException(throwable));
                        } else {
                            handler.completed(results);
                        }
                    });
        }

        @Override
        public int getMaxConcurrentChunks() {
            return maxConcurrentChunks;
        }

        @Override
        public void setMaxConcurrentChunks(int maxConcurrentChunks) {
            this.maxConcurrentChunks = maxConcurrentChunks;
        }

        @Override
        public boolean supportsBackpressure() {
            return true;
        }

        @Override
        public CompletableFuture<Void> applyBackpressure() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> releaseBackpressure() {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Safe runner for isolated pool testing - prevents state bleeding between
     * tests.
     * Creates a new executor and pool for each test, ensuring complete isolation.
     *
     * @param poolSize   the size of the buffer pool
     * @param bufferSize the buffer size
     * @param testBody   the test logic to execute
     */
    public static void runWithIsolatedPool(int poolSize, int bufferSize, Consumer<AsyncByteBufferPool> testBody) {
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        AsyncByteBufferPool pool = AsyncByteBufferPoolImpl.create(bufferSize, poolSize);

        try {
            // Execute test directly (not async) to avoid executor issues
            testBody.accept(pool);
        } catch (Exception e) {
            throw new RuntimeException("Test execution failed", e);
        } finally {
            // 1. Shutdown Pool Logic (release buffers)
            try {
                pool.clear();
            } catch (Exception e) {
                System.err.println("Error clearing pool: " + e.getMessage());
            }

            // 2. Hard Shutdown of Executor
            executor.shutdownNow();
            try {
                // Wait a tiny bit to ensure threads die
                if (!executor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    System.err.println("Executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Safe runner for isolated pool testing with default buffer size.
     *
     * @param poolSize the size of the buffer pool
     * @param testBody the test logic to execute
     */
    public static void runWithIsolatedPool(int poolSize, Consumer<AsyncByteBufferPool> testBody) {
        runWithIsolatedPool(poolSize, 8192, testBody);
    }

    /**
     * Gets the result of a CompletableFuture with timeout handling for tests.
     *
     * @param future  the future to get result from
     * @param timeout the timeout duration
     * @param <T>     the result type
     * @return the result
     * @throws Exception if the future fails or times out
     */
    public static <T> T getFutureResult(CompletableFuture<T> future, Duration timeout) throws Exception {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Expects a future to fail with the specified exception type.
     *
     * @param future                the future to check
     * @param timeout               the timeout duration
     * @param expectedExceptionType the expected exception type
     * @param <T>                   the result type
     */
    public static <T> void expectFailedFuture(CompletableFuture<T> future, Duration timeout,
            Class<? extends Throwable> expectedExceptionType) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            fail("Expected future to fail with " + expectedExceptionType.getSimpleName()
                    + " but it completed successfully");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (!expectedExceptionType.isInstance(cause)) {
                fail("Expected " + expectedExceptionType.getSimpleName() + " but got "
                        + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }
        }
    }
}