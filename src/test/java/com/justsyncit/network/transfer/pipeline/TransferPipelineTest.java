package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.network.NetworkService;
import com.justsyncit.network.compression.CompressionService;
import com.justsyncit.network.protocol.ProtocolMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferPipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void testFullPipelineFlow() throws Exception {
        // Setup Mocks and Environment
        Path file = tempDir.resolve("pipeline_test.dat");
        byte[] content = new byte[] { 1, 2, 3, 4, 5 };
        Files.write(file, content);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        NetworkService networkService = Mockito.mock(NetworkService.class);
        CompressionService compressionService = Mockito.mock(CompressionService.class);
        InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 8080);

        // Mock behaviors
        when(compressionService.compress(any(byte[].class))).thenReturn(content); // Identity compression
        when(networkService.sendMessage(any(ProtocolMessage.class), eq(remoteAddress)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Create Stages
        ReadStage readStage = new ReadStage(executor);
        HashStage hashStage = new HashStage(executor);
        CompressStage compressStage = new CompressStage(executor, compressionService, true);
        SendStage sendStage = new SendStage(executor, networkService, remoteAddress);

        TransferPipeline pipeline = new TransferPipeline(readStage, hashStage, compressStage, sendStage);

        // Create Task
        ChunkTask task = new ChunkTask("test-id", file, 0, content.length, content.length);

        // Execute
        pipeline.submit(task).join();

        // Verify
        verify(networkService).sendMessage(any(ProtocolMessage.class), eq(remoteAddress));
        verify(compressionService).compress(any(byte[].class));

        executor.shutdown();
    }
}
