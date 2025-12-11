package com.justsyncit.backup;

import com.justsyncit.scanner.SymlinkStrategy;
import com.justsyncit.network.TransportType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BackupOptionsTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDefaultBuilder() {
        BackupOptions options = new BackupOptions.Builder().build();
        assertNotNull(options);
        assertEquals(SymlinkStrategy.RECORD, options.getSymlinkStrategy());
        assertFalse(options.isIncludeHiddenFiles());
        assertTrue(options.isVerifyIntegrity());
        assertEquals(64 * 1024, options.getChunkSize());
        assertEquals(Integer.MAX_VALUE, options.getMaxDepth());
        assertFalse(options.isRemoteBackup());
        assertEquals(TransportType.TCP, options.getTransportType());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCustomBuilder() {
        InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        BackupOptions options = new BackupOptions.Builder()
                .symlinkStrategy(SymlinkStrategy.FOLLOW)
                .includeHiddenFiles(true)
                .verifyIntegrity(false)
                .chunkSize(1024)
                .maxDepth(5)
                .snapshotName("snap1")
                .description("desc")
                .remoteBackup(true)
                .remoteAddress(address)
                .transportType(TransportType.QUIC)
                .build();

        assertEquals(SymlinkStrategy.FOLLOW, options.getSymlinkStrategy());
        assertTrue(options.isIncludeHiddenFiles());
        assertFalse(options.isVerifyIntegrity());
        assertEquals(1024, options.getChunkSize());
        assertEquals(5, options.getMaxDepth());
        assertEquals("snap1", options.getSnapshotName());
        assertEquals("desc", options.getDescription());
        assertTrue(options.isRemoteBackup());
        assertEquals(address, options.getRemoteAddress());
        assertEquals(TransportType.QUIC, options.getTransportType());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testInvalidChunkSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BackupOptions.Builder().chunkSize(0);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new BackupOptions.Builder().chunkSize(-1);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testInvalidMaxDepth() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BackupOptions.Builder().maxDepth(-1);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testNullSymlinkStrategy() {
        assertThrows(NullPointerException.class, () -> {
            new BackupOptions.Builder().symlinkStrategy(null);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testNullTransportType() {
        assertThrows(NullPointerException.class, () -> {
            new BackupOptions.Builder().transportType(null);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRemoteBackupWithoutAddress() {
        assertThrows(IllegalStateException.class, () -> {
            new BackupOptions.Builder().remoteBackup(true).build();
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testEqualsAndHashCode() {
        BackupOptions options1 = new BackupOptions.Builder().snapshotName("test").build();
        BackupOptions options2 = new BackupOptions.Builder().snapshotName("test").build();
        BackupOptions options3 = new BackupOptions.Builder().snapshotName("other").build();

        assertEquals(options1, options2);
        assertEquals(options1.hashCode(), options2.hashCode());
        assertNotEquals(options1, options3);
    }
}