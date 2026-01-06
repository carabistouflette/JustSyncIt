package com.justsyncit.network.transfer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

/**
 * Wraps a FileChannel transfer for zero-copy.
 */
public class FileRegionOperation implements TransferOperation {
    private final FileChannel fileChannel;
    private final long position;
    private final long count;
    private long transferred;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Intentional zero-copy exposure of FileChannel")
    public FileRegionOperation(FileChannel fileChannel, long position, long count) {
        this.fileChannel = fileChannel;
        this.position = position;
        this.count = count;
        this.transferred = 0;
    }

    @Override
    public boolean transfer(SocketChannel channel) throws IOException {
        long written = fileChannel.transferTo(position + transferred, count - transferred, channel);
        if (written > 0) {
            transferred += written;
        }
        return transferred >= count;
    }

    @Override
    public void release() {
        // Nothing to release for file channel wrapper usually,
        // as the channel lifecycle is managed externally
    }
}
