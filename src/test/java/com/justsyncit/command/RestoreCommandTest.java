package com.justsyncit.command;

import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import com.justsyncit.restore.RestoreOptions;
import com.justsyncit.restore.RestoreService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestoreCommandTest {

    @Mock
    private RestoreService restoreService;

    @Mock
    private NetworkService networkService;

    @Mock
    private CommandContext context;

    private RestoreCommand command;
    private ByteArrayOutputStream outputStream;
    private ByteArrayOutputStream errorStream;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new RestoreCommand(restoreService, networkService);
        outputStream = new ByteArrayOutputStream();
        errorStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(errorStream));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testGetName() {
        assertEquals("restore", command.getName());
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
        boolean result = command.execute(new String[] { "--help" }, context);
        assertTrue(result);
        assertTrue(outputStream.toString().contains("Restore Command Help"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithNoArgs() {
        boolean result = command.execute(new String[] {}, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Snapshot ID and target directory are required"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithMissingTargetDir() {
        boolean result = command.execute(new String[] { "abc-123" }, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Snapshot ID and target directory are required"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithInvalidOptions() {
        boolean result = command.execute(new String[] { "abc-123", tempDir.toString(), "--invalid-option" }, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Unknown option"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteRestore() throws Exception {
        // Mock successful restore
        RestoreService.RestoreResult restoreResult = RestoreService.RestoreResult.create(10, 0, 0, 1000, true, 100);
        when(restoreService.restore(anyString(), any(Path.class), any(RestoreOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(restoreResult));

        boolean result = command.execute(new String[] { "snap-1", tempDir.toString() }, context);

        assertTrue(result);
        assertTrue(outputStream.toString().contains("Restore completed successfully"));
        verify(restoreService).restore(eq("snap-1"), eq(tempDir), any(RestoreOptions.class));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExecuteRemoteRestore() throws Exception {
        // Mock successful connection and restore
        when(networkService.connectToNode(any(InetSocketAddress.class), any(TransportType.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(networkService.disconnectFromNode(any(InetSocketAddress.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        RestoreService.RestoreResult restoreResult = RestoreService.RestoreResult.create(10, 0, 0, 1000, true, 100);
        when(restoreService.restore(anyString(), any(Path.class), any(RestoreOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(restoreResult));

        boolean result = command.execute(new String[] {
                "snap-1", tempDir.toString(),
                "--remote",
                "--server", "localhost:8080",
                "--transport", "TCP"
        }, context);

        assertTrue(result);
        assertTrue(outputStream.toString().contains("Remote restore completed successfully"));

        ArgumentCaptor<RestoreOptions> optionsCaptor = ArgumentCaptor.forClass(RestoreOptions.class);
        verify(restoreService).restore(eq("snap-1"), eq(tempDir), optionsCaptor.capture());

        RestoreOptions options = optionsCaptor.getValue();
        assertTrue(options.isRemoteRestore());
        assertEquals("localhost", options.getRemoteAddress().getHostString());
        assertEquals(8080, options.getRemoteAddress().getPort());
        assertEquals(TransportType.TCP, options.getTransportType());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithTargetIsFile() throws Exception {
        Path existingFile = tempDir.resolve("somefile.txt");
        Files.createFile(existingFile);

        boolean result = command.execute(new String[] { "abc-123", existingFile.toString() }, context);
        assertFalse(result);
        assertTrue(errorStream.toString().contains("Target path exists but is not a directory"));
    }
}
