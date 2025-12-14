package com.justsyncit.network.transfer.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Orchestrates the full transfer pipeline for a specific file.
 * Manages backpressure to ensure we don't read too many chunks into memory
 * before they are sent.
 */
public class TransferPipeline {
    private static final Logger logger = LoggerFactory.getLogger(TransferPipeline.class);

    // Backpressure control: Limit number of chunks "in flight"
    // A standard 64KB chunk * 100 = 6.4MB of raw buffer + overhead
    private static final int MAX_IN_FLIGHT_CHUNKS = 100; // Tunable

    private final ReadStage readStage;
    private final HashStage hashStage;
    private final CompressStage compressStage;
    private final SendStage sendStage;

    private final Semaphore flowControl;
    private final List<CompletableFuture<Void>> inFlightFutures;

    public TransferPipeline(ReadStage readStage, HashStage hashStage, CompressStage compressStage,
            SendStage sendStage) {
        this.readStage = readStage;
        this.hashStage = hashStage;
        this.compressStage = compressStage;
        this.sendStage = sendStage;

        this.flowControl = new Semaphore(MAX_IN_FLIGHT_CHUNKS);
        this.inFlightFutures = new ArrayList<>();
    }

    /**
     * Submits a chunk task to the pipeline.
     * This method might block if the pipeline is full (backpressure).
     * 
     * @param task the chunk task definition (without data yet)
     * @return a future tracking this specific chunk's completion
     */
    public CompletableFuture<Void> submit(ChunkTask task) {
        try {
            // Acquire permit (blocks if full)
            flowControl.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }

        // Chain the stages
        CompletableFuture<Void> pipelineFuture = readStage.process(task)
                .thenCompose(hashStage::process)
                .thenCompose(compressStage::process)
                .thenCompose(sendStage::process)
                .handle((result, ex) -> {
                    // Release permit regardless of success/failure
                    flowControl.release();
                    if (ex != null) {
                        logger.error("Pipeline error for chunk offset {}", task.getOffset(), ex);
                        throw new RuntimeException(ex);
                    }
                    return null;
                });

        synchronized (inFlightFutures) {
            inFlightFutures.add(pipelineFuture);
            // Cleanup finished futures periodically to avoid memory leak in the list itself
            // (simple implementation for now)
            inFlightFutures.removeIf(CompletableFuture::isDone);
        }

        return pipelineFuture;
    }

    /**
     * Waits for all currently submitted tasks to complete.
     */
    public CompletableFuture<Void> waitForCompletion() {
        List<CompletableFuture<Void>> futures;
        synchronized (inFlightFutures) {
            futures = new ArrayList<>(inFlightFutures);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }
}
