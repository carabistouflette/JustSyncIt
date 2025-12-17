package com.justsyncit.dedup.dashboard;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to track and report deduplication metrics.
 * Designed to be thread-safe for concurrent chunking operations.
 */
public class DedupMetricsService {
    private static final DedupMetricsService INSTANCE = new DedupMetricsService();

    private final AtomicLong totalBytesProcessed = new AtomicLong(0);
    private final AtomicLong uniqueBytesStored = new AtomicLong(0);
    private final AtomicLong duplicateBytesSaved = new AtomicLong(0);
    private final AtomicLong totalChunks = new AtomicLong(0);
    private final AtomicLong uniqueChunks = new AtomicLong(0);

    private DedupMetricsService() {
    }

    public static DedupMetricsService getInstance() {
        return INSTANCE;
    }

    public void recordChunkProcessing(long chunkSize, boolean isDuplicate) {
        totalBytesProcessed.addAndGet(chunkSize);
        totalChunks.incrementAndGet();
        if (isDuplicate) {
            duplicateBytesSaved.addAndGet(chunkSize);
        } else {
            uniqueBytesStored.addAndGet(chunkSize);
            uniqueChunks.incrementAndGet();
        }
    }

    public DedupStats getStats() {
        long total = totalBytesProcessed.get();
        long saved = duplicateBytesSaved.get();
        double ratio = total > 0 ? (double) total / (total - saved) : 1.0;
        double percentage = total > 0 ? (double) saved / total * 100.0 : 0.0;

        return new DedupStats(
                total,
                uniqueBytesStored.get(),
                saved,
                totalChunks.get(),
                uniqueChunks.get(),
                ratio,
                percentage);
    }

    public record DedupStats(
            long totalBytes,
            long uniqueBytes,
            long savedBytes,
            long totalChunks,
            long uniqueChunks,
            double dedupRatio,
            double savingsPercentage) {
    }
}
