package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.network.compression.CompressionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Stage that compresses chunk data.
 */
public class CompressStage implements PipelineStage<ChunkTask, ChunkTask> {
    private static final Logger logger = LoggerFactory.getLogger(CompressStage.class);
    private final ExecutorService executor;
    private final CompressionService compressionService;
    private final boolean enabled;

    public CompressStage(ExecutorService executor, CompressionService compressionService, boolean enabled) {
        this.executor = executor;
        this.compressionService = compressionService;
        this.enabled = enabled;
    }

    @Override
    public CompletableFuture<ChunkTask> process(ChunkTask task) {
        if (!enabled || compressionService == null) {
            // Passthrough if disabled
            task.setProcessedData(task.getRawData());
            return CompletableFuture.completedFuture(task);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] input = task.getRawData();
                // Assuming compressionService is thread-safe or we need to synchronize?
                // Most modern compression libs are thread-safe or stateless per method call.
                // If it's the ZstdCompressionService we saw earlier, it should be okay.
                byte[] compressed = compressionService.compress(input);

                // If compression increases size (rare but possible), send original?
                // The protocol definition might expect compressed data if we negotiated it.
                // For now, always trust the compressor.

                task.setProcessedData(compressed);
                return task;
            } catch (Exception e) {
                logger.error("Compression failed", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public String getName() {
        return "CompressStage";
    }
}
