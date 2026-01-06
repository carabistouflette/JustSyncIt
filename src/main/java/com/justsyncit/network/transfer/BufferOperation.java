package com.justsyncit.network.transfer;

import com.justsyncit.scanner.AsyncByteBufferPool;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Wraps a ByteBuffer for transfer.
 */
public class BufferOperation implements TransferOperation {
    private final ByteBuffer buffer;
    private final AsyncByteBufferPool pool;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Intentional zero-copy exposure of buffer")
    public BufferOperation(ByteBuffer buffer, AsyncByteBufferPool pool) {
        this.buffer = buffer;
        this.pool = pool;
    }

    public BufferOperation(ByteBuffer buffer) {
        this(buffer, null);
    }

    @Override
    public boolean transfer(SocketChannel channel) throws IOException {
        channel.write(buffer);
        return !buffer.hasRemaining();
    }

    @Override
    public void release() {
        if (pool != null) {
            pool.releaseAsync(buffer);
        }
    }
}
