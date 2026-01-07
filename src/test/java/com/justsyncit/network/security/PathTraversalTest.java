package com.justsyncit.network.security;

import com.justsyncit.network.protocol.FileTransferRequestMessage;
import com.justsyncit.network.transfer.FileTransferManagerImpl;
import com.justsyncit.network.transfer.FileTransferStatus;
import com.justsyncit.storage.ContentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PathTraversalTest {

    private FileTransferManagerImpl fileTransferManager;
    private ContentStore contentStore;

    @BeforeEach
    void setUp() {
        fileTransferManager = new FileTransferManagerImpl();
        contentStore = mock(ContentStore.class);
        fileTransferManager.start();
    }

    @Test
    void testPathTraversalInRequest(@TempDir Path tempDir) throws ExecutionException, InterruptedException {
        // GIVEN
        String maliciousPath = "../../evil.txt";
        long fileSize = 100;
        InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 8080);

        FileTransferRequestMessage request = new FileTransferRequestMessage(
                maliciousPath, fileSize, System.currentTimeMillis(), "hash", 1024, "NONE");

        // WHEN
        fileTransferManager.handleFileTransferRequest(request, remoteAddress, contentStore).get();

        // THEN
        // In the vulnerable version, the transfer is registered with the malicious path
        // as the key
        FileTransferStatus status = fileTransferManager.getTransferStatus(maliciousPath);
        assertNotNull(status, "Transfer should be registered");

        // Vulnerability Check:
        // If vulnerable, the local path will preserve the traversal
        // If fixed, it should be stripped or rejected.
        // For this test, we expect the FIX to ensure the file is NOT written to
        // ../../evil.txt
        // effectively checking that the path name is sanitized.

        Path statusPath = status.getFilePath();
        String fileName = statusPath.getFileName().toString();

        // Assert that the path does NOT contain traversal elements and is safe
        // This assertion will FAIL on the vulnerable code if it just keeps the path as
        // is
        // We want the resulting path to be just "evil.txt" (or equivalent safe path)

        // Note: activeTransfers.put(fileName, status) uses the raw filename as key
        // currently.
        // This test assumes we will fix it to potentially use the same key but
        // different internal path,
        // or reject it.
        // But for "Flattening", the status check logic might change.

        // Let's assert that the resolved path is absolute and within a specific base
        // directory (if we had one)
        // Or simply that it's just a filename.

        assertFalse(statusPath.toString().contains(".."), "Path should be sanitized to remove traversal characters");
        assertEquals("evil.txt", statusPath.getFileName().toString(), "Path should be flattened to just the filename");
        assertTrue(statusPath.getParent().toString().endsWith("downloads"), "File should be in downloads directory");
    }
}
