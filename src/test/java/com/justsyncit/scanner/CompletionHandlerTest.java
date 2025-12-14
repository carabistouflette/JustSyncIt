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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CompletionHandler.
 * Tests the callback pattern implementation.
 */
class CompletionHandlerTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Simulate successful completion
        handler.completed("test result");

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertEquals("test result", resultRef.get(), "Result should be set");
        assertNull(errorRef.get(), "Error should not be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerFailure() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Simulate failed completion
        Exception testException = new RuntimeException("test error");
        handler.failed(testException);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertNull(resultRef.get(), "Result should not be set");
        assertEquals(testException, errorRef.get(), "Error should be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerWithCompletableFuture() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Convert CompletionHandler to CompletableFuture
        CompletableFuture<String> future = new CompletableFuture<>();
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                handler.failed(
                        throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
            } else {
                handler.completed(result);
            }
        });

        // Complete the future successfully
        future.complete("async result");

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertEquals("async result", resultRef.get(), "Result should be set");
        assertNull(errorRef.get(), "Error should not be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerWithCompletableFutureFailure() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Convert CompletionHandler to CompletableFuture
        CompletableFuture<String> future = new CompletableFuture<>();
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                handler.failed(
                        throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
            } else {
                handler.completed(result);
            }
        });

        // Complete the future with an exception
        Exception testException = new RuntimeException("async error");
        future.completeExceptionally(testException);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertNull(resultRef.get(), "Result should not be set");
        assertEquals(testException, errorRef.get(), "Error should be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerChaining() throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<String> resultRef1 = new AtomicReference<>();
        AtomicReference<String> resultRef2 = new AtomicReference<>();

        CompletionHandler<String, Exception> handler1 = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef1.set(result);
                latch1.countDown();
            }

            @Override
            public void failed(Exception exception) {
                latch1.countDown();
            }
        };

        CompletionHandler<String, Exception> handler2 = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef2.set(result);
                latch2.countDown();
            }

            @Override
            public void failed(Exception exception) {
                latch2.countDown();
            }
        };

        // Chain handlers: handler1 completes, which triggers handler2
        handler1.completed("result1");

        assertTrue(latch1.await(1, TimeUnit.SECONDS), "First latch should be released");
        assertEquals("result1", resultRef1.get(), "First result should be set");

        // Trigger second handler
        handler2.completed("result2");

        assertTrue(latch2.await(1, TimeUnit.SECONDS), "Second latch should be released");
        assertEquals("result2", resultRef2.get(), "Second result should be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerWithNullResult() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Test with null result (should be allowed)
        handler.completed(null);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertNull(resultRef.get(), "Result should be null");
        assertNull(errorRef.get(), "Error should not be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerWithNullException() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Test with null exception (should be allowed)
        handler.failed(null);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertNull(resultRef.get(), "Result should not be set");
        assertNull(errorRef.get(), "Error should be null");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerRuntimeException() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Test with runtime exception
        RuntimeException runtimeException = new RuntimeException("runtime error");
        handler.failed(runtimeException);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertNull(resultRef.get(), "Result should not be set");
        assertEquals(runtimeException, errorRef.get(), "Runtime exception should be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerCheckedException() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Test with checked exception
        Exception checkedException = new Exception("checked error");
        handler.failed(checkedException);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released");
        assertNull(resultRef.get(), "Result should not be set");
        assertEquals(checkedException, errorRef.get(), "Checked exception should be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerMultipleCalls() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Call completed twice (should work)
        handler.completed("first");
        handler.completed("second");

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Latch should be released after first call");
        assertEquals("second", resultRef.get(), "Last result should be set");
        assertNull(errorRef.get(), "Error should not be set");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompletionHandlerExceptionInCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        CompletionHandler<String, Exception> handler = new CompletionHandler<>() {
            @Override
            public void completed(String result) {
                resultRef.set(result);
                // Simulate exception in callback
                throw new RuntimeException("Exception in completed callback");
            }

            @Override
            public void failed(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        };

        // Test that exceptions in callbacks don't prevent latch from being released
        assertThrows(RuntimeException.class, () -> handler.completed("test"));

        // Latch should not be released due to exception
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS), "Latch should not be released due to exception");
        assertEquals("test", resultRef.get(), "Result should be set before exception");
        assertNull(errorRef.get(), "Error should not be set");
    }
}