package com.justsyncit.network.transfer;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.connection.Connection;
import com.justsyncit.network.encryption.EncryptionException;
import com.justsyncit.network.encryption.EncryptionService;
import com.justsyncit.network.protocol.ChunkDataMessage;
import com.justsyncit.network.protocol.ProtocolConstants;
import com.justsyncit.network.protocol.ProtocolHeader;
import com.justsyncit.scanner.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Handles secure file transfers with encryption and efficient buffering.
 * Replaces ZeroCopyTransferHandler to address security and performance issues.
 */
public class SecureTransferHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecureTransferHandler.class);
    private final Blake3Service blake3Service;
    private final EncryptionService encryptionService;

    public SecureTransferHandler(Blake3Service blake3Service, EncryptionService encryptionService) {
        this.blake3Service = blake3Service;
        this.encryptionService = encryptionService;
    }

    /**
     * Sends a file part using secure transfer (encrypted).
     * Offloads blocking I/O and CPU operations to the thread pool.
     *
     * @param connection the connection to send to
     * @param filePath   the file to read from
     * @param offset     offset in the file
     * @param length     length to read
     * @param messageId  the message ID
     * @param sessionKey the session key for encryption
     */
    public CompletableFuture<Void> sendFilePart(Connection connection, Path filePath, long offset, long length,
            int messageId, byte[] sessionKey) {
        return CompletableFuture.supplyAsync(() -> {
            try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {
                // 1. Read data into a buffer (No MapMode.READ_ONLY to avoid thrashing)
                ByteBuffer dataBuffer = ByteBuffer.allocate((int) length);
                int bytesRead = fileChannel.read(dataBuffer, offset);
                if (bytesRead != length) {
                    throw new IOException("Failed to read expected bytes from file");
                }
                dataBuffer.flip();
                byte[] rawData = dataBuffer.array();

                // 2. Calculate checksum
                String hash = blake3Service.hashBuffer(rawData);

                // 3. Encrypt the data
                byte[] encryptedData;
                try {
                    // AES-GCM adds IV (12 bytes) + Tag (16 bytes) overhead
                    encryptedData = encryptionService.encrypt(rawData, sessionKey);
                } catch (EncryptionException e) {
                    throw new IOException("Failed to encrypt file chunk", e);
                }

                // 4. Prepare the ChunkDataMessage with ENCRYPTED payload
                long totalSize = Files.size(filePath);
                ChunkDataMessage message = new ChunkDataMessage(
                        filePath.toString(),
                        offset,
                        encryptedData.length, // Payload size is now encrypted size
                        totalSize,
                        hash, // Hash of the PLAINTEXT data for integrity verification
                        encryptedData);

                // 5. Send message (Header + Encrypted Body)
                // Using generic sendMessage which handles serialization of header+body
                // Return the message future to be composed into the async chain
                return message;

            } catch (IOException e) {
                logger.error("Failed to send file part: {}", filePath, e);
                throw new CompletionException(e);
            }
        }, ThreadPoolManager.getInstance().getIoThreadPool())
                .thenCompose(message -> connection.sendMessage(message))
                .thenApply(v -> null);
    }
}
