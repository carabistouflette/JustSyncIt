package com.justsyncit.network.transfer.pipeline;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a single stage in the transfer pipeline.
 *
 * @param <I> Input type
 * @param <O> Output type
 */
public interface PipelineStage<I, O> {

    /**
     * Processes a single item.
     * 
     * @param input the input item
     * @return a future that completes with the output item
     */
    CompletableFuture<O> process(I input);

    /**
     * Gets the name of this stage.
     * 
     * @return the stage name
     */
    String getName();

    /**
     * Shuts down the stage and releases resources.
     */
    default void shutdown() {
    }
}
