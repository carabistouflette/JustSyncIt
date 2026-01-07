package com.justsyncit.network.transfer;

import com.justsyncit.network.protocol.ChunkDataMessage;
import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.quic.QuicTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

/**
 * Handles QUIC transport operations including file transfers and message
 * sending.
 * Extracts logic previously embedded in NetworkServiceImpl.
 */
public class QuicTransportHandler {

    private static final Logger logger = LoggerFactory.getLogger(QuicTransportHandler.class);
    private final QuicTransport quicTransport;
    private final com.justsyncit.network.NetworkStatisticsImpl statistics;

    public QuicTransportHandler(QuicTransport quicTransport, com.justsyncit.network.NetworkStatisticsImpl statistics) {
        this.quicTransport = quicTransport;
        this.statistics = statistics;
    }

    public CompletableFuture<Void> sendFilePart(Path filePath, long offset, long length,
            InetSocketAddress remoteAddress) {
        // QUIC impl doesn't support zero-copy partial transfer yet, fallback to reading
        // and sending
        return CompletableFuture.supplyAsync(() -> {
            try {
                try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
                    // Limit buffer size to 1MB or length
                    ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(length, 1024 * 1024));
                    fc.read(buffer, offset);
                    buffer.flip();

                    long fileSize = Files.size(filePath);
                    ProtocolMessage partMessage = new ChunkDataMessage(
                            filePath.toString(), offset, buffer.remaining(), fileSize, "hash-placeholder",
                            buffer.array());

                    // Recursively call sendMessage (this class's method)
                    sendMessage(partMessage, remoteAddress).join();
                }
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress) {
        return quicTransport.sendMessage(message, remoteAddress).thenRun(() -> {
            if (statistics != null) {
                statistics.incrementBytesSent(message.getTotalSize());
            }
            logger.trace("Message sent via QUIC to {}: {}", remoteAddress, message.getMessageType());
        }).exceptionally(throwable -> {
            logger.error("Failed to send message via QUIC to {}: {}", remoteAddress, message.getMessageType(),
                    throwable);
            return null;
        });
    }
}
