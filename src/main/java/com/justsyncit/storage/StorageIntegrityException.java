package com.justsyncit.storage;

/**
 * Exception thrown when storage integrity verification fails.
 * This occurs when retrieved data doesn't match its expected hash.
 */
public class StorageIntegrityException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new StorageIntegrityException with the specified detail message.
     *
     * @param message the detail message
     */
    public StorageIntegrityException(String message) {
        super(message);
    }

    /**
     * Constructs a new StorageIntegrityException with the specified detail message
     * and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public StorageIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new StorageIntegrityException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public StorageIntegrityException(Throwable cause) {
        super(cause);
    }
}