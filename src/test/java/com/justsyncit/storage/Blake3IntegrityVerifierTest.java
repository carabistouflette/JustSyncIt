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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.HashingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Blake3IntegrityVerifier.
 */
class Blake3IntegrityVerifierTest {

    /** Mock BLAKE3 service. */
    @Mock
    private Blake3Service mockBlake3Service;

    /** Integrity verifier under test. */
    private Blake3IntegrityVerifier integrityVerifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        integrityVerifier = new Blake3IntegrityVerifier(mockBlake3Service);
    }

    @Test
    void testConstructorWithNullService() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new Blake3IntegrityVerifier(null));
    }

    @Test
    void testVerifyIntegrityWithValidData() throws HashingException, StorageIntegrityException {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "abcdef1234567890";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);

        // Act & Assert - should not throw
        integrityVerifier.verifyIntegrity(data, expectedHash);
    }

    @Test
    void testVerifyIntegrityWithNullData() {
        // Arrange
        String expectedHash = "abcdef1234567890";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.verifyIntegrity(null, expectedHash));
    }

    @Test
    void testVerifyIntegrityWithNullHash() {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.verifyIntegrity(data, null));
    }

    @Test
    void testVerifyIntegrityWithMismatchedHash() throws HashingException, StorageIntegrityException {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "expected_hash";
        String actualHash = "actual_hash";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(actualHash);

        // Act & Assert
        StorageIntegrityException exception = assertThrows(
                StorageIntegrityException.class,
                () -> integrityVerifier.verifyIntegrity(data, expectedHash)
        );

        assertEquals(
                "Integrity check failed. Expected: expected_hash, Actual: actual_hash",
                exception.getMessage()
        );
    }

    @Test
    void testCalculateHashWithValidData() throws HashingException {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "abcdef1234567890";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);

        // Act
        String result = integrityVerifier.calculateHash(data);

        // Assert
        assertEquals(expectedHash, result);
    }

    @Test
    void testCalculateHashWithNullData() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.calculateHash(null));
    }

    @Test
    void testValidateHashWithValidHash() {
        // Arrange
        String validHash = "abcdef1234567890";

        // Act & Assert - should not throw
        integrityVerifier.validateHash(validHash);
    }

    @Test
    void testValidateHashWithNullHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash(null));
    }

    @Test
    void testValidateHashWithEmptyHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash(""));
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash("   "));
    }

    @Test
    void testValidateHashWithWhitespaceOnlyHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash("\t\n\r"));
    }

    @Test
    void testVerifyIntegrityWithEmptyData() throws HashingException, StorageIntegrityException {
        // Arrange
        byte[] data = new byte[0];
        String expectedHash = "empty_hash";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);

        // Act & Assert - should not throw (empty data is valid)
        integrityVerifier.verifyIntegrity(data, expectedHash);
    }

    @Test
    void testCalculateHashWithEmptyData() throws HashingException {
        // Arrange
        byte[] data = new byte[0];
        String expectedHash = "empty_hash";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);

        // Act
        String result = integrityVerifier.calculateHash(data);

        // Assert
        assertEquals(expectedHash, result);
    }

    @Test
    void testVerifyIntegrityWithLargeData() throws HashingException, StorageIntegrityException {
        // Arrange
        byte[] data = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        String expectedHash = "large_data_hash";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);

        // Act & Assert - should not throw
        integrityVerifier.verifyIntegrity(data, expectedHash);
    }
}