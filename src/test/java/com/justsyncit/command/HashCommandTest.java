/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.command;

import com.justsyncit.hash.Blake3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HashCommand.
 */
@DisplayName("HashCommand Tests")
class HashCommandTest {

    private Blake3Service mockBlake3Service;
    private HashCommand hashCommand;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockBlake3Service = mock(Blake3Service.class);
        hashCommand = new HashCommand(mockBlake3Service);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct command name")
    void shouldReturnCorrectCommandName() {
        assertEquals("--hash", hashCommand.getName());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct description")
    void shouldReturnCorrectDescription() {
        assertNotNull(hashCommand.getDescription());
        assertTrue(hashCommand.getDescription().contains("BLAKE3"));
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct usage")
    void shouldReturnCorrectUsage() {
        String usage = hashCommand.getUsage();
        assertNotNull(usage);
        assertTrue(usage.contains("--hash"));
        assertTrue(usage.contains("file"));
    }

    @Test
    @Timeout(5)
    @DisplayName("Should execute help option")
    void shouldExecuteHelpOption() {
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = hashCommand.execute(new String[] { "--help" }, context);
        assertTrue(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail when no arguments provided")
    void shouldFailWhenNoArgumentsProvided() {
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = hashCommand.execute(new String[] {}, context);
        assertFalse(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail for non-existent file")
    void shouldFailForNonExistentFile() {
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = hashCommand.execute(new String[] { "/non/existent/file.txt" }, context);
        assertFalse(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should hash existing file successfully")
    void shouldHashExistingFileSuccessfully() throws Exception {
        // Create test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // Mock hash service
        when(mockBlake3Service.hashFile(any(Path.class)))
                .thenReturn("abc123hash");

        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = hashCommand.execute(new String[] { testFile.toString() }, context);
        assertTrue(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail when directory path provided instead of file")
    void shouldFailWhenDirectoryPathProvided() throws Exception {
        Path testDir = tempDir.resolve("subdir");
        Files.createDirectory(testDir);

        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = hashCommand.execute(new String[] { testDir.toString() }, context);
        assertFalse(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should handle IO exception")
    void shouldHandleIOException() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        when(mockBlake3Service.hashFile(any(Path.class)))
                .thenThrow(new java.io.IOException("Hash failed"));

        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = hashCommand.execute(new String[] { testFile.toString() }, context);
        assertFalse(result);
    }
}
