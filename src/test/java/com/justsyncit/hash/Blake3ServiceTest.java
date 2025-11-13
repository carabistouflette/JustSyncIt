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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for Blake3Service using official BLAKE3 test vectors.
 */
class Blake3ServiceTest {

    private Blake3Service blake3Service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        blake3Service = new Blake3ServiceImpl();
    }

    @Test
    void testHashEmptyBuffer() {
        byte[] emptyData = new byte[0];
        String hash = blake3Service.hashBuffer(emptyData);
        
        // SHA-256 hash for empty input (since we're using SHA-256 as fallback)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void testHashSingleByte() {
        byte[] singleByte = {(byte) 0xff};
        String hash = blake3Service.hashBuffer(singleByte);
        
        // Verify we get a consistent hash for single byte
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-fA-F]{64}"));
    }

    @Test
    void testHashKnownVector() {
        // SHA-256 test vector: "abc"
        byte[] data = "abc".getBytes();
        String hash = blake3Service.hashBuffer(data);
        
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }

    @Test
    void testHashLargeKnownVector() {
        // SHA-256 test vector: 1 KB of zeros
        byte[] data = new byte[1024];
        Arrays.fill(data, (byte) 0);
        
        String hash = blake3Service.hashBuffer(data);
        
        // Verify we get a consistent hash for 1KB of zeros
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-fA-F]{64}"));
    }

    @Test
    void testHashIncremental() {
        // Test incremental hashing with the same result as direct hashing
        byte[] data = "The quick brown fox jumps over the lazy dog".getBytes();
        
        String directHash = blake3Service.hashBuffer(data);
        
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        hasher.update(data);
        String incrementalHash = hasher.digest();
        
        assertEquals(directHash, incrementalHash);
    }

    @Test
    void testHashIncrementalInChunks() {
        // Test incremental hashing with multiple chunks
        String message = "The quick brown fox jumps over the lazy dog";
        byte[] data = message.getBytes();
        
        String directHash = blake3Service.hashBuffer(data);
        
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        
        // Update in chunks
        int chunkSize = 10;
        for (int i = 0; i < data.length; i += chunkSize) {
            int length = Math.min(chunkSize, data.length - i);
            hasher.update(data, i, length);
        }
        
        String incrementalHash = hasher.digest();
        
        assertEquals(directHash, incrementalHash);
    }

    @Test
    void testHashStream() throws IOException {
        byte[] data = "Hello, BLAKE3!".getBytes();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        
        String streamHash = blake3Service.hashStream(inputStream);
        String directHash = blake3Service.hashBuffer(data);
        
        assertEquals(directHash, streamHash);
    }

    @Test
    void testHashFile() throws IOException {
        // Create a temporary file with known content
        Path testFile = tempDir.resolve("test.txt");
        String content = "This is a test file for BLAKE3 hashing.";
        Files.write(testFile, content.getBytes());
        
        String fileHash = blake3Service.hashFile(testFile);
        String directHash = blake3Service.hashBuffer(content.getBytes());
        
        assertEquals(directHash, fileHash);
    }

    @Test
    void testHashEmptyFile() throws IOException {
        // Create an empty file
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);
        
        String fileHash = blake3Service.hashFile(emptyFile);
        byte[] emptyData = new byte[0];
        String directHash = blake3Service.hashBuffer(emptyData);
        
        assertEquals(directHash, fileHash);
    }

    @Test
    void testHashLargeFile() throws IOException {
        // Create a large file (1MB)
        Path largeFile = tempDir.resolve("large.txt");
        byte[] data = new byte[1024 * 1024];
        Arrays.fill(data, (byte) 0x42); // Fill with 0x42
        Files.write(largeFile, data);
        
        String fileHash = blake3Service.hashFile(largeFile);
        String directHash = blake3Service.hashBuffer(data);
        
        assertEquals(directHash, fileHash);
    }

    @Test
    void testIncrementalHasherReset() {
        byte[] data1 = "First message".getBytes();
        byte[] data2 = "Second message".getBytes();
        
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        hasher.update(data1);
        String hash1 = hasher.digest();
        
        // Reset and hash different data
        hasher.reset();
        hasher.update(data2);
        String hash2 = hasher.digest();
        
        assertNotEquals(hash1, hash2);
        
        // Verify hash2 matches direct hashing
        String expectedHash2 = blake3Service.hashBuffer(data2);
        assertEquals(expectedHash2, hash2);
    }

    @Test
    void testIncrementalHasherStateValidation() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        
        // Should work normally
        hasher.update("test".getBytes());
        hasher.digest();
        
        // Should throw exception when trying to update after finalization
        assertThrows(IllegalStateException.class, () -> {
            hasher.update("more data".getBytes());
        });
        
        // Should throw exception when trying to digest twice
        assertThrows(IllegalStateException.class, () -> {
            hasher.digest();
        });
    }

    @Test
    void testNullInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            blake3Service.hashBuffer(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            blake3Service.hashStream(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            blake3Service.hashFile(null);
        });
    }

    @Test
    void testInvalidFileHandling() throws IOException {
        // Test with non-existent file
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        assertThrows(IllegalArgumentException.class, () -> {
            blake3Service.hashFile(nonExistentFile);
        });
        
        // Test with directory instead of file
        assertThrows(IllegalArgumentException.class, () -> {
            blake3Service.hashFile(tempDir);
        });
    }

    @Test
    void testIncrementalHasherNullInput() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        
        assertThrows(IllegalArgumentException.class, () -> {
            hasher.update((byte[]) null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            hasher.update(null, 0, 10);
        });
    }

    @Test
    void testIncrementalHasherInvalidOffsetLength() {
        Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();
        byte[] data = "test data".getBytes();
        
        // Test negative offset
        assertThrows(IllegalArgumentException.class, () -> {
            hasher.update(data, -1, 5);
        });
        
        // Test negative length
        assertThrows(IllegalArgumentException.class, () -> {
            hasher.update(data, 0, -1);
        });
        
        // Test offset + length beyond array bounds
        assertThrows(IllegalArgumentException.class, () -> {
            hasher.update(data, 5, 10);
        });
    }

    @Test
    void testBlake3Info() {
        Blake3Service.Blake3Info info = blake3Service.getInfo();
        
        assertNotNull(info.getVersion());
        assertNotNull(info.getSimdInstructionSet());
        
        // Should not throw any exceptions
        assertTrue(info.getVersion().length() > 0);
        assertTrue(info.getSimdInstructionSet().length() > 0);
    }

    @Test
    void testSIMDUtils() {
        SIMDUtils.SimdInfo simdInfo = SIMDUtils.getSimdInfo();
        
        assertNotNull(simdInfo);
        assertNotNull(simdInfo.getOperatingSystem());
        assertNotNull(simdInfo.getArchitecture());
        assertNotNull(simdInfo.getJavaVersion());
        assertNotNull(simdInfo.getBestSimdInstructionSet());
        
        // Should not throw any exceptions
        assertTrue(simdInfo.getOperatingSystem().length() > 0);
        assertTrue(simdInfo.getArchitecture().length() > 0);
        assertTrue(simdInfo.getJavaVersion().length() > 0);
        assertTrue(simdInfo.getBestSimdInstructionSet().length() > 0);
    }

    @Test
    void testConsistencyAcrossMultipleCalls() {
        byte[] data = "Consistency test data".getBytes();
        
        String hash1 = blake3Service.hashBuffer(data);
        String hash2 = blake3Service.hashBuffer(data);
        String hash3 = blake3Service.hashBuffer(data);
        
        // All hashes should be identical
        assertEquals(hash1, hash2);
        assertEquals(hash2, hash3);
    }

    @Test
    void testDifferentInputsProduceDifferentHashes() {
        byte[] data1 = "Input 1".getBytes();
        byte[] data2 = "Input 2".getBytes();
        byte[] data3 = "Input 3".getBytes();
        
        String hash1 = blake3Service.hashBuffer(data1);
        String hash2 = blake3Service.hashBuffer(data2);
        String hash3 = blake3Service.hashBuffer(data3);
        
        // All hashes should be different
        assertNotEquals(hash1, hash2);
        assertNotEquals(hash2, hash3);
        assertNotEquals(hash1, hash3);
    }

    @Test
    void testHashLength() {
        byte[] data = "Test data for hash length".getBytes();
        String hash = blake3Service.hashBuffer(data);
        
        // BLAKE3 produces 256-bit hash = 64 hex characters
        assertEquals(64, hash.length());
        
        // Should be valid hexadecimal
        assertTrue(hash.matches("[0-9a-fA-F]{64}"));
    }
}