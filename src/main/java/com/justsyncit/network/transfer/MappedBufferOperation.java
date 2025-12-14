package com.justsyncit.network.transfer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Wraps a MappedByteBuffer for transfer.
 * Supports efficient transfer of memory-mapped file regions.
 */
public class MappedBufferOperation implements TransferOperation {
    private final MappedByteBuffer buffer;
    private final long size;
    private long transferred;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Intentional zero-copy exposure of buffer")
    public MappedBufferOperation(MappedByteBuffer buffer) {
        this.buffer = buffer;
        this.size = buffer.remaining();
        this.transferred = 0;
    }

    @Override
    public boolean transfer(SocketChannel channel) throws IOException {
        int written = channel.write(buffer);
        if (written > 0) {
            transferred += written;
        }
        return !buffer.hasRemaining();
    }

    public long getTransferredBytes() {
        return transferred;
    }

    public boolean isComplete() {
        return !buffer.hasRemaining();
    }

    /**
     * Gets the underlying mapped byte buffer.
     *
     * @return the mapped byte buffer
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Intentional zero-copy exposure of buffer")
    public MappedByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public void release() {
        // No-op for MappedByteBuffer as it's managed by GC/FileChannel
        // In future, we could use Cleaner if needed for deterministic unmapping
    }
}
