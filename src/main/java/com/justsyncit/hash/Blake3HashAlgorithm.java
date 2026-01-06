package com.justsyncit.hash;

import org.bouncycastle.crypto.digests.Blake3Digest;

import java.util.Optional;

/**
 * BLAKE3 hash algorithm implementation using Bouncy Castle.
 * 
 * <p>
 * This class implements the HashAlgorithm interface using the robust Bouncy
 * Castle
 * implementation of BLAKE3. This replaces the previous placeholder
 * implementation.
 * </p>
 */
public final class Blake3HashAlgorithm implements HashAlgorithm {

    private final Blake3Digest digest;
    private static final int HASH_LENGTH = 32; // Default 256 bits
    private static final int BLOCK_SIZE = 64;
    private static final int SECURITY_LEVEL = 128; // 128-bit security against collision

    /**
     * Creates a new BLAKE3 hash algorithm instance.
     */
    private Blake3HashAlgorithm() {
        this.digest = new Blake3Digest(HASH_LENGTH * 8); // Size in bits
    }

    /**
     * Creates a new BLAKE3 hash algorithm instance.
     *
     * @return a new Blake3HashAlgorithm instance
     */
    public static Blake3HashAlgorithm create() {
        return new Blake3HashAlgorithm();
    }

    @Override
    public void update(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        digest.update(data, 0, data.length);
    }

    @Override
    public void update(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset or length");
        }
        digest.update(data, offset, length);
    }

    @Override
    public byte[] digest() {
        byte[] result = new byte[HASH_LENGTH];
        digest.doFinal(result, 0);
        return result;
    }

    @Override
    public void reset() {
        digest.reset();
    }

    @Override
    public String getAlgorithmName() {
        return "BLAKE3";
    }

    @Override
    public int getHashLength() {
        return HASH_LENGTH;
    }

    @Override
    public Optional<Integer> getBlockSize() {
        return Optional.of(BLOCK_SIZE);
    }

    @Override
    public Optional<Integer> getSecurityLevel() {
        return Optional.of(SECURITY_LEVEL);
    }

    @Override
    public boolean isThreadSafe() {
        return false; // Bouncy Castle digests are generally not thread-safe
    }

    @Override
    public HashAlgorithm createInstance() {
        return create();
    }
}
