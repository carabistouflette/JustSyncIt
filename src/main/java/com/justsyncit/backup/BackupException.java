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