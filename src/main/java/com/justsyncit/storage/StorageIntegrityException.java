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

package com.justsyncit.storage;

/**
 * Exception thrown when storage integrity verification fails.
 * This occurs when retrieved data doesn't match its expected hash.
 */
public class StorageIntegrityException extends Exception {

    /**
     * Constructs a new StorageIntegrityException with the specified detail message.
     *
     * @param message the detail message
     */
    public StorageIntegrityException(String message) {
        super(message);
    }

    /**
     * Constructs a new StorageIntegrityException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
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