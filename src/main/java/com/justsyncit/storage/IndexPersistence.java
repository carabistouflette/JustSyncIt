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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles persistence of the chunk index to and from disk.
 * Uses a simple text format with hash|relative_path entries.
 */
public final class IndexPersistence {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(IndexPersistence.class);

    /** The file used to store the index. */
    private final Path indexFile;
    /** The directory where chunks are stored. */
    private final Path storageDirectory;

    /**
     * Creates a new IndexPersistence.
     *
     * @param storageDirectory the directory where chunks are stored
     * @param indexFile the file to use for the index
     * @throws IllegalArgumentException if storageDirectory or indexFile is null
     */
    public IndexPersistence(Path storageDirectory, Path indexFile) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("Storage directory cannot be null");
        }
        if (indexFile == null) {
            throw new IllegalArgumentException("Index file cannot be null");
        }
        this.storageDirectory = storageDirectory;
        this.indexFile = indexFile;
    }

    /**
     * Loads the index from disk.
     *
     * @return a map of hashes to file paths
     * @throws IOException if an I/O error occurs
     */
    public Map<String, Path> loadIndex() throws IOException {
        Map<String, Path> indexMap = new HashMap<>();

        if (!Files.exists(indexFile)) {
            logger.debug("Index file does not exist, starting with empty index");
            return indexMap;
        }

        try (BufferedReader reader = Files.newBufferedReader(indexFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2) {
                        String hash = parts[0].trim();
                        String relativePath = parts[1].trim();
                        Path fullPath = storageDirectory.resolve(relativePath);
                        indexMap.put(hash, fullPath);
                    }
                }
            }
        }

        logger.debug("Loaded {} chunks from index", indexMap.size());
        return indexMap;
    }

    /**
     * Saves the index to disk.
     *
     * @param indexMap the map of hashes to file paths to save
     * @throws IOException if an I/O error occurs
     */
    public void saveIndex(Map<String, Path> indexMap) throws IOException {
        // Write to temporary file first, then atomically move
        Path tempFile = indexFile.resolveSibling(indexFile.getFileName() + ".tmp");

        try (BufferedWriter writer = Files.newBufferedWriter(tempFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            for (Map.Entry<String, Path> entry : indexMap.entrySet()) {
                String hash = entry.getKey();
                Path fullPath = entry.getValue();
                Path relativePath = storageDirectory.relativize(fullPath);
                writer.write(hash + "|" + relativePath.toString());
                writer.newLine();
            }
        }

        // Atomically replace the old index file
        Files.move(tempFile, indexFile,
                  StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING);

        logger.debug("Saved {} chunks to index", indexMap.size());
    }

    /**
     * Ensures the necessary directories exist for index persistence.
     *
     * @throws IOException if directories cannot be created
     */
    public void ensureDirectoriesExist() throws IOException {
        Files.createDirectories(storageDirectory);
        Path indexParentDir = indexFile.getParent();
        if (indexParentDir != null) {
            Files.createDirectories(indexParentDir);
        }
    }
}