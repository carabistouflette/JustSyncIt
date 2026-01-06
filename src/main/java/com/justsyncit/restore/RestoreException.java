package com.justsyncit.restore;

/**
 * Exception thrown when a restore operation fails.
 * Follows Single Responsibility Principle by specifically handling
 * restore-related errors.
 */
public class RestoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new RestoreException with the specified detail message.
     *
     * @param message the detail message
     */
    public RestoreException(String message) {
        super(message);
    }

    /**
     * Constructs a new RestoreException with the specified detail message and
     * cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public RestoreException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new RestoreException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public RestoreException(Throwable cause) {
        super(cause);
    }
}