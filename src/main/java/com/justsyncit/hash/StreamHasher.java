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

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for stream hashing operations.
 * Follows Interface Segregation Principle by focusing only on stream operations.
 */
public interface StreamHasher {

    /**
     * Hashes the content of an InputStream.
     * The stream will be fully consumed but not closed.
     *
     * @param inputStream the input stream to hash
     * @return the hash as a hexadecimal string
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws IllegalArgumentException if the inputStream is null
     */
    String hashStream(InputStream inputStream) throws IOException;
}