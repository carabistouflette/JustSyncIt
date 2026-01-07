package com.justsyncit.network;

import com.justsyncit.network.protocol.FileTransferRequestMessage;
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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AbsurdityReproductionTest {

    @Mock
    private NetworkService networkService;
    @Mock
    private ContentStore contentStore;
    @Mock
    private com.justsyncit.network.compression.CompressionService compressionService;

    private FileTransferManagerImpl fileTransferManager;
    private final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 9000);

    @BeforeEach
    void setUp() {
        fileTransferManager = new FileTransferManagerImpl();
        fileTransferManager.setNetworkService(networkService);
        fileTransferManager.setCompressionService(compressionService);
        fileTransferManager.start();
    }

    @Test
    void testRequestContainsRealHash() throws Exception {
        // Given a file to transfer
        Path tempFile = Files.createTempFile("absurdity-test", ".tmp");
        Files.write(tempFile, "Faille de sécurité".getBytes());
        tempFile.toFile().deleteOnExit();

        // Mock network service to return completed futures
        when(networkService.sendMessage(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(compressionService.getAlgorithmName()).thenReturn("NONE");

        // When sendFile is called
        fileTransferManager.setTransferPipelineFactory(
                new com.justsyncit.network.transfer.pipeline.DefaultTransferPipelineFactory());
        fileTransferManager.sendFile(tempFile, remoteAddress, contentStore).get(5, TimeUnit.SECONDS);

        // Then capture the FileTransferRequestMessage
        ArgumentCaptor<com.justsyncit.network.protocol.ProtocolMessage> messageCaptor = ArgumentCaptor
                .forClass(com.justsyncit.network.protocol.ProtocolMessage.class);
        verify(networkService, atLeastOnce()).sendMessage(messageCaptor.capture(), eq(remoteAddress));

        // Find the FileTransferRequestMessage
        FileTransferRequestMessage requestMessage = messageCaptor.getAllValues().stream()
                .filter(m -> m instanceof FileTransferRequestMessage)
                .map(m -> (FileTransferRequestMessage) m)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FileTransferRequestMessage not sent"));

        // FAIL if it contains "pending_calculation"
        // This test documents the absurdity.
        System.out.println("Sent Hash: " + requestMessage.getBlake3Hash());
        assertNotEquals("pending_calculation", requestMessage.getBlake3Hash(),
                "CRITICAL: The system is sending a placeholder hash! This is an absurdity.");
    }
}
