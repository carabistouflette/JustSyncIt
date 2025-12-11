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

import com.justsyncit.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for TwoLevelChunkPathGenerator.
 */
class TwoLevelChunkPathGeneratorTest {

    /** Temporary directory for tests. */
    @TempDir
    Path tempDir;

    /** Path generator under test. */
    private TwoLevelChunkPathGenerator pathGenerator;

    @BeforeEach
    void setUp() {
        pathGenerator = new TwoLevelChunkPathGenerator();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGeneratePathWithValidHash() throws ServiceException {
        // Arrange
        String hash = "abcdef1234567890";

        // Act
        Path result = pathGenerator.generatePath(tempDir, hash);

        // Assert
        assertNotNull(result);
        assertEquals(tempDir.resolve("ab").resolve("cdef1234567890"), result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGeneratePathWithMinimumHash() throws ServiceException {
        // Arrange
        String hash = "abcd";

        // Act
        Path result = pathGenerator.generatePath(tempDir, hash);

        // Assert
        assertNotNull(result);
        assertEquals(tempDir.resolve("ab").resolve("cd"), result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGeneratePathWithLongHash() throws ServiceException {
        // Arrange
        String hash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

        // Act
        Path result = pathGenerator.generatePath(tempDir, hash);

        // Assert
        assertNotNull(result);
        assertEquals(tempDir.resolve("ab").resolve(hash.substring(2)), result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithValidHash() {
        // Arrange
        String hash = "abcdef1234567890";

        // Act & Assert - should not throw
        pathGenerator.validateHash(hash);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithNullHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> pathGenerator.validateHash(null));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithEmptyHash() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> pathGenerator.validateHash(""));
        assertThrows(IllegalArgumentException.class, () -> pathGenerator.validateHash("   "));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testValidateHashWithTooShortHash() {
        // Arrange
        String hash = "abc"; // Less than minimum length

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> pathGenerator.validateHash(hash));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGeneratePathCreatesDirectories() throws java.io.IOException, ServiceException {
        // Arrange
        String hash = "test1234567890";

        // Act
        Path result = pathGenerator.generatePath(tempDir, hash);

        // Assert
        assertNotNull(result);
        assertEquals(tempDir.resolve("te").resolve("st1234567890"), result);
        // Verify parent directory was created
        java.nio.file.Files.createDirectories(tempDir.resolve("te"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGeneratePathWithDifferentHashes() throws ServiceException {
        // Test multiple different hashes
        String[] hashes = {
            "01234567890abcdef",
            "fedcba0987654321",
            "aaaaaaaaaaaaaaaa",
            "111111111111111",
            "zzzzzzzzzzzzzzzz"
        };

        for (String hash : hashes) {
            // Act
            Path result = pathGenerator.generatePath(tempDir, hash);

            // Assert
            assertNotNull(result);
            String expectedSubDir = hash.substring(0, 2);
            String expectedFileName = hash.substring(2);
            assertEquals(tempDir.resolve(expectedSubDir).resolve(expectedFileName), result);
        }
    }
}