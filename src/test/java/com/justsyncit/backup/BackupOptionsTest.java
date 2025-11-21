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

package com.justsyncit.backup;

import com.justsyncit.scanner.SymlinkStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BackupOptions.
 */
public class BackupOptionsTest {

    private BackupOptions.Builder builder;

    @BeforeEach
    void setUp() {
        builder = new BackupOptions.Builder();
    }

    @Test
    void testDefaultOptions() {
        BackupOptions options = builder.build();
        assertEquals(SymlinkStrategy.RECORD, options.getSymlinkStrategy());
        assertFalse(options.isIncludeHiddenFiles());
        assertTrue(options.isVerifyIntegrity());
        assertEquals(65536, options.getChunkSize());
        assertNull(options.getSnapshotName());
        assertNull(options.getDescription());
    }

    @Test
    void testBuilderWithSymlinkStrategy() {
        BackupOptions options = builder.symlinkStrategy(SymlinkStrategy.FOLLOW).build();
        assertEquals(SymlinkStrategy.FOLLOW, options.getSymlinkStrategy());
    }

    @Test
    void testBuilderWithIncludeHiddenFiles() {
        BackupOptions options = builder.includeHiddenFiles(true).build();
        assertTrue(options.isIncludeHiddenFiles());
    }

    @Test
    void testBuilderWithVerifyIntegrity() {
        BackupOptions options = builder.verifyIntegrity(false).build();
        assertFalse(options.isVerifyIntegrity());
    }

    @Test
    void testBuilderWithChunkSize() {
        BackupOptions options = builder.chunkSize(128 * 1024).build();
        assertEquals(128 * 1024, options.getChunkSize());
    }

    @Test
    void testBuilderWithSnapshotName() {
        BackupOptions options = builder.snapshotName("test-snapshot").build();
        assertEquals("test-snapshot", options.getSnapshotName());
    }

    @Test
    void testBuilderWithDescription() {
        BackupOptions options = builder.description("Test backup").build();
        assertEquals("Test backup", options.getDescription());
    }

    @Test
    void testBuilderWithMultipleOptions() {
        BackupOptions options = builder
                .symlinkStrategy(SymlinkStrategy.SKIP)
                .includeHiddenFiles(true)
                .verifyIntegrity(false)
                .chunkSize(32 * 1024)
                .snapshotName("multi-test")
                .description("Multiple options test")
                .build();
        assertEquals(SymlinkStrategy.SKIP, options.getSymlinkStrategy());
        assertTrue(options.isIncludeHiddenFiles());
        assertFalse(options.isVerifyIntegrity());
        assertEquals(32 * 1024, options.getChunkSize());
        assertEquals("multi-test", options.getSnapshotName());
        assertEquals("Multiple options test", options.getDescription());
    }

    @Test
    void testToString() {
        BackupOptions options = builder
                .snapshotName("test")
                .description("Test description")
                .build();
        String result = options.toString();
        assertNotNull(result);
        // Just verify that toString() returns something and doesn't throw an exception
        // The actual format may vary, so we just check it's not null and not empty
        assertFalse(result.isEmpty());
        assertTrue(result.contains("BackupOptions"));
    }
}