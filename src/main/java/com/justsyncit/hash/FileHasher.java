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
import java.nio.file.Path;

/**
 * Interface for file hashing operations.
 * Follows Interface Segregation Principle by focusing only on file operations.
 */
public interface FileHasher {

    /**
     * Hashes the entire content of a file.
     *
     * @param filePath the path to the file to hash
     * @return the hash as a hexadecimal string
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if the file path is invalid or file doesn't exist
     */
    String hashFile(Path filePath) throws IOException;
}