package com.justsyncit.scanner;

import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Mock implementation of AsyncFilesystemScanner for testing.
 * Provides controllable behavior and state tracking for test scenarios.
 */
@DisplayName("Mock Async Filesystem Scanner")
public class MockAsyncFilesystemScanner implements AsyncFilesystemScanner {

    private final AtomicInteger scanCount;
    private final AtomicInteger activeOperations;
    private final AtomicLong totalFilesScanned;
    private final AtomicLong totalBytesScanned;
    private final List<Path> scannedFiles;
    private volatile boolean closed;
    private volatile int maxConcurrentScans;
    private final List<AsyncScanResult> scanHistory;
    private final Map<String, CompletableFuture<Void>> activeScans;
    private AsyncFileVisitor asyncFileVisitor;
    private AsyncByteBufferPool asyncBufferPool;
    private AsyncProgressListener asyncProgressListener;
    private double backpressureLevel;
    private SymlinkStrategy symlinkStrategy;
    private int maxDepth;
    private FileVisitor fileVisitor;
    private FilesystemScanner.ProgressListener progressListener;

    public MockAsyncFilesystemScanner() {
        this.scanCount = new AtomicInteger(0);
        this.activeOperations = new AtomicInteger(0);
        this.totalFilesScanned = new AtomicLong(0);
        this.totalBytesScanned = new AtomicLong(0);
        this.scannedFiles = new ArrayList<>();
        this.closed = false;
        this.maxConcurrentScans = 4;
        this.scanHistory = new ArrayList<>();
        this.activeScans = new HashMap<>();
        this.backpressureLevel = 0.0;
        this.symlinkStrategy = SymlinkStrategy.FOLLOW;
        this.maxDepth = Integer.MAX_VALUE;
    }

