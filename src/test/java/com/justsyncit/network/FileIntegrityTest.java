package com.justsyncit.network;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.protocol.TransferCompleteMessage;
import com.justsyncit.network.transfer.FileTransferManagerImpl;
import com.justsyncit.storage.ContentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileIntegrityTest {

    @Mock
    private NetworkService networkService;
    @Mock
    private ContentStore contentStore;
    @Mock
    private Blake3Service blake3Service;

    private FileTransferManagerImpl fileTransferManager;
    private final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 9000);
    private Path tempFile;

    @BeforeEach
    void setUp() throws Exception {
        fileTransferManager = new FileTransferManagerImpl();
        fileTransferManager.setNetworkService(networkService);
        fileTransferManager.setBlake3Service(blake3Service);
        fileTransferManager.start();

        // Create a temp file
        tempFile = Files.createTempFile("test-integrity", ".dat");
        Files.write(tempFile, "test content".getBytes());
    }

    @Test
    void testSendFileSendsCorrectHash() throws Exception {
        // Given
        String expectedHash = "real_blake3_hash";
        when(blake3Service.hashFile(any())).thenReturn(expectedHash);
        when(networkService.sendMessage(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // When
        fileTransferManager.sendFile(tempFile, remoteAddress, contentStore).get(5, TimeUnit.SECONDS);

        // Then
        // We expect multiple messages: Request, Chunks, Complete.
        // We want to verify the TransferCompleteMessage contains the expected hash.

        ArgumentCaptor<com.justsyncit.network.protocol.ProtocolMessage> messageCaptor = ArgumentCaptor
                .forClass(com.justsyncit.network.protocol.ProtocolMessage.class);
        verify(networkService, atLeastOnce()).sendMessage(messageCaptor.capture(), eq(remoteAddress));

        TransferCompleteMessage completeMessage = messageCaptor.getAllValues().stream()
                .filter(msg -> msg instanceof TransferCompleteMessage)
                .map(msg -> (TransferCompleteMessage) msg)
                .findFirst()
                .orElseThrow(() -> new AssertionError("TransferCompleteMessage not sent"));

        // THIS ASSERTION IS EXPECTED TO FAIL BEFORE THE FIX
        // Currently it sends "pending_calculation"
        assertNotEquals("pending_calculation", completeMessage.getFinalBlake3Hash(),
                "Should not send placeholder hash");
        assertEquals(expectedHash, completeMessage.getFinalBlake3Hash(), "Should send correct file hash");
    }
}
