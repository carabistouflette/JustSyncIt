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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for VerifyCommand.
 */
@DisplayName("VerifyCommand Tests")
class VerifyCommandTest {

    private VerifyCommand verifyCommand;
    private Blake3Service mockBlake3Service;

    @BeforeEach
    void setUp() {
        verifyCommand = new VerifyCommand();
        mockBlake3Service = mock(Blake3Service.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct command name")
    void shouldReturnCorrectCommandName() {
        assertEquals("--verify", verifyCommand.getName());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct description")
    void shouldReturnCorrectDescription() {
        assertNotNull(verifyCommand.getDescription());
        assertTrue(verifyCommand.getDescription().toLowerCase().contains("verify"));
    }

    @Test
    @Timeout(5)
    @DisplayName("Should return correct usage")
    void shouldReturnCorrectUsage() {
        String usage = verifyCommand.getUsage();
        assertNotNull(usage);
        assertTrue(usage.contains("--verify"));
    }

    @Test
    @Timeout(5)
    @DisplayName("Should execute help option")
    void shouldExecuteHelpOption() {
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = verifyCommand.execute(new String[] { "--help" }, context);
        assertTrue(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail when no arguments provided")
    void shouldFailWhenNoArgumentsProvided() {
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = verifyCommand.execute(new String[] {}, context);
        assertFalse(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail when only file path provided")
    void shouldFailWhenOnlyFilePathProvided() {
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = verifyCommand.execute(new String[] { "/path/to/file" }, context);
        assertFalse(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail for non-existent file")
    void shouldFailForNonExistentFile() {
        String validHash = "a".repeat(64); // 64 hex chars = valid BLAKE3 hash length
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = verifyCommand.execute(
                new String[] { "/non/existent/file.txt", validHash }, context);
        assertFalse(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail for invalid hash format - too short")
    void shouldFailForInvalidHashFormatTooShort() {
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = verifyCommand.execute(
                new String[] { "/path/to/file", "abc123" }, context);
        assertFalse(result);
    }

    @Test
    @Timeout(5)
    @DisplayName("Should fail for invalid hash format - non-hex characters")
    void shouldFailForInvalidHashFormatNonHex() {
        String invalidHash = "g".repeat(64); // 'g' is not a valid hex char
        CommandContext context = new CommandContext(mockBlake3Service);
        boolean result = verifyCommand.execute(
                new String[] { "/path/to/file", invalidHash }, context);
        assertFalse(result);
    }
}
