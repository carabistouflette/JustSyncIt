/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.compression;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Interface for compression services.
 * Defines methods for compressing and decompressing data.
 */
public interface CompressionService {

    /**
     * Compresses the given data.
     *
     * @param data the data to compress
     * @return the compressed data
     * @throws IOException if an error occurs during compression
     */
    byte[] compress(byte[] data) throws IOException;

    /**
     * Decompresses the given data.
     *
     * @param compressedData the compressed data
     * @return the decompressed data
     * @throws IOException if an error occurs during decompression
     */
    byte[] decompress(byte[] compressedData) throws IOException;

    /**
     * Helper to compress a ByteBuffer.
     * 
     * @param data the buffer to compress
     * @return the compressed data as byte hash
     * @throws IOException
     */
    default byte[] compress(ByteBuffer data) throws IOException {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        return compress(bytes);
    }

    /**
     * Compresses the given data using a dictionary.
     * 
     * @param data       the data to compress
     * @param dictionary the dictionary to use
     * @return the compressed data
     * @throws IOException
     */
    default byte[] compress(byte[] data, byte[] dictionary) throws IOException {
        return compress(data); // Default fallback if not supported
    }

    /**
     * Decompresses the given data using a dictionary.
     * 
     * @param compressedData the compressed data
     * @param dictionary     the dictionary to use
     * @return the decompressed data
     * @throws IOException
     */
    default byte[] decompress(byte[] compressedData, byte[] dictionary) throws IOException {
        return decompress(compressedData); // Default fallback if not supported
    }

    /**
     * Sets the compression level.
     * 
     * @param level the compression level (1-22)
     */
    void setLevel(int level);

    /**
     * Gets the compression algorithm name.
     * 
     * @return the algorithm name (e.g. "ZSTD")
     */
    String getAlgorithmName();
}
