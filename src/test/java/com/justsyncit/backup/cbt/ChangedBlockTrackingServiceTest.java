package com.justsyncit.backup.cbt;

import com.justsyncit.scanner.AsyncByteBufferPool;
import com.justsyncit.scanner.AsyncScanOptions;
import com.justsyncit.scanner.AsyncWatchServiceManager;
import com.justsyncit.scanner.ThreadPoolManager;
import com.justsyncit.scanner.WatchServiceRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChangedBlockTrackingServiceTest {

    private AsyncWatchServiceManager watchServiceManager;
    private ThreadPoolManager threadPoolManager;
    private AsyncByteBufferPool bufferPool;
    private ModificationJournal journal;
    private ChangedBlockTrackingService service;

    @BeforeEach
    void setUp() {
        watchServiceManager = mock(AsyncWatchServiceManager.class);
        threadPoolManager = mock(ThreadPoolManager.class);
        bufferPool = mock(AsyncByteBufferPool.class);
        journal = mock(ModificationJournal.class);

        // ChangedBlockTrackingService constructor likely takes these args.
        // Based on previous file view attempts, it seemed to have these.
        // I need to confirm the constructor signature.
        // Assuming:
        // public ChangedBlockTrackingService(ThreadPoolManager, AsyncByteBufferPool,
        // ModificationJournal, AsyncWatchServiceManager)
        // OR similar.
        // Let's check the constructor via reflection or file view if this fails
        // compilation.

        service = new ChangedBlockTrackingService(watchServiceManager, journal);
    }

    @Test
    void testEnableAndDisableTracking() {
        Path rootDir = Paths.get("/tmp/test/monitor");
        WatchServiceRegistration registration = mock(WatchServiceRegistration.class);
        CompletableFuture<WatchServiceRegistration> future = CompletableFuture.completedFuture(registration);

        when(watchServiceManager.startDirectoryMonitoring(any(), any(), any())).thenReturn(future);

        // Enable tracking
        service.enableTracking(rootDir);

        // Verify startDirectoryMonitoring was called
        verify(watchServiceManager).startDirectoryMonitoring(eq(rootDir.toAbsolutePath().normalize()),
                any(AsyncScanOptions.class), any());

        // Disable tracking
        service.disableTracking(rootDir);

        // Verify stopDirectoryMonitoring was called with the registration
        verify(watchServiceManager).stopDirectoryMonitoring(registration);
    }
}
