package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.storage.ContentStore;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Pipeline stage for storing the verified chunks.
 */
public class StoreStage {

    private final ExecutorService executor;
    private final ContentStore contentStore;

    public StoreStage(ExecutorService executor, ContentStore contentStore) {
        this.executor = executor;
        this.contentStore = contentStore;
    }

    public CompletableFuture<Integer> process(byte[] data) {
        return CompletableFuture.runAsync(() -> {
            try {
                contentStore.storeChunk(data);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor).thenApply(v -> data.length);
    }
}
