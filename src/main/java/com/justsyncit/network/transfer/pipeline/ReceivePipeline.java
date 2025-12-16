package com.justsyncit.network.transfer.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates the pipeline for processing a received chunk.
 */
public class ReceivePipeline {

    private static final Logger logger = LoggerFactory.getLogger(ReceivePipeline.class);

    private final DecompressStage decompressStage;
    private final VerifyStage verifyStage;
    private final StoreStage storeStage;

    public ReceivePipeline(DecompressStage decompressStage, VerifyStage verifyStage, StoreStage storeStage) {
        this.decompressStage = decompressStage;
        this.verifyStage = verifyStage;
        this.storeStage = storeStage;
    }

    /**
     * Processes a chunk through the pipeline.
     *
     * @param chunkData the raw chunk data
     * @return a future that completes with the uncompressed size of the stored
     *         data, or fails heavily
     */
    public CompletableFuture<Integer> process(byte[] chunkData) {
        return decompressStage.process(chunkData)
                .thenCompose(verifyStage::process)
                .thenCompose(storeStage::process)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Receive pipeline failed", ex);
                    }
                });
    }
}
