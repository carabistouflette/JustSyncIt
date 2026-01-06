package com.justsyncit.backup;

/**
 * Exception thrown when a backup operation fails.
 * Follows Single Responsibility Principle by specifically handling
 * backup-related errors.
 */
public class BackupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum ErrorCode {
        IO_ERROR,
        CONFIGURATION_ERROR,
        PERMISSION_DENIED,
        UNKNOWN
    }

    private final ErrorCode errorCode;

    /**
     * Constructs a new BackupException with the specified detail message.
     *
     * @param message the detail message
     */
    public BackupException(String message) {
        this(message, ErrorCode.UNKNOWN);
    }

    /**
     * Constructs a new BackupException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public BackupException(String message, Throwable cause) {
        this(message, cause, ErrorCode.UNKNOWN);
    }

    /**
     * Constructs a new BackupException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public BackupException(Throwable cause) {
        this(cause, ErrorCode.UNKNOWN);
    }

    /**
     * Constructs a new BackupException with the specified detail message and error
     * code.
     *
     * @param message   the detail message
     * @param errorCode the specific error code
     */
    public BackupException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new BackupException with the specified detail message, cause,
     * and error code.
     *
     * @param message   the detail message
     * @param cause     the cause of this exception
     * @param errorCode the specific error code
     */
    public BackupException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new BackupException with the specified cause and error code.
     *
     * @param cause     the cause of this exception
     * @param errorCode the specific error code
     */
    public BackupException(Throwable cause, ErrorCode errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code associated with this exception.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}