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

import com.github.luben.zstd.Zstd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Zstandard implementation of CompressionService.
 * Uses high-performance zstd-jni library.
 */
public class ZstdCompressionService implements CompressionService {

    private static final Logger logger = LoggerFactory.getLogger(ZstdCompressionService.class);

    private int compressionLevel;

    /**
     * Creates a new ZstdCompressionService with default compression level.
     * Default level 3 offers a good balance between speed and ratio.
     */
    public ZstdCompressionService() {
        this(3);
    }

    /**
     * Creates a new ZstdCompressionService with specified compression level.
     *
     * @param compressionLevel the compression level (1-22)
     */
    public ZstdCompressionService(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    @Override
    public void setLevel(int level) {
        this.compressionLevel = level;
    }

    @Override
    public byte[] compress(byte[] data) throws IOException {
        try {
            return Zstd.compress(data, compressionLevel);
        } catch (Exception e) {
            logger.error("Failed to compress data", e);
            throw new IOException("Zstd compression failed", e);
        }
    }

    @Override
    public byte[] decompress(byte[] compressedData) throws IOException {
        try {
            // 1. Get content size
            long originalSize = Zstd.getFrameContentSize(compressedData);

            // 2. Handle specific Zstd return codes (hardcoded for compatibility)
            // -1 indicates the size is not known (e.g. streaming compression)
            if (originalSize == -1) {
                throw new IOException("Original size is unknown in Zstd header - cannot use array-based decompression");
            }
            // -2 indicates an error in the header/frame
            if (originalSize == -2) {
                throw new IOException("Invalid Zstd header or data");
            }

            // 3. Safety check: Java arrays are limited to 2GB (Integer.MAX_VALUE)
            if (originalSize > Integer.MAX_VALUE) {
                throw new IOException("Decompressed size (" + originalSize + ") exceeds Java array limit");
            }

            // 4. Decompress
            return Zstd.decompress(compressedData, (int) originalSize);

        } catch (Exception e) {
            logger.error("Failed to decompress data", e);
            // Preserve the original message if it's already an IOException
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Zstd decompression failed", e);
        }
    }

    @Override
    public byte[] compress(byte[] data, byte[] dictionary) throws IOException {
        try {
            long maxDstSize = Zstd.compressBound(data.length);
            if (maxDstSize > Integer.MAX_VALUE) {
                throw new IOException("Max compressed size exceeds Java array limit");
            }
            byte[] dst = new byte[(int) maxDstSize];
            long size = Zstd.compress(dst, data, dictionary, compressionLevel);
            if (Zstd.isError(size)) {
                throw new IOException("Zstd compression failed: " + Zstd.getErrorName(size));
            }
            return java.util.Arrays.copyOf(dst, (int) size);
        } catch (Exception e) {
            logger.error("Failed to compress data with dictionary", e);
            throw new IOException("Zstd dictionary compression failed", e);
        }
    }

    @Override
    public byte[] decompress(byte[] compressedData, byte[] dictionary) throws IOException {
        try {
            long originalSize = Zstd.getFrameContentSize(compressedData);
            if (originalSize == -1 || originalSize == -2) {
                // Try to estimate or just fail if unknown
                throw new IOException("Unknown content size in Zstd frame");
            }
            if (originalSize > Integer.MAX_VALUE) {
                throw new IOException("Decompressed size (" + originalSize + ") exceeds Java array limit");
            }

            int size = (int) originalSize;
            byte[] dst = new byte[size];

            long decompressedSize = Zstd.decompress(dst, compressedData, dictionary);
            if (Zstd.isError(decompressedSize)) {
                throw new IOException("Zstd decompression failed: " + Zstd.getErrorName(decompressedSize));
            }

            return dst;
        } catch (Exception e) {
            logger.error("Failed to decompress data with dictionary", e);
            throw new IOException("Zstd dictionary decompression failed", e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return "ZSTD";
    }
}
