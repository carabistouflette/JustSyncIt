package com.justsyncit.network.transfer.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Stage that computes checksums for chunks.
 */
public class HashStage implements PipelineStage<ChunkTask, ChunkTask> {
    private static final Logger logger = LoggerFactory.getLogger(HashStage.class);
    private final ExecutorService executor;

    public HashStage(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ChunkTask> process(ChunkTask task) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] data = task.getRawData();
            if (data == null) {
                throw new IllegalStateException("Raw data is missing for hash stage");
            }

            // Calculate Hash
            // We'll use SHA-256 for standard Java, but in the detailed plan we mentioned
            // Blake3.
            // Since I don't see the Blake3 library in imports in FileTransferManagerImpl, I
            // will stick to
            // a simple has or standard digest unless the user provided a specific service.
            // In FileTransferManagerImpl there was a comment: "// This would use
            // Blake3Service to compute checksum"
            // we will simulate a strong hash here using SHA-256 for now.
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(data);

                // Convert to hex string
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }

                task.setChecksum(sb.toString());
                return task;
            } catch (Exception e) {
                logger.error("Failed to compute hash", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public String getName() {
        return "HashStage";
    }
}
