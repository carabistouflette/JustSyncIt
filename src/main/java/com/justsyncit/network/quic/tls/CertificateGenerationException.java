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

package com.justsyncit.network.quic.tls;

/**
 * Exception thrown when certificate generation fails.
 * Provides specific error handling for certificate operations.
 */
public class CertificateGenerationException extends Exception {

    /**
     * Creates a new certificate generation exception.
     *
     * @param message the error message
     */
    public CertificateGenerationException(String message) {
        super(message);
    }

    /**
     * Creates a new certificate generation exception with cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public CertificateGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new certificate generation exception from cause.
     *
     * @param cause the underlying cause
     */
    public CertificateGenerationException(Throwable cause) {
        super(cause);
    }
}