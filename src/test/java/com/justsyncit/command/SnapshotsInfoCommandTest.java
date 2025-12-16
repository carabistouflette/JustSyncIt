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

package com.justsyncit.command;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test class for SnapshotsInfoCommand.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotsInfoCommandTest {

    @Mock
    private MetadataService metadataService;

    @Mock
    private Blake3Service blake3Service;

    private SnapshotsInfoCommand command;
    private CommandContext context;
    private ByteArrayOutputStream outputStream;
    private ByteArrayOutputStream errorStream;
    private PrintStream printStream;
    private PrintStream errorPrintStream;

    @BeforeEach
    void setUp() {
        command = new SnapshotsInfoCommand(metadataService);
        context = new CommandContext(blake3Service);
        outputStream = new ByteArrayOutputStream();
        errorStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream);
        errorPrintStream = new PrintStream(errorStream);
        System.setOut(printStream);
        System.setErr(errorPrintStream);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetName() {
        assertEquals("snapshots", command.getName());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetDescription() {
        assertTrue(command.getDescription().contains("Show detailed information"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithHelp() {
        boolean result = command.execute(new String[] { "info", "--help" }, context);
        assertTrue(result);

        String output = outputStream.toString();
        assertTrue(output.contains("Snapshots Info Command Help"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithMissingArgs() {
        boolean result = command.execute(new String[] { "info" }, context);
        assertFalse(result);

        String error = errorStream.toString();
        assertTrue(error.contains("Error: Missing subcommand 'info' or snapshot ID"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithValidSnapshotId() throws IOException {
        String snapshotId = "test-snap-123";
        Snapshot snapshot = new Snapshot(snapshotId, "Test Snapshot", "Description",
                Instant.now(), 10, 1024);

        when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.of(snapshot));

        boolean result = command.execute(new String[] { "info", snapshotId }, context);
        assertTrue(result);

        String output = outputStream.toString();
        assertTrue(output.contains("Snapshot Information"));
        assertTrue(output.contains(snapshotId));
        assertTrue(output.contains("Test Snapshot"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithInvalidSnapshotId() throws IOException {
        String snapshotId = "invalid-snap";
        when(metadataService.getSnapshot(snapshotId)).thenReturn(Optional.empty());

        boolean result = command.execute(new String[] { "info", snapshotId }, context);
        assertFalse(result);

        String error = errorStream.toString();
        assertTrue(error.contains("Error: Snapshot not found"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithServiceError() throws IOException {
        String snapshotId = "error-snap";
        when(metadataService.getSnapshot(snapshotId)).thenThrow(new IOException("Disk error"));

        boolean result = command.execute(new String[] { "info", snapshotId }, context);
        assertFalse(result);

        String error = errorStream.toString();
        assertTrue(error.contains("Failed to get snapshot information for ID: " + snapshotId));
        assertTrue(error.contains("Disk error"));
    }
}
