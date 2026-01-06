package com.justsyncit.network.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;

/**
 * AES-256-GCM encryption service implementation.
 * 
 * <p>
 * Provides authenticated encryption with:
 * <ul>
 * <li>256-bit AES key</li>
 * <li>96-bit (12 byte) IV (recommended for GCM)</li>
 * <li>128-bit authentication tag</li>
 * </ul>
 * 
 * <p>
 * Output format: IV (12 bytes) || Ciphertext || Auth Tag (16 bytes)
 * 
 * <p>
 * Thread-safe: uses ThreadLocal cipher instances.
 */
public final class AesGcmEncryptionService implements EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BYTES = 32; // 256 bits
    private static final int IV_SIZE_BYTES = 12; // 96 bits (recommended for GCM)
    private static final int TAG_SIZE_BITS = 128; // 128-bit auth tag

    private final SecureRandom secureRandom;

    /**
     * Thread-local cipher for thread safety without synchronization overhead.
     */
    private final ThreadLocal<Cipher> cipherThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AES-GCM cipher", e);
        }
    });

    /**
     * Constructs a new AES-GCM encryption service.
     */
    public AesGcmEncryptionService() {
        this.secureRandom = new SecureRandom();
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] key) throws EncryptionException {
        return encrypt(plaintext, key, null);
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] key, byte[] associatedData)
            throws EncryptionException {
        // Generate random IV for standard encryption
        byte[] iv = new byte[IV_SIZE_BYTES];
        secureRandom.nextBytes(iv);

        return encryptInternal(plaintext, key, iv, associatedData);
    }

    @Override
    public byte[] encryptDeterministic(byte[] plaintext, byte[] key, byte[] ivSeed, byte[] associatedData)
            throws EncryptionException {
        if (ivSeed == null || ivSeed.length < IV_SIZE_BYTES) {
            throw new EncryptionException("IV seed must be at least " + IV_SIZE_BYTES + " bytes");
        }

        // Derive IV from seed using HKDF (HMAC-based Key Derivation Function)
        // This ensures cryptographically strong derivation and better distribution than
        // simple truncation
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        // Use the seed as IKM (Input Key Material).
        // We use a constant info string to bind this to the specific context of IV
        // generation.
        // No salt is strictly required if the seed is already high entropy (which it
        // should be),
        // but passing null satisfies the API.
        hkdf.init(new HKDFParameters(ivSeed, null, "JustSyncIt-AES-GCM-IV".getBytes(StandardCharsets.UTF_8)));

        byte[] iv = new byte[IV_SIZE_BYTES];
        hkdf.generateBytes(iv, 0, IV_SIZE_BYTES);

        return encryptInternal(plaintext, key, iv, associatedData);
    }

    private byte[] encryptInternal(byte[] plaintext, byte[] key, byte[] iv, byte[] associatedData)
            throws EncryptionException {
        validateKey(key);

        byte[] keyCopy = null;
        try {
            // Create a copy of the key for internal use
            keyCopy = Arrays.copyOf(key, key.length);

            // Create new Cipher instance to avoid IV reuse issues with GCM
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyCopy, ALGORITHM);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // Add associated data if provided
            if (associatedData != null && associatedData.length > 0) {
                cipher.updateAAD(associatedData);
            }

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Prepend IV to ciphertext: IV || Ciphertext || Tag
            byte[] result = new byte[IV_SIZE_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_SIZE_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_SIZE_BYTES, ciphertext.length);

            return result;

        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        } finally {
            // Zero out sensitive key material
            if (keyCopy != null) {
                Arrays.fill(keyCopy, (byte) 0);
            }
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] key) throws EncryptionException {
        return decrypt(ciphertext, key, null);
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] key, byte[] associatedData)
            throws EncryptionException {
        validateKey(key);
        validateCiphertext(ciphertext);

        byte[] iv = null;
        byte[] encryptedData = null;
        byte[] keyCopy = null;
        try {
            // Extract IV from beginning of ciphertext
            iv = Arrays.copyOfRange(ciphertext, 0, IV_SIZE_BYTES);
            encryptedData = Arrays.copyOfRange(ciphertext, IV_SIZE_BYTES, ciphertext.length);

            // Create a copy of the key for internal use
            keyCopy = Arrays.copyOf(key, key.length);

            Cipher cipher = cipherThreadLocal.get();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyCopy, ALGORITHM);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Add associated data if provided
            if (associatedData != null && associatedData.length > 0) {
                cipher.updateAAD(associatedData);
            }

            return cipher.doFinal(encryptedData);

        } catch (javax.crypto.AEADBadTagException e) {
            throw new EncryptionException("Authentication failed: data may have been tampered", e);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        } finally {
            // Zero out sensitive key material
            if (keyCopy != null) {
                Arrays.fill(keyCopy, (byte) 0);
            }
            if (iv != null) {
                Arrays.fill(iv, (byte) 0);
            }
            if (encryptedData != null) {
                Arrays.fill(encryptedData, (byte) 0);
            }
        }
    }

    @Override
    public int getKeySize() {
        return KEY_SIZE_BYTES;
    }

    @Override
    public int getIvSize() {
        return IV_SIZE_BYTES;
    }

    @Override
    public String getAlgorithmName() {
        return "AES-256-GCM";
    }

    private void validateKey(byte[] key) throws EncryptionException {
        if (key == null) {
            throw new EncryptionException("Key cannot be null");
        }
        if (key.length != KEY_SIZE_BYTES) {
            throw new EncryptionException(
                    "Invalid key size: expected " + KEY_SIZE_BYTES + " bytes, got " + key.length);
        }
    }

    private void validateCiphertext(byte[] ciphertext) throws EncryptionException {
        if (ciphertext == null) {
            throw new EncryptionException("Ciphertext cannot be null");
        }
        // Minimum: IV (12 bytes) + auth tag (16 bytes) = 28 bytes for empty plaintext
        int minSize = IV_SIZE_BYTES + (TAG_SIZE_BITS / 8);
        if (ciphertext.length < minSize) {
            throw new EncryptionException(
                    "Ciphertext too short: minimum " + minSize + " bytes required");
        }
    }
}
