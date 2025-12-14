package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.network.NetworkService;
import com.justsyncit.network.protocol.ChunkDataMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Stage that sends the processed chunk over the network.
 */
public class SendStage implements PipelineStage<ChunkTask, Void> {
    private static final Logger logger = LoggerFactory.getLogger(SendStage.class);
    private final ExecutorService executor;
    private final NetworkService networkService;
    private final InetSocketAddress remoteAddress;

    public SendStage(ExecutorService executor, NetworkService networkService, InetSocketAddress remoteAddress) {
        this.executor = executor;
        this.networkService = networkService;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public CompletableFuture<Void> process(ChunkTask task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Construct the message
                // Note: The protocol uses String identifier usually.
                // We use filename from path for now as per previous impl.
                String identifier = task.getFilePath().getFileName().toString();

                ChunkDataMessage msg = new ChunkDataMessage(
                        identifier,
                        task.getOffset(),
                        task.getProcessedData().length,
                        task.getTotalFileSize(),
                        task.getChecksum(),
                        task.getProcessedData());

                // Send it. NetworkService.sendMessage returns a Future.
                // We block here to respect the executor's worker thread (async-blocking-async
                // bridge)
                // OR better: we compose the future.
                return msg;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor).thenCompose(msg -> {
            try {
                return networkService.sendMessage(msg, remoteAddress);
            } catch (java.io.IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }).thenAccept(v -> {
            // Done
            // Could log simplified progress here
        });
    }

    @Override
    public String getName() {
        return "SendStage";
    }
}
