package com.justsyncit.command;

import com.justsyncit.backup.BackupOptions;
import com.justsyncit.backup.BackupService;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupCommandTest {

    @Mock
    private BackupService backupService;

    @Mock
    private NetworkService networkService;

    @Mock
    private CommandContext context;

    private BackupCommand command;
    private ByteArrayOutputStream outputStream;
    private ByteArrayOutputStream errorStream;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new BackupCommand(backupService, networkService);
        outputStream = new ByteArrayOutputStream();
        errorStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(errorStream));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetName() {
        assertEquals("backup", command.getName());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetDescription() {
        assertNotNull(command.getDescription());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetUsage() {
        assertNotNull(command.getUsage());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithHelp() {
        boolean result = command.execute(new String[]{"--help"}, context);
        assertTrue(result);
        assertTrue(outputStream.toString().contains("Backup Command Help"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithNoArgs() {
        boolean result = command.execute(new String[]{}, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Source directory is required"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithNonExistentSource() {
        boolean result = command.execute(new String[]{"/non/existent/path"}, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Source directory does not exist"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteLocalBackup() throws Exception {
        // Mock successful backup
        BackupService.BackupResult backupResult = BackupService.BackupResult.success("snap-1", 10, 1000, 5, true);
        when(backupService.backup(any(Path.class), any(BackupOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(backupResult));

        boolean result = command.execute(new String[]{tempDir.toString()}, context);

        assertTrue(result);
        assertTrue(outputStream.toString().contains("Backup completed successfully"));
        verify(backupService).backup(eq(tempDir), any(BackupOptions.class));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteRemoteBackup() throws Exception {
        // Mock successful connection and backup
        when(networkService.connectToNode(any(InetSocketAddress.class), any(TransportType.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(networkService.disconnectFromNode(any(InetSocketAddress.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        BackupService.BackupResult backupResult = BackupService.BackupResult.success("snap-1", 10, 1000, 5, true);
        when(backupService.backup(any(Path.class), any(BackupOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(backupResult));

        boolean result = command.execute(new String[]{
                tempDir.toString(),
                "--remote",
                "--server", "localhost:8080",
                "--transport", "TCP"
        }, context);

        assertTrue(result);
        assertTrue(outputStream.toString().contains("Remote backup completed successfully"));

        ArgumentCaptor<BackupOptions> optionsCaptor = ArgumentCaptor.forClass(BackupOptions.class);
        verify(backupService).backup(eq(tempDir), optionsCaptor.capture());

        BackupOptions options = optionsCaptor.getValue();
        assertTrue(options.isRemoteBackup());
        assertEquals("localhost", options.getRemoteAddress().getHostString());
        assertEquals(8080, options.getRemoteAddress().getPort());
        assertEquals(TransportType.TCP, options.getTransportType());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithInvalidOptions() {
        boolean result = command.execute(new String[]{tempDir.toString(), "--invalid-option"}, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Unknown option"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithInvalidChunkSize() {
        boolean result = command.execute(new String[]{tempDir.toString(), "--chunk-size", "invalid"}, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Invalid chunk size"));
    }
}
