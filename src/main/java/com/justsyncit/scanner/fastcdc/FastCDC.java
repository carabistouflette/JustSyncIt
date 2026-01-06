package com.justsyncit.scanner.fastcdc;

/**
 * Implementation of the FastCDC (Fast Content-Defined Chunking) algorithm.
 * Uses Gear hashing and a normalized chunking approach.
 */
public class FastCDC {

    // Gear hash table (randomly generated 64-bit integers)
    private static final long[] GEAR_TABLE = new long[256];

    static {
        // Initialize Gear hash table with a deterministic seed for reproducibility
        java.util.Random random = new java.util.Random(0xDEADBEEF);
        for (int i = 0; i < 256; i++) {
            GEAR_TABLE[i] = random.nextLong();
        }
    }

    private final int minSize;
    private final int averageSize;
    private final int maxSize;

    // Masks for different processing stages (Normalized Chunking)
    private final long mask1;
    private final long mask2;

    // private static final int NORMALIZATION_LEVEL = 2; // Default from paper

    // Internal buffer for processing DirectByteBuffers
    private byte[] internalBuffer;

    public FastCDC(int minSize, int averageSize, int maxSize) {
        if (minSize >= averageSize || averageSize >= maxSize) {
            throw new IllegalArgumentException("Invalid chunk sizes: min < average < max is required");
        }
        this.minSize = minSize;
        this.averageSize = averageSize;
        this.maxSize = maxSize;
        this.internalBuffer = new byte[maxSize]; // Initialize with maxSize capacity

        // Calculate masks based on average size
        int bits = 31 - Integer.numberOfLeadingZeros(averageSize);
        this.mask1 = (1L << (bits + 1)) - 1; // More stringent mask for first half
        this.mask2 = (1L << (bits - 1)) - 1; // Less stringent mask for second half
    }

    /**
     * Finds the next chunk boundary in the buffer.
     * 
     * @param data   the data buffer
     * @param offset starting offset
     * @param length available data length
     * @return the number of bytes in the chunk (relative to offset), or 0 if no
     *         boundary found within length
     *         (implying the chunk extends beyond this buffer or reached maxSize)
     */
    public int nextChunk(byte[] data, int offset, int length) {
        // Fast path for when we already have a byte array
        return nextChunkInternal(data, 0, offset, length);
    }

    /**
     * Finds the next chunk boundary in the buffer.
     *
     * @param buffer the data buffer
     * @param offset starting offset (absolute position in buffer)
     * @param length available data length
     * @return the number of bytes in the chunk, or 0 if no boundary found within
     *         length
     */
    public int nextChunk(java.nio.ByteBuffer buffer, int offset, int length) {
        if (length <= minSize) {
            return length;
        }

        byte[] data;
        int arrayBaseOffset;

        if (buffer.hasArray()) {
            data = buffer.array();
            arrayBaseOffset = buffer.arrayOffset();
        } else {
            // Optimization for Direct ByteBuffers: Bulk read into cached byte array
            // We only strictly need bytes from offset + minSize up to limit,
            // but reading from offset simplifies indexing and ensures we have enough
            // context.
            int readLength = Math.min(length, maxSize);

            // Ensure internalBuffer is large enough
            if (internalBuffer.length < readLength) {
                internalBuffer = new byte[readLength];
            }

            // Copy the relevant portion of the ByteBuffer into the internal byte array
            buffer.get(offset, internalBuffer, 0, readLength);
            data = internalBuffer;
            arrayBaseOffset = -offset; // Maps buffer[offset] to data[0]
        }

        return nextChunkInternal(data, arrayBaseOffset, offset, length);
    }

    private int nextChunkInternal(byte[] data, int arrayBaseOffset, int offset, int length) {
        if (length <= minSize) {
            return length;
        }

        // Skip minimum chunk size
        int current = offset + minSize;
        int limit = Math.min(offset + maxSize, offset + length);
        int midPoint = Math.min(offset + averageSize, limit);

        long fp = 0; // fingerprint

        // Stage 1: minSize to averageSize (harder condition)
        while (current < midPoint) {
            fp = (fp << 1) + GEAR_TABLE[data[arrayBaseOffset + current] & 0xFF];
            if ((fp & mask1) == 0) {
                return current - offset + 1;
            }
            current++;
        }

        // Stage 2: averageSize to maxSize (easier condition)
        while (current < limit) {
            fp = (fp << 1) + GEAR_TABLE[data[arrayBaseOffset + current] & 0xFF];
            if ((fp & mask2) == 0) {
                return current - offset + 1;
            }
            current++;
        }

        return current - offset; // Reached limit (maxSize or end of buffer)
    }

}
