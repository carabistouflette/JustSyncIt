/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 */

package com.justsyncit.scanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Robustness tests for BatchScheduler to verify behavior under failure
 * conditions.
 */
@DisplayName("BatchScheduler Robustness Tests")
public class BatchSchedulerRobustnessTest {

    @Mock
    private ThreadPoolManager mockThreadPoolManager;

    @Mock
    private AsyncBatchProcessor mockAsyncBatchProcessor;

    private BatchScheduler batchScheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockThreadPoolManager.getThreadPool(any())).thenReturn(java.util.concurrent.ForkJoinPool.commonPool());
    }

    @AfterEach
    void tearDown() {
        if (batchScheduler != null) {
            batchScheduler.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Should release semaphore when processBatch throws synchronous exception")
    void shouldReleaseSemaphoreOnSynchronousFailure() throws InterruptedException {
        // Given
        BatchConfiguration config = new BatchConfiguration().withMaxConcurrentBatches(1);
        batchScheduler = BatchScheduler.create(mockThreadPoolManager, mockAsyncBatchProcessor, config);
        batchScheduler.start();

        List<Path> files1 = Arrays.asList(Paths.get("test1.txt"));
        List<Path> files2 = Arrays.asList(Paths.get("test2.txt"));
        BatchOptions options = new BatchOptions();

        // Setup: First batch throws RuntimeException synchronously
        // We use doThrow because we want to throw when called with ANY arguments first,
        // then succeed for second?
        // Or better, match by arguments.

        // Batch 1 fails synchronously
        org.mockito.Mockito.doThrow(new RuntimeException("Simulated synchronous failure"))
                .when(mockAsyncBatchProcessor).processBatch(eq(files1), any(), any());

        // Batch 2 succeeds (returns future)
        org.mockito.Mockito.doReturn(CompletableFuture.completedFuture(
                new BatchResult("id2", files2, Instant.now(), Instant.now(), 1, 0, 100, null, null, null)))
                .when(mockAsyncBatchProcessor).processBatch(eq(files2), any(), any());

        // When
        System.out.println("DEBUG: Scheduling first batch");
        try {
            batchScheduler.scheduleBatch(files1, options);
        } catch (Exception e) {
            System.out.println("DEBUG: Exception scheduling first batch: " + e);
            e.printStackTrace();
            throw e;
        }

        System.out.println("DEBUG: Sleeping");
        Thread.sleep(500); // Give time for first batch to fail

        System.out.println("DEBUG: Scheduling second batch");
        try {
            batchScheduler.scheduleBatch(files2, options);
        } catch (Exception e) {
            System.out.println("DEBUG: Exception scheduling second batch: " + e);
            e.printStackTrace();
            throw e;
        }

        System.out.println("DEBUG: Verifying second batch");
        // Then
        // If semaphore released, Batch 2 should be processed
        // We verify that processBatch was called for the second batch
        // We use a timeout to wait for it
        org.mockito.Mockito.verify(mockAsyncBatchProcessor, org.mockito.Mockito.timeout(2000))
                .processBatch(eq(files2), any(), any());
    }
}
