package com.justsyncit.network.encryption;

import java.nio.ByteBuffer;

/**
 * Interface for encryption services providing authenticated encryption.
 * Supports AEAD (Authenticated Encryption with Associated Data).
 */
public interface EncryptionService {

    /**
     * Encrypts data with authenticated encryption.
     *
     * @param plaintext the data to encrypt
     * @param key       the encryption key (must be appropriate size for algorithm)
     * @return encrypted data with IV prepended
     * @throws EncryptionException if encryption fails
     */
    byte[] encrypt(byte[] plaintext, byte[] key) throws EncryptionException;

    /**
     * Encrypts data from plaintext buffer to ciphertext buffer.
     *
     * @param plaintext  the data to encrypt
     * @param ciphertext the buffer to write encrypted data to (must have sufficient
     *                   capacity)
     * @param key        the encryption key
     * @return number of bytes written to ciphertext buffer
     * @throws EncryptionException if encryption fails
     */
    int encrypt(ByteBuffer plaintext, ByteBuffer ciphertext, byte[] key) throws EncryptionException;

    /**
     * Encrypts data with authenticated encryption and associated data.
     *
     * @param plaintext      the data to encrypt
     * @param key            the encryption key
     * @param associatedData additional authenticated data (not encrypted, but
     *                       authenticated)
     * @return encrypted data with IV prepended
     * @throws EncryptionException if encryption fails
     */
    byte[] encrypt(byte[] plaintext, byte[] key, byte[] associatedData) throws EncryptionException;

    /**
     * Decrypts data with authentication verification.
     *
     * @param ciphertext the encrypted data (with IV prepended)
     * @param key        the decryption key
     * @return decrypted plaintext
     * @throws EncryptionException if decryption or authentication fails
     */
    byte[] decrypt(byte[] ciphertext, byte[] key) throws EncryptionException;

    /**
     * Decrypts data from ciphertext buffer to plaintext buffer.
     *
     * @param ciphertext the encrypted data
     * @param plaintext  the buffer to write decrypted data to
     * @param key        the decryption key
     * @return number of bytes written to plaintext buffer
     * @throws EncryptionException if decryption fails
     */
    int decrypt(ByteBuffer ciphertext, ByteBuffer plaintext, byte[] key) throws EncryptionException;

    /**
     * Decrypts data with authentication and associated data verification.
     *
     * @param ciphertext     the encrypted data (with IV prepended)
     * @param key            the decryption key
     * @param associatedData the associated data that was used during encryption
     * @return decrypted plaintext
     * @throws EncryptionException if decryption or authentication fails
     */
    byte[] decrypt(byte[] ciphertext, byte[] key, byte[] associatedData) throws EncryptionException;

    /**
     * Encrypts data deterministically using a seed for IV generation.
     * This is required for deduplication, as identical plaintext + seed will
     * produce identical ciphertext.
     *
     * @param plaintext      the data to encrypt
     * @param key            the encryption key
     * @param ivSeed         the seed for generating the IV (e.g., hash of
     *                       plaintext)
     * @param associatedData additional authenticated data
     * @return encrypted data with IV prepended
     * @throws EncryptionException if encryption fails
     */
    byte[] encryptDeterministic(byte[] plaintext, byte[] key, byte[] ivSeed, byte[] associatedData)
            throws EncryptionException;

    /**
     * Gets the required key size in bytes.
     *
     * @return key size in bytes
     */
    int getKeySize();

    /**
     * Gets the IV size in bytes.
     *
     * @return IV size in bytes
     */
    int getIvSize();

    /**
     * Gets the algorithm name.
     *
     * @return algorithm identifier
     */
    String getAlgorithmName();
}
