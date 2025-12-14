/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.encryption;

/**
 * Exception thrown when encryption or decryption operations fail.
 */
public class EncryptionException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new encryption exception with the specified message.
     *
     * @param message the detail message
     */
    public EncryptionException(String message) {
        super(message);
    }

    /**
     * Constructs a new encryption exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
