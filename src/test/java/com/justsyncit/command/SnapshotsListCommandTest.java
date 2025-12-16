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
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Test class for SnapshotsListCommand.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotsListCommandTest {

    @Mock
    private MetadataService metadataService;

    @Mock
    private Blake3Service blake3Service;

    private SnapshotsListCommand command;
    private CommandContext context;
    private ByteArrayOutputStream outputStream;
    private ByteArrayOutputStream errorStream;
    private PrintStream printStream;
    private PrintStream errorPrintStream;

    @BeforeEach
    void setUp() {
        command = new SnapshotsListCommand(metadataService);
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
        assertEquals("List all available snapshots", command.getDescription());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetUsage() {
        assertEquals("snapshots list [options]", command.getUsage());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithHelp() {
        boolean result = command.execute(new String[] { "--help" }, context);
        assertTrue(result);

        String output = outputStream.toString();
        assertTrue(output.contains("Snapshots List Command Help"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithMissingSubcommand() {
        boolean result = command.execute(new String[] {}, context);
        assertFalse(result);

        String output = outputStream.toString();
        String error = errorStream.toString();
        assertTrue(error.contains("Error: Missing subcommand 'list'"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithInvalidSubcommand() {
        boolean result = command.execute(new String[] { "invalid" }, context);
        assertFalse(result);

        String output = outputStream.toString();
        String error = errorStream.toString();
        assertTrue(error.contains("Error: Missing subcommand 'list'"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteWithValidList() throws Exception {
        // Mock snapshots
        Snapshot snapshot1 = new Snapshot("snap1", "Snapshot 1", "First snapshot",
                Instant.now(), 100, 1024 * 1024);
        Snapshot snapshot2 = new Snapshot("snap2", "Snapshot 2", "Second snapshot",
                Instant.now().minusSeconds(3600), 200, 2 * 1024 * 1024);

        List<Snapshot> snapshots = Arrays.asList(snapshot1, snapshot2);
        when(metadataService.listSnapshots()).thenReturn(snapshots);

        boolean result = command.execute(new String[] { "list" }, context);
        assertTrue(result);

        String output = outputStream.toString();
        assertTrue(output.contains("Available Snapshots:"));
        assertTrue(output.contains("snap1"));
        assertTrue(output.contains("snap2"));
        assertTrue(output.contains("Total: 2 snapshot(s)"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteWithVerboseOption() throws Exception {
        // Mock snapshots
        Snapshot snapshot = new Snapshot("snap1", "Snapshot 1", "First snapshot",
                Instant.now(), 100, 1024 * 1024);

        List<Snapshot> snapshots = Arrays.asList(snapshot);
        when(metadataService.listSnapshots()).thenReturn(snapshots);

        boolean result = command.execute(new String[] { "list", "--verbose" }, context);
        assertTrue(result);

        String output = outputStream.toString();
        assertTrue(output.contains("Description"));
        assertTrue(output.contains("First snapshot"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteWithSortBySizeOption() throws Exception {
        // Mock snapshots
        Snapshot snapshot1 = new Snapshot("snap1", "Small Snapshot", "Small",
                Instant.now(), 100, 1024 * 1024);
        Snapshot snapshot2 = new Snapshot("snap2", "Large Snapshot", "Large",
                Instant.now().minusSeconds(3600), 200, 2 * 1024 * 1024);

        List<Snapshot> snapshots = Arrays.asList(snapshot1, snapshot2);
        when(metadataService.listSnapshots()).thenReturn(snapshots);

        boolean result = command.execute(new String[] { "list", "--sort-by-size" }, context);
        assertTrue(result);

        String output = outputStream.toString();
        // Should show large snapshot first when sorting by size
        int largeIndex = output.indexOf("Large Snapshot");
        int smallIndex = output.indexOf("Small Snapshot");
        assertTrue(largeIndex < smallIndex);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithUnknownOption() {
        boolean result = command.execute(new String[] { "list", "--unknown" }, context);
        assertFalse(result);

        String output = outputStream.toString();
        String error = errorStream.toString();
        assertTrue(error.contains("Error: Unknown option: --unknown"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteWithEmptySnapshotList() throws Exception {
        when(metadataService.listSnapshots()).thenReturn(Collections.emptyList());

        boolean result = command.execute(new String[] { "list" }, context);
        assertTrue(result);

        String output = outputStream.toString();
        assertTrue(output.contains("No snapshots found."));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteWithServiceException() throws Exception {
        when(metadataService.listSnapshots()).thenThrow(new RuntimeException("Service error"));

        boolean result = command.execute(new String[] { "list" }, context);
        assertFalse(result);

        String output = outputStream.toString();
        String error = errorStream.toString();
        assertTrue(error.contains("Failed to list snapshots"));
    }
}