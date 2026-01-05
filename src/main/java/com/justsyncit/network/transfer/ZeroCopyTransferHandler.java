package com.justsyncit.network.transfer;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.connection.Connection;
import com.justsyncit.network.protocol.ChunkDataMessage;
import com.justsyncit.network.protocol.ProtocolConstants;
import com.justsyncit.network.protocol.ProtocolHeader;
import com.justsyncit.scanner.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Handles zero-copy file transfers with memory-mapped checksumming.
 * Extracted from NetworkServiceImpl to reduce complexity.
 */
public class ZeroCopyTransferHandler {

    private static final Logger logger = LoggerFactory.getLogger(ZeroCopyTransferHandler.class);
    private final Blake3Service blake3Service;

    public ZeroCopyTransferHandler(Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
    }

    /**
     * Sends a file part using zero-copy transfer and memory-mapped checksumming.
     * Offloads blocking I/O and CPU operations to the thread pool.
     */
    public CompletableFuture<Void> sendFilePart(Connection connection, Path filePath, long offset, long length,
            int messageId) {
        return CompletableFuture.supplyAsync(() -> {
            // Open the file channel - we need it to stay open for the transfer
            FileChannel fileChannel;
            try {
                fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
            } catch (IOException e) {
                throw new CompletionException(e);
            }

            try {
                // 1. Calculate checksum using Memory-Mapped I/O
                String hash;
                try {
                    MappedByteBuffer mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, length);
                    hash = blake3Service.hashBuffer(mappedBuffer);
                } catch (IOException e) {
                    logger.error("Failed to map file for checksumming: {}", filePath, e);
                    try {
                        fileChannel.close();
                    } catch (IOException ignored) {
                    }
                    throw new CompletionException(e);
                }

                // 2. Prepare the ChunkDataMessage
                long totalSize = Files.size(filePath);
                ChunkDataMessage templateMsg = new ChunkDataMessage(
                        filePath.toString(),
                        offset,
                        (int) length,
                        totalSize,
                        hash,
                        new byte[0] // Empty data for the template
                );

                // 3. Serialize Header and adjust Payload Length
                ByteBuffer headerBuf = templateMsg.serialize();
                ProtocolHeader header = ProtocolHeader.deserialize(headerBuf);

                int metadataSize = templateMsg.getPayloadSize();
                int newPayloadLength = metadataSize + (int) length;

                ProtocolHeader newHeader = new ProtocolHeader(
                        header.getMagic(),
                        header.getVersion(),
                        header.getMessageType(),
                        header.getFlags(),
                        newPayloadLength,
                        messageId);

                ByteBuffer newHeaderBuf = newHeader.serialize();
                headerBuf.position(ProtocolConstants.HEADER_SIZE);

                ByteBuffer finalMetaBuffer = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE + headerBuf.remaining());
                finalMetaBuffer.put(newHeaderBuf);
                finalMetaBuffer.put(headerBuf);
                finalMetaBuffer.flip();

                // 4. Send header then zero-copy body
                return connection.send(finalMetaBuffer)
                        .thenCompose(v -> connection.sendFileRegion(fileChannel, offset, length))
                        .whenComplete((v, ex) -> {
                            try {
                                fileChannel.close();
                            } catch (IOException e) {
                                logger.warn("Failed to close file channel for {}", filePath, e);
                            }

                            if (ex != null) {
                                logger.error("Failed to send file part: {}", filePath, ex);
                            } else {
                                logger.debug("Sent file part: {} offset={} length={} hash={}", filePath, offset, length,
                                        hash);
                            }
                        });

            } catch (Exception e) {
                try {
                    fileChannel.close();
                } catch (IOException closeEx) {
                    // ignore
                }
                throw new CompletionException(e);
            }
        }, ThreadPoolManager.getInstance().getIoThreadPool()).thenCompose(f -> f);
    }
}
