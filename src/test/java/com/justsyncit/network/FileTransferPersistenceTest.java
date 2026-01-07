package com.justsyncit.network;

import com.justsyncit.network.protocol.ChunkDataMessage;
import com.justsyncit.network.protocol.FileTransferRequestMessage;
import com.justsyncit.network.protocol.TransferCompleteMessage;
import com.justsyncit.network.transfer.FileTransferManagerImpl;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileTransferPersistenceTest {

    @Mock
    private NetworkService networkService;
    @Mock
    private ContentStore contentStore;
    @Mock
    private MetadataService metadataService;

    private FileTransferManagerImpl fileTransferManager;
    private final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 9000);

    @BeforeEach
    void setUp() {
        fileTransferManager = new FileTransferManagerImpl();
        fileTransferManager.setNetworkService(networkService);
        fileTransferManager.setMetadataService(metadataService);
        fileTransferManager.start();
    }

    @Test
    void testFileMetadataIsPersistedAfterTransfer() throws Exception {
        // Given
        String fileName = "test-doc.pdf";
        long fileSize = 100;
        String fakeHash = "abc123hash";
        Path localPath = Paths.get(fileName);

        // 1. Send Request
        FileTransferRequestMessage request = new FileTransferRequestMessage(
                fileName, fileSize, System.currentTimeMillis(), "placeholder", 64 * 1024, "NONE");

        fileTransferManager.handleFileTransferRequest(request, remoteAddress, contentStore).get(1, TimeUnit.SECONDS);

        // 2. Send Chunk
        ChunkDataMessage chunk = new ChunkDataMessage(
                fileName, 0, 100, 100, fakeHash, new byte[100]);

        // Mock pipeline creation or behavior?
        // The FileTransferManagerImpl creates a real pipeline.
        // We might need to mock TransferPipelineFactory to avoid real
        // decompression/verification logic
        // if it's too complex, but for now let's see if it runs with mocks or if we
        // need more setup.
        // Actually, FileTransferManagerImpl uses a real factory by default.
        // The ReceivePipeline uses services. I might need to mock those services.
        // For simplicity, I'll trust the pipeline works if dependencies are mocks.
        // BUT wait, ReceivePipeline creates stages.
        // I'll try to execute it.

        fileTransferManager.handleChunkData(chunk, remoteAddress, contentStore).get(1, TimeUnit.SECONDS);

        // 3. Send Complete
        TransferCompleteMessage complete = new TransferCompleteMessage(fileName, fileSize, fileSize, fakeHash);
        fileTransferManager.handleTransferComplete(complete, remoteAddress).get(1, TimeUnit.SECONDS);

        // Then
        ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(metadataService, times(1)).updateFile(metadataCaptor.capture());

        FileMetadata captured = metadataCaptor.getValue();
        assertTrue(captured.getPath().endsWith(fileName));
        assertEquals(fakeHash, captured.getFileHash());
        assertEquals(1, captured.getChunkHashes().size());
        assertEquals(fakeHash, captured.getChunkHashes().get(0));
    }
}
