package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.network.compression.CompressionService;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Pipeline stage for decompressing chunk data.
 */
public class DecompressStage {

    private final ExecutorService executor;
    private final CompressionService compressionService;
    private final String compressionType;

    public DecompressStage(ExecutorService executor, CompressionService compressionService, String compressionType) {
        this.executor = executor;
        this.compressionService = compressionService;
        this.compressionType = compressionType;
    }

    public CompletableFuture<byte[]> process(byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if ("ZSTD".equals(compressionType)) {
                    if (compressionService == null) {
                        throw new IOException("Received ZSTD compressed data but no compression service configured");
                    }
                    return compressionService.decompress(data);
                }
                // No compression or unknown type (treat as uncompressed for now, or add more
                // logic)
                return data;
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }
}
