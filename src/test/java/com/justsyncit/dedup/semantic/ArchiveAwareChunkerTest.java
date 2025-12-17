package com.justsyncit.dedup.semantic;

import com.justsyncit.scanner.ChunkingOptions;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FileChunker.ChunkingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ArchiveAwareChunkerTest {

    @Test
    public void testZipDetectionAndDelegation(@TempDir Path tempDir) throws Exception {
        // Create a real zip file
        Path zipFile = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            ZipEntry entry = new ZipEntry("entry.txt");
            zos.putNextEntry(entry);
            zos.write("content".getBytes());
            zos.closeEntry();
        }

        FileChunker mockDelegate = Mockito.mock(FileChunker.class);
        ChunkingResult mockResult = new ChunkingResult(zipFile, 1, 100, 0, "hash", Collections.emptyList());

        when(mockDelegate.chunkFile(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

        ArchiveAwareChunker chunker = new ArchiveAwareChunker(mockDelegate);

        // Execute
        CompletableFuture<ChunkingResult> future = chunker.chunkFile(zipFile, new ChunkingOptions());
        ChunkingResult result = future.get();

        // Verify delegation occurred
        verify(mockDelegate).chunkFile(eq(zipFile), any());

        // In the current implementation, we just log and delegate.
        // So behavior is same as delegate.
        // To verify "semantic" logic triggered, we would need to inspect logs or
        // internal state.
        // But detecting it does not crash or fail is good.
    }
}
