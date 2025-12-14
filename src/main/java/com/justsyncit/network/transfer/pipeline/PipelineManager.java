package com.justsyncit.network.transfer.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * Manages the lifecycle and execution resources for the transfer pipeline.
 */
public class PipelineManager {
    private static final Logger logger = LoggerFactory.getLogger(PipelineManager.class);

    private final ExecutorService executorService;
    private final int parallelism;

    /**
     * Creates a new PipelineManager with optimal parallelism settings.
     */
    public PipelineManager() {
        this.parallelism = Math.max(4, Runtime.getRuntime().availableProcessors());

        // Use a ForkJoinPool for work stealing efficiency, which is good for
        // irregular workloads often found in pipeline stages (compression varies by
        // data, IO varies)
        this.executorService = new ForkJoinPool(
                parallelism,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true // Async mode
        );

        logger.info("PipelineManager initialized with parallelism: {}", parallelism);
    }

    /**
     * Gets the shared executor service for pipeline stages.
     * 
     * @return the executor service
     */
    public ExecutorService getExecutor() {
        return executorService;
    }

    /**
     * Shuts down the pipeline manager and its resources.
     */
    public void shutdown() {
        logger.info("Shutting down PipelineManager...");
        executorService.shutdown();
    }

    /**
     * Gets the parallelism level.
     * 
     * @return number of target threads
     */
    public int getParallelism() {
        return parallelism;
    }
}
