package com.justsyncit.storage;

import com.justsyncit.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoLevelChunkPathGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatePath_ShouldThrowException_WhenHashContainsPathTraversal() {
        TwoLevelChunkPathGenerator generator = new TwoLevelChunkPathGenerator();
        String maliciousHash = "..evilpath";

        // This expects the fix to throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            generator.generatePath(tempDir, maliciousHash);
        });

        assertTrue(exception.getMessage().contains("alphanumeric"),
                "Exception message should mention alphanumeric requirements");
    }

    @Test
    void generatePath_ShouldThrowException_WhenHashContainsSlash() {
        TwoLevelChunkPathGenerator generator = new TwoLevelChunkPathGenerator();
        String maliciousHash = "ab/cd/ef";

        assertThrows(IllegalArgumentException.class, () -> {
            generator.generatePath(tempDir, maliciousHash);
        });
    }

    @Test
    void generatePath_ShouldSucceed_WhenHashIsValidHex() throws ServiceException {
        TwoLevelChunkPathGenerator generator = new TwoLevelChunkPathGenerator();
        String validHash = "a1b2c3d4e5";

        Path path = generator.generatePath(tempDir, validHash);

        assertTrue(path.startsWith(tempDir), "Generated path should be within storage directory");
        assertTrue(path.toString().endsWith("a1/b2c3d4e5"), "Path should follow 2-level structure");
    }
}