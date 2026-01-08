package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.compression.CompressionService;
import com.justsyncit.network.encryption.EncryptionService;
import com.justsyncit.storage.ContentStore;

import java.net.InetSocketAddress;

/**
 * Factory for creating transfer pipelines.
 * Decouples the FileTransferManager from the concrete pipeline implementation.
 */
public interface TransferPipelineFactory {

        /**
         * Creates a new transfer pipeline.
         *
         * @param networkService     the network service for sending data
         * @param compressionService the compression service (can be null)
         * @param compressionEnabled whether compression is enabled
         * @param encryptionService  the encryption service (can be null)
         * @param encryptionKey      the encryption key (can be null)
         * @param encryptionEnabled  whether encryption is enabled
         * @param remoteAddress      the destination address
         * @return a configured transfer pipeline
         */
        TransferPipeline createPipeline(NetworkService networkService,
                        CompressionService compressionService,
                        boolean compressionEnabled,
                        EncryptionService encryptionService,
                        byte[] encryptionKey,
                        boolean encryptionEnabled,
                        InetSocketAddress remoteAddress);

        /**
         * Creates a new receive pipeline.
         *
         * @param compressionService the compression service
         * @param compressionType    the type of compression used
         * @param encryptionService  the encryption service
         * @param encryptionKey      the encryption key
         * @param encryptionEnabled  whether encryption is enabled
         * @param transferId         the transfer ID (for AEAD)
         * @param blake3Service      the hashing service
         * @param expectedChecksum   the expected checksum
         * @param chunkOffset        the chunk offset (for logging/errors)
         * @param contentStore       the content store
         * @return a configured receive pipeline
         */
        ReceivePipeline createReceivePipeline(CompressionService compressionService,
                        String compressionType,
                        EncryptionService encryptionService,
                        byte[] encryptionKey,
                        boolean encryptionEnabled,
                        String transferId,
                        Blake3Service blake3Service,
                        String expectedChecksum,
                        long chunkOffset,
                        ContentStore contentStore);
}
