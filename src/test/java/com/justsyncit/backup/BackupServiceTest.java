package com.justsyncit.backup;

import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FilesystemScanner;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private ContentStore contentStore;
    @Mock
    private MetadataService metadataService;
    @Mock
    private FilesystemScanner scanner;
    @Mock
    private FileChunker chunker;

    private BackupService backupService;

    @BeforeEach
    void setUp() {
        backupService = new BackupService(contentStore, metadataService, scanner, chunker);
    }

    @Test
    void testBackupWithNullSourceDir() {
        BackupOptions options = new BackupOptions.Builder().build();
        CompletableFuture<BackupService.BackupResult> future = backupService.backup(null, options);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getCause() instanceof IllegalArgumentException);
        assertEquals("Source directory cannot be null", exception.getCause().getCause().getMessage());
    }

    /*
     * @Test
     * void testBackupWithNonExistentDir() {
     * BackupOptions options = new BackupOptions.Builder().build();
     * Path nonExistentPath = Paths.get("non-existent-dir-" +
     * System.currentTimeMillis());
     * CompletableFuture<BackupService.BackupResult> future =
     * backupService.backup(nonExistentPath, options);
     * 
     * ExecutionException exception = assertThrows(ExecutionException.class,
     * future::get);
     * assertTrue(exception.getCause() instanceof RuntimeException);
     * assertTrue(exception.getCause().getCause() instanceof
     * IllegalArgumentException);
     * assertTrue(exception.getCause().getCause().getMessage().
     * contains("Directory must exist"));
     * }
     */

    // Note: Testing successful backup is difficult because FileProcessor.create is
    // a static call
    // and effectively tight couples BackupService to FileProcessor.
    // In a real refactoring, we would inject a FileProcessorFactory.
    // For now, we verified the argument validation logic which is a good robustness
    // check.
}
