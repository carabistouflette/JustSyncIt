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
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.util.concurrent.TimeUnit;

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
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testConstructorWithNullService() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new Blake3IntegrityVerifier(null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testVerifyIntegrityWithValidData() throws HashingException, StorageIntegrityException {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expectedHash = "abcdef1234567890";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);

        // Act & Assert - should not throw
        integrityVerifier.verifyIntegrity(data, expectedHash);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testVerifyIntegrityWithNullData() {
        // Arrange
        String expectedHash = "abcdef1234567890";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.verifyIntegrity(null, expectedHash));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testVerifyIntegrityWithNullHash() {
        // Arrange
        byte[] data = "test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.verifyIntegrity(data, null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
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
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
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
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCalculateHashWithNullData() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.calculateHash(null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithValidHash() {
        // Arrange
        String validHash = "abcdef1234567890";

        // Act & Assert - should not throw
        integrityVerifier.validateHash(validHash);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithNullHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash(null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithEmptyHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash(""));
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash("   "));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithWhitespaceOnlyHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> integrityVerifier.validateHash("\t\n\r"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testVerifyIntegrityWithEmptyData() throws HashingException, StorageIntegrityException {
        // Arrange
        byte[] data = new byte[0];
        String expectedHash = "empty_hash";

        when(mockBlake3Service.hashBuffer(data)).thenReturn(expectedHash);

        // Act & Assert - should not throw (empty data is valid)
        integrityVerifier.verifyIntegrity(data, expectedHash);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
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
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
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