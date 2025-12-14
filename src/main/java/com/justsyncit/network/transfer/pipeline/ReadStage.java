package com.justsyncit.network.transfer.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Stage that reads file chunks.
 * Uses MappedByteBuffer for efficient OS-level caching if possible,
 * or normal file channel reads.
 */
public class ReadStage implements PipelineStage<ChunkTask, ChunkTask> {
    private static final Logger logger = LoggerFactory.getLogger(ReadStage.class);
    private final ExecutorService executor;

    public ReadStage(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ChunkTask> process(ChunkTask task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // In a real high-perf scenario, we might keep FileChannels open in a cache
                // For this implementation, we open on demand to ensure safety, but
                // we could optimize this later by passing the channel in the task context if
                // available.
                try (FileChannel channel = FileChannel.open(task.getFilePath(), StandardOpenOption.READ)) {

                    // Option A: Standard read (simpler, less OS dependent issues)
                    // ByteBuffer buffer = ByteBuffer.allocate(task.getLength());
                    // channel.read(buffer, task.getOffset());
                    // task.setRawData(buffer.array());

                    // Option B: MappedByteBuffer for zero-copy-ish reads
                    // (Actually, to get byte[] we still copy, but it might be faster due to OS page
                    // cache)
                    // MappedByteBuffer is tricky with GC, so let's stick to standard efficient
                    // reading first
                    // or use MappedByteBuffer if we want to avoid kernel-user copy, BUT we
                    // eventually need byte[]
                    // for compression.

                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(task.getLength());
                    int bytesRead = channel.read(buffer, task.getOffset());

                    if (bytesRead == -1) {
                        throw new IOException("End of file reached unexpectedly at offset " + task.getOffset());
                    }

                    if (bytesRead < task.getLength()) {
                        // This might happen at the end of file, should handle gracefully or ensure
                        // task.length is correct
                        // For now we assume task length matches file size remaining
                        byte[] exactData = new byte[bytesRead];
                        System.arraycopy(buffer.array(), 0, exactData, 0, bytesRead);
                        task.setRawData(exactData);
                    } else {
                        task.setRawData(buffer.array());
                    }

                    return task;
                }
            } catch (IOException e) {
                logger.error("Failed to read chunk for file {} at offset {}", task.getFilePath(), task.getOffset(), e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public String getName() {
        return "ReadStage";
    }
}
