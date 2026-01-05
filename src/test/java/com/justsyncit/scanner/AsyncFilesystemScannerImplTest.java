package com.justsyncit.scanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AsyncFilesystemScanner Implementation Tests")

class AsyncFilesystemScannerImplTest extends AsyncTestBase {

    @TempDir
    Path tempDir;

    private AsyncFilesystemScanner scanner;
    private ThreadPoolManager threadPoolManager;
    private AsyncByteBufferPool bufferPool;

    @BeforeEach
    void setUp() {
        super.setUp();
        threadPoolManager = ThreadPoolManager.getInstance();
        bufferPool = AsyncByteBufferPoolImpl.create(1024 * 1024, 4);
        scanner = new AsyncFilesystemScannerImpl(threadPoolManager, bufferPool);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scanner != null) {
            scanner.closeAsync();
        }
        if (bufferPool != null) {
            bufferPool.clearAsync();
        }
        super.tearDown();
    }

    @Test
    @DisplayName("Should scan directory asynchronously")
    void shouldScanDirectoryAsync() throws Exception {
        // Given
        createTestFiles(tempDir, "test", 5, 1024);
        AsyncScanOptions options = new AsyncScanOptions();

        // When
        CompletableFuture<AsyncScanResult> future = scanner.scanDirectoryAsync(tempDir, options);
        AsyncScanResult result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertNotNull(result);
        assertEquals(tempDir, result.getRootDirectory());
        assertEquals(5, result.getScannedFileCount());
        assertEquals(0, result.getErrorCount());
        assertEquals(5 * 1024, result.getTotalSize());
    }

    @Test
    @DisplayName("Should handle concurrent scans")
    void shouldHandleConcurrentScans() throws Exception {
        // Given
        Path dir1 = tempDir.resolve("scan1");
        Path dir2 = tempDir.resolve("scan2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
        createTestFiles(dir1, "f1", 3, 100);
        createTestFiles(dir2, "f2", 3, 100);

        AsyncScanOptions options = new AsyncScanOptions();

        // When
        CompletableFuture<AsyncScanResult> f1 = scanner.scanDirectoryAsync(dir1, options);
        CompletableFuture<AsyncScanResult> f2 = scanner.scanDirectoryAsync(dir2, options);

        AsyncScanResult r1 = f1.get(5, TimeUnit.SECONDS);
        AsyncScanResult r2 = f2.get(5, TimeUnit.SECONDS);

        // Then
        assertEquals(3, r1.getScannedFileCount());
        assertEquals(3, r2.getScannedFileCount());
    }

    @Test
    @DisplayName("Should handle streaming scan")
    void shouldHandleStreamingScan() throws Exception {
        // Given
        createTestFiles(tempDir, "stream", 10, 100);
        AsyncScanOptions options = new AsyncScanOptions();
        AtomicInteger streamedCount = new AtomicInteger(0);

        // When
        CompletableFuture<Void> future = scanner.scanDirectoryStreaming(tempDir, options, partial -> {
            streamedCount.addAndGet(partial.getScannedFileCount());
        });

        future.get(5, TimeUnit.SECONDS);

        // Then
        // The streaming consumer should have received at least 10 items (could be
        // individually or batched)
        assertTrue(streamedCount.get() >= 10);
    }

    private void createTestFiles(Path dir, String prefix, int count, int size) throws Exception {
        for (int i = 0; i < count; i++) {
            AsyncTestUtils.createTestFile(dir, prefix + i, size);
        }
    }
}
