package com.justsyncit.hash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Blake3FileHasherTest {

    private Blake3FileHasher fileHasher;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Use Mockito to create mocks for the interfaces
        StreamHasher streamHasherMock = Mockito.mock(StreamHasher.class);
        BufferHasher bufferHasherMock = Mockito.mock(BufferHasher.class);

        // Use default constructor which uses the default MAX_FILE_SIZE
        fileHasher = new Blake3FileHasher(
                streamHasherMock,
                bufferHasherMock);
    }

    @Test
    void testMaxFileSizeLimit() {
        // Check default limit is effectively 1TB
        assertEquals(1099511627776L, fileHasher.getMaxFileSizeValue());
    }

    @Test
    void testHashFilesBelowLimit() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "test".getBytes());

        assertDoesNotThrow(() -> fileHasher.hashFile(testFile));
    }
}
