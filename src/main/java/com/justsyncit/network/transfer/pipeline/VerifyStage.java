package com.justsyncit.network.transfer.pipeline;

import com.justsyncit.hash.Blake3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Pipeline stage for verifying chunk data integrity.
 */
public class VerifyStage {

    private static final Logger logger = LoggerFactory.getLogger(VerifyStage.class);
    private final ExecutorService executor;
    private final Blake3Service blake3Service;
    private final String expectedChecksum;
    private final long chunkOffset;

    public VerifyStage(ExecutorService executor, Blake3Service blake3Service, String expectedChecksum,
            long chunkOffset) {
        this.executor = executor;
        this.blake3Service = blake3Service;
        this.expectedChecksum = expectedChecksum;
        this.chunkOffset = chunkOffset;
    }

    public CompletableFuture<byte[]> process(byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            String actualChecksum = computeChecksum(data);
            if (!expectedChecksum.equals(actualChecksum)) {
                throw new java.util.concurrent.CompletionException(
                        new IOException("Checksum mismatch for chunk " + chunkOffset));
            }
            return data;
        }, executor);
    }

    private String computeChecksum(byte[] data) {
        if (blake3Service != null) {
            try {
                return blake3Service.hashBuffer(data);
            } catch (Exception e) {
                logger.error("Failed to compute BLAKE3 checksum, falling back to SHA-256", e);
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data);
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not found - cannot verify data integrity", e);
            throw new RuntimeException("Secure hashing algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