    @Override
    public CompletableFuture<AsyncScanResult> scanDirectoryAsync(Path directory, ScanOptions options) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        String scanId = "scan-" + System.currentTimeMillis();
        activeOperations.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50); // Simulate scanning work
                AsyncScanResult result = createMockAsyncScanResult(directory, options, scanId);
                scanCount.incrementAndGet();
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Scanning failed", e);
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public CompletableFuture<WatchServiceRegistration> startDirectoryMonitoring(
            Path directory, 
            AsyncScanOptions options, 
            Consumer<FileChangeEvent> eventHandler) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(10); // Simulate registration work
                return new WatchServiceRegistration(
                    directory, 
                    options.getWatchEventKinds(), 
                    options.isRecursiveWatching(), 
                    options
                );
            } catch (Exception e) {
                throw new RuntimeException("Monitoring registration failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> stopDirectoryMonitoring(WatchServiceRegistration registration) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10); // Simulate stopping work
                registration.deactivate();
            } catch (Exception e) {
                throw new RuntimeException("Monitoring stop failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<AsyncScanResult> scanDirectoryParallel(
            Path directory, 
            ScanOptions options, 
            int concurrency) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        String scanId = "parallel-scan-" + System.currentTimeMillis();
        activeOperations.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(30); // Simulate parallel scanning work
                AsyncScanResult result = createMockAsyncScanResult(directory, options, scanId);
                scanCount.incrementAndGet();
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Parallel scanning failed", e);
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public CompletableFuture<Void> scanDirectoryStreaming(
            Path directory, 
            ScanOptions options, 
            Consumer<AsyncScanResult> resultConsumer) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        String scanId = "streaming-scan-" + System.currentTimeMillis();
        activeOperations.incrementAndGet();
        
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(20); // Simulate streaming work
                AsyncScanResult result = createMockAsyncScanResult(directory, options, scanId);
                resultConsumer.accept(result);
                scanCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException("Streaming scanning failed", e);
            } finally {
                activeOperations.decrementAndGet();
            }
        });
    }

    @Override
    public void setAsyncFileVisitor(AsyncFileVisitor asyncVisitor) {
        this.asyncFileVisitor = asyncVisitor;
    }

    @Override
    public AsyncFileVisitor getAsyncFileVisitor() {
        return asyncFileVisitor;
    }

    @Override
    public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
        this.asyncBufferPool = asyncBufferPool;
    }

    @Override
    public AsyncByteBufferPool getAsyncBufferPool() {
        return asyncBufferPool;
    }

    @Override
    public void setAsyncProgressListener(AsyncProgressListener asyncProgressListener) {
        this.asyncProgressListener = asyncProgressListener;
    }

    @Override
    public AsyncProgressListener getAsyncProgressListener() {
        return asyncProgressListener;
    }

    @Override
    public boolean cancelScan(String scanId) {
        CompletableFuture<Void> scan = activeScans.get(scanId);
        if (scan != null) {
            scan.cancel(true);
            activeScans.remove(scanId);
            return true;
        }
        return false;
    }

    @Override
    public int getActiveScanCount() {
        return activeOperations.get();
    }

    @Override
    public int getMaxConcurrentScans() {
        return maxConcurrentScans;
    }

    @Override
    public void setMaxConcurrentScans(int maxConcurrentScans) {
        this.maxConcurrentScans = maxConcurrentScans;
    }

    @Override
    public CompletableFuture<AsyncScannerStats> getStatsAsync() {
        return CompletableFuture.completedFuture(createMockStats());
    }

    @Override
    public void applyBackpressure(double pressureLevel) {
        this.backpressureLevel = Math.max(0.0, Math.min(1.0, pressureLevel));
    }

    @Override
    public void releaseBackpressure() {
        this.backpressureLevel = 0.0;
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

    // Legacy interface methods
    @Override
    public CompletableFuture<ScanResult> scanDirectory(Path directory, ScanOptions options) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50); // Simulate scanning work
                ScanResult result = createMockScanResult(directory, options);
                scanCount.incrementAndGet();
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Scanning interrupted", e);
            }
        });
    }

    @Override
    public void setFileVisitor(FileVisitor visitor) {
        this.fileVisitor = visitor;
    }

    @Override
    public void setProgressListener(FilesystemScanner.ProgressListener listener) {
        this.progressListener = listener;
    }

    // Test control methods
    public void reset() {
        scanCount.set(0);
        activeOperations.set(0);
        totalFilesScanned.set(0);
        totalBytesScanned.set(0);
        scannedFiles.clear();
        scanHistory.clear();
        activeScans.clear();
        closed = false;
        backpressureLevel = 0.0;
    }

    public int getScanCount() {
        return scanCount.get();
    }

    public long getTotalFilesScanned() {
        return totalFilesScanned.get();
    }

    public long getTotalBytesScanned() {
        return totalBytesScanned.get();
    }

    public List<Path> getScannedFiles() {
        return new ArrayList<>(scannedFiles);
    }

    public List<AsyncScanResult> getScanHistory() {
        return new ArrayList<>(scanHistory);
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getBackpressureLevel() {
        return backpressureLevel;
    }

    /**
     * Simulate scanning failure
     */
    public void simulateFailure() {
        closed = true;
    }

    /**
     * Simulate scanning delay
     */
    public void simulateDelay(long delayMs) {
        // This would be used in conjunction with test configuration
    }

    private AsyncScanResult createMockAsyncScanResult(Path directory, ScanOptions options, String scanId) {
        // Create mock scanned files
        List<ScanResult.ScannedFile> mockFiles = List.of(
            new ScanResult.ScannedFile(directory.resolve("file1.txt"), 1024, Instant.now(), false, false, null),
            new ScanResult.ScannedFile(directory.resolve("file2.txt"), 2048, Instant.now(), false, false, null),
            new ScanResult.ScannedFile(directory.resolve("subdir/file3.txt"), 4096, Instant.now(), false, false, null)
        );
        
        List<ScanResult.ScanError> mockErrors = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scanType", "mock");
        metadata.put("scanner", "MockAsyncFilesystemScanner");
        
        Instant startTime = Instant.now().minusMillis(100);
        Instant endTime = Instant.now();
        
        ScanResult baseResult = new ScanResult(directory, mockFiles, mockErrors, startTime, endTime, metadata);
        
        // Create AsyncScanResult with additional async-specific data
        Map<String, Object> asyncMetadata = new HashMap<>();
        asyncMetadata.put("threadCount", 2);
        asyncMetadata.put("parallelism", options instanceof AsyncScanOptions ? 
            ((AsyncScanOptions) options).getParallelism() : 1);
        
        AsyncScanResult result = new AsyncScanResult(
            scanId, directory, mockFiles, mockErrors, startTime, endTime, metadata,
            2, // threadCount
            100.0, // throughput (files/sec)
            8192, // peakMemoryUsage
            1, // directoriesScanned
            0, // symbolicLinksEncountered
            0, // sparseFilesDetected
            0, // backpressureEvents
            false, // wasCancelled
            asyncMetadata
        );
        
        scannedFiles.addAll(mockFiles.stream().map(ScanResult.ScannedFile::getPath).toList());
        totalFilesScanned.addAndGet(mockFiles.size());
        totalBytesScanned.addAndGet(mockFiles.stream().mapToLong(ScanResult.ScannedFile::getSize).sum());
        scanHistory.add(result);
        
        return result;
    }

    private ScanResult createMockScanResult(Path directory, ScanOptions options) {
        // Create mock scanned files
        List<ScanResult.ScannedFile> mockFiles = List.of(
            new ScanResult.ScannedFile(directory.resolve("file1.txt"), 1024, Instant.now(), false, false, null),
            new ScanResult.ScannedFile(directory.resolve("file2.txt"), 2048, Instant.now(), false, false, null),
            new ScanResult.ScannedFile(directory.resolve("subdir/file3.txt"), 4096, Instant.now(), false, false, null)
        );
        
        List<ScanResult.ScanError> mockErrors = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scanType", "mock");
        metadata.put("scanner", "MockAsyncFilesystemScanner");
        
        Instant startTime = Instant.now().minusMillis(100);
        Instant endTime = Instant.now();
        
        ScanResult result = new ScanResult(directory, mockFiles, mockErrors, startTime, endTime, metadata);
        
        scannedFiles.addAll(mockFiles.stream().map(ScanResult.ScannedFile::getPath).toList());
        totalFilesScanned.addAndGet(mockFiles.size());
        totalBytesScanned.addAndGet(mockFiles.stream().mapToLong(ScanResult.ScannedFile::getSize).sum());
        
        return result;
    }

    private AsyncScannerStats createMockStats() {
        AsyncScannerStats stats = new AsyncScannerStats();
        stats.incrementScansInitiated();
        stats.incrementScansCompleted();
        stats.addFilesScanned(totalFilesScanned.get());
        stats.addBytesProcessed(totalBytesScanned.get());
        stats.setAverageThroughput(100.0);
        stats.setPeakMemoryUsage(8192);
        stats.setUptimeMs(System.currentTimeMillis());
        return stats;
    }
}