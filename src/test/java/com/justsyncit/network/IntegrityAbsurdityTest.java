package com.justsyncit.network;

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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IntegrityAbsurdityTest {

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
        void testSenderSendsRealHash() throws Exception {
                // Given a file to transfer
                Path tempFile = Files.createTempFile("integrity-test", ".tmp");
                Files.write(tempFile, "Faille de sécurité".getBytes());
                tempFile.toFile().deleteOnExit();

                // Mock network service to return completed futures
                when(networkService.sendMessage(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

                when(compressionService.getAlgorithmName()).thenReturn("NONE");

                // When sendFile is called
                fileTransferManager.setTransferPipelineFactory(
                                new com.justsyncit.network.transfer.pipeline.DefaultTransferPipelineFactory());
                com.justsyncit.network.transfer.FileTransferResult result = fileTransferManager
                                .sendFile(tempFile, remoteAddress, contentStore).get(5, TimeUnit.SECONDS);

                System.out.println("Transfer Result Success: " + result.isSuccess());
                if (!result.isSuccess()) {
                        System.out.println("Transfer Error: " + result.getErrorMessage());
                }

                if (!result.isSuccess()) {
                        throw new AssertionError("Transfer failed: " + result.getErrorMessage());
                }

                // Then capture the TransferCompleteMessage
                ArgumentCaptor<com.justsyncit.network.protocol.ProtocolMessage> messageCaptor = ArgumentCaptor
                                .forClass(com.justsyncit.network.protocol.ProtocolMessage.class);
                verify(networkService, atLeastOnce()).sendMessage(messageCaptor.capture(), eq(remoteAddress));

                System.out.println("Captured Messages: " + messageCaptor.getAllValues());

                // Find the TransferCompleteMessage
                TransferCompleteMessage completeMessage = messageCaptor.getAllValues().stream()
                                .filter(m -> m instanceof TransferCompleteMessage)
                                .map(m -> (TransferCompleteMessage) m)
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("TransferCompleteMessage not sent"));

                // Assert the FIX
                System.out.println("Sent Hash: " + completeMessage.getFinalBlake3Hash());
                assertNotNull(completeMessage.getFinalBlake3Hash());
                assertNotEquals("pending_calculation", completeMessage.getFinalBlake3Hash(),
                                "The system should NO LONGER send a placeholder hash.");
        }
}
