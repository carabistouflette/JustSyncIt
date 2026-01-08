package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.network.encryption.EncryptionException;
import com.justsyncit.network.encryption.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Pipeline stage that decrypts chunk data using AES-256-GCM.
 * 
 * <p>
 * This stage should be placed before decompression in the receive pipeline.
 * It uses the transfer ID as associated data for AEAD verification.
 */
public final class DecryptStage implements PipelineStage<byte[], byte[]> {

    private static final Logger logger = LoggerFactory.getLogger(DecryptStage.class);

    private final ExecutorService executor;
    private final EncryptionService encryptionService;
    private final byte[] encryptionKey;
    private final String transferId;
    private final boolean enabled;

    /**
     * Creates a decryption stage.
     *
     * @param executor          the executor for async operations
     * @param encryptionService the encryption service to use
     * @param encryptionKey     the encryption key (256-bit for AES-256)
     * @param transferId        the transfer ID for AEAD verification
     * @param enabled           whether decryption is enabled
     */
    public DecryptStage(
            ExecutorService executor,
            EncryptionService encryptionService,
            byte[] encryptionKey,
            String transferId,
            boolean enabled) {
        this.executor = executor;
        this.encryptionService = encryptionService;
        this.encryptionKey = encryptionKey != null ? encryptionKey.clone() : null;
        this.transferId = transferId;
        this.enabled = enabled;
    }

    @Override
    public CompletableFuture<byte[]> process(byte[] input) {
        if (!enabled || encryptionService == null || encryptionKey == null || input == null) {
            // Passthrough if disabled or null input
            return CompletableFuture.completedFuture(input);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use transfer ID as associated data for AEAD verification
                byte[] associatedData = transferId.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8);

                // Decrypt (IV is included in the input prefix by AesGcmEncryptionService)
                byte[] plaintext = encryptionService.decrypt(
                        input, encryptionKey, associatedData);

                logger.debug("Decrypted chunk for transfer {}, size: {} -> {}",
                        transferId, input.length, plaintext.length);

                return plaintext;

            } catch (EncryptionException e) {
                logger.error("Decryption failed for transfer {}", transferId, e);
                throw new RuntimeException("Decryption failed", e);
            }
        }, executor);
    }

    @Override
    public String getName() {
        return "DecryptStage";
    }

    @Override
    public void shutdown() {
        // Clear encryption key from memory (best effort)
        if (encryptionKey != null) {
            java.util.Arrays.fill(encryptionKey, (byte) 0);
        }
    }
}
