/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.transfer;

import com.justsyncit.network.NetworkService;
import com.justsyncit.network.compression.CompressionService;
import com.justsyncit.network.compression.ZstdCompressionService;
import com.justsyncit.network.protocol.ChunkDataMessage;
import com.justsyncit.network.protocol.FileTransferRequestMessage;
import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.storage.ContentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileTransferCompressionTest {

    private FileTransferManagerImpl transferManager;
    private NetworkService networkService;
    private CompressionService compressionService;
    private ContentStore contentStore;
    private InetSocketAddress remoteAddress;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        networkService = mock(NetworkService.class);
        contentStore = mock(ContentStore.class);
        compressionService = new ZstdCompressionService();
        remoteAddress = new InetSocketAddress("localhost", 8080);

        transferManager = new FileTransferManagerImpl();
        transferManager.setNetworkService(networkService);
        transferManager.setCompressionService(compressionService);
        transferManager.start().join();
    }

    @AfterEach
    void tearDown() {
        transferManager.stop().join();
    }

    @Test
    void testSendFileWithCompression() throws IOException {
        // Create a test file with repetitive content for good compression
        Path testFile = tempDir.resolve("test_data.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("CompressMe ");
        }
        byte[] originalData = sb.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(testFile, originalData);

        when(networkService.sendMessage(any(ProtocolMessage.class), any(InetSocketAddress.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Start transfer
        transferManager.sendFile(testFile, remoteAddress, contentStore).join();

        // Check messages
        ArgumentCaptor<ProtocolMessage> captor = ArgumentCaptor.forClass(ProtocolMessage.class);
        // Verify multiple calls: Request + Chunks + Complete
        verify(networkService, times(3)).sendMessage(captor.capture(), eq(remoteAddress));

        ProtocolMessage reqMsg = captor.getAllValues().get(0);
        assertTrue(reqMsg instanceof FileTransferRequestMessage);
        FileTransferRequestMessage ftReq = (FileTransferRequestMessage) reqMsg;
        assertEquals("ZSTD", ftReq.getCompressionType());

        ProtocolMessage chunkMsg = captor.getAllValues().get(1);
        assertTrue(chunkMsg instanceof ChunkDataMessage);
        ChunkDataMessage cdm = (ChunkDataMessage) chunkMsg;

        byte[] sentData = cdm.getChunkData();
        assertTrue(sentData.length < originalData.length, "Data should be compressed");

        // Verify sending correct identifier (filename)
        assertEquals(testFile.getFileName().toString(), cdm.getFilePath());
    }

    @Test
    void testHandleCompressedChunk() throws IOException {
        String fileName = "test_receive.txt";
        long fileSize = 1000;

        // Register transfer manually
        ProtocolMessage req = new FileTransferRequestMessage(fileName, fileSize, System.currentTimeMillis(), "hash",
                65536, "ZSTD");
        transferManager.handleFileTransferRequest(req, remoteAddress, contentStore).join();

        // Prepare compressed data
        byte[] originalChunk = "This is a test chunk that should be compressed".getBytes(StandardCharsets.UTF_8);
        byte[] compressedChunk = compressionService.compress(originalChunk);

        // Compute checksum expected by manager (of DECOMPRESSED data)
        // Compute checksum expected by manager (of DECOMPRESSED data)
        String checksum;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(originalChunk);
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            checksum = hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        ChunkDataMessage chunkMsg = new ChunkDataMessage(fileName, 0, compressedChunk.length, fileSize, checksum,
                compressedChunk);

        // Handle chunk
        transferManager.handleChunkData(chunkMsg, remoteAddress, contentStore).join();

        // Verify content store received DECOMPRESSED data
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(contentStore).storeChunk(dataCaptor.capture());

        assertArrayEquals(originalChunk, dataCaptor.getValue());
    }
}
