/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.hash;

/**
 * Exception thrown when hashing operations fail.
 * Follows Single Responsibility Principle by specifically handling
 * hashing-related errors with proper categorization and context.
 *
 * <p>This exception is thread-safe and immutable, making it safe to use
 * across multiple threads without additional synchronization.</p>
 */
public class HashingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Enumeration of error codes for different types of hashing failures.
     * This allows for more granular error handling and recovery strategies.
     */
    public enum ErrorCode {
        /** Algorithm-specific failure during hash computation */
        ALGORITHM_FAILURE,
        /** I/O error while reading data for hashing */
        IO_ERROR,
        /** Insufficient memory for hashing operations */
        MEMORY_ERROR,
        /** Invalid input data provided for hashing */
        INVALID_INPUT,
        /** Invalid hashing configuration or parameters */
        CONFIGURATION_ERROR,
        /** Unknown or unexpected error */
        UNKNOWN
    }

    private final ErrorCode errorCode;
    private final String hashAlgorithm;
    private final String operationContext;

    /**
     * Creates a new HashingException with the specified detail message.
     *
     * @param message the detail message
     */
    public HashingException(String message) {
        this(message, ErrorCode.UNKNOWN);
    }

    /**
     * Creates a new HashingException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public HashingException(String message, Throwable cause) {
        this(message, cause, ErrorCode.UNKNOWN);
    }

    /**
     * Creates a new HashingException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public HashingException(Throwable cause) {
        this(cause, ErrorCode.UNKNOWN);
    }

    /**
     * Creates a new HashingException with the specified detail message and error code.
     *
     * @param message   the detail message
     * @param errorCode the specific error code
     */
    public HashingException(String message, ErrorCode errorCode) {
        this(message, errorCode, null, null);
    }

    /**
     * Creates a new HashingException with the specified detail message, cause,
     * and error code.
     *
     * @param message   the detail message
     * @param cause     the cause of this exception
     * @param errorCode the specific error code
     */
    public HashingException(String message, Throwable cause, ErrorCode errorCode) {
        this(message, errorCode, null, null, cause);
    }

    /**
     * Creates a new HashingException with the specified cause and error code.
     *
     * @param cause     the cause of this exception
     * @param errorCode the specific error code
     */
    public HashingException(Throwable cause, ErrorCode errorCode) {
        this(cause.getMessage(), errorCode, null, null, cause);
    }

    /**
     * Creates a new HashingException with the specified detail message, error code,
     * hash algorithm, and operation context.
     *
     * @param message          the detail message
     * @param errorCode        the specific error code
     * @param hashAlgorithm    the hashing algorithm being used (may be null)
     * @param operationContext the context of the operation that failed (may be null)
     */
    public HashingException(String message, ErrorCode errorCode, String hashAlgorithm, String operationContext) {
        this(message, errorCode, hashAlgorithm, operationContext, null);
    }

    /**
     * Creates a new HashingException with the specified detail message, error code,
     * hash algorithm, operation context, and cause.
     *
     * @param message          the detail message
     * @param errorCode        the specific error code
     * @param hashAlgorithm    the hashing algorithm being used (may be null)
     * @param operationContext the context of the operation that failed (may be null)
     * @param cause            the cause of this exception (may be null)
     */
    public HashingException(String message, ErrorCode errorCode, String hashAlgorithm,
                           String operationContext, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode != null ? errorCode : ErrorCode.UNKNOWN;
        this.hashAlgorithm = hashAlgorithm;
        this.operationContext = operationContext;
    }

    /**
     * Returns the error code associated with this exception.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the hash algorithm that was being used when the error occurred.
     *
     * @return the hash algorithm name, or null if not available
     */
    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    /**
     * Returns the context of the operation that failed.
     * This might include file paths, data sizes, or other relevant information.
     *
     * @return the operation context, or null if not available
     */
    public String getOperationContext() {
        return operationContext;
    }

    /**
     * Returns a detailed string representation of this exception including
     * error code, algorithm, and context information.
     *
     * @return a detailed string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append(" [errorCode=").append(errorCode);
        
        if (hashAlgorithm != null) {
            sb.append(", algorithm=").append(hashAlgorithm);
        }
        
        if (operationContext != null) {
            sb.append(", context=").append(operationContext);
        }
        
        sb.append(']');
        return sb.toString();
    }
}