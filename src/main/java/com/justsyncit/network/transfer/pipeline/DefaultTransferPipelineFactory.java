package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.compression.CompressionService;
import com.justsyncit.storage.ContentStore;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

/**
 * Default implementation of TransferPipelineFactory.
 * Manages the shared PipelineManager resource.
 */
public class DefaultTransferPipelineFactory implements TransferPipelineFactory {

    private final PipelineManager pipelineManager;

    /**
     * Creates a new default factory.
     * Initializes a shared PipelineManager.
     */
    public DefaultTransferPipelineFactory() {
        this.pipelineManager = new PipelineManager();
    }

    /**
     * Creates a new default factory with an existing PipelineManager.
     *
     * @param pipelineManager the pipeline manager to use
     */
    public DefaultTransferPipelineFactory(PipelineManager pipelineManager) {
        this.pipelineManager = pipelineManager;
    }

    @Override
    public TransferPipeline createPipeline(NetworkService networkService,
            CompressionService compressionService,
            boolean compressionEnabled,
            InetSocketAddress remoteAddress) {

        boolean useCompression = compressionEnabled && compressionService != null;
        ExecutorService executor = pipelineManager.getExecutor();

        ReadStage readStage = new ReadStage(executor);
        HashStage hashStage = new HashStage(executor);
        CompressStage compressStage = new CompressStage(executor, compressionService, useCompression);
        SendStage sendStage = new SendStage(executor, networkService, remoteAddress);

        return new TransferPipeline(readStage, hashStage, compressStage, sendStage);
    }

    @Override
    public ReceivePipeline createReceivePipeline(CompressionService compressionService,
            String compressionType,
            Blake3Service blake3Service,
            String expectedChecksum,
            long chunkOffset,
            ContentStore contentStore) {

        // Use CPU pool for decompression and verification
        ExecutorService cpuExecutor = com.justsyncit.scanner.ThreadPoolManager.getInstance().getCpuThreadPool();
        // Use IO pool for storage
        ExecutorService ioExecutor = com.justsyncit.scanner.ThreadPoolManager.getInstance().getIoThreadPool();

        DecompressStage decompressStage = new DecompressStage(cpuExecutor, compressionService, compressionType);
        VerifyStage verifyStage = new VerifyStage(cpuExecutor, blake3Service, expectedChecksum, chunkOffset);
        StoreStage storeStage = new StoreStage(ioExecutor, contentStore);

        return new ReceivePipeline(decompressStage, verifyStage, storeStage);
    }

    /**
     * Shuts down the underlying PipelineManager.
     */
    public void shutdown() {
        pipelineManager.shutdown();
    }
}
