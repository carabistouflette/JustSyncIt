package com.justsyncit.backup.cbt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

class BlockBitmapTest {

    @Test
    void testBitmapCreation() {
        // 1MB file -> 256 blocks of 4KB
        BlockBitmap bitmap = new BlockBitmap(1024 * 1024);
        assertEquals(256, bitmap.getTotalBlocks());
        assertFalse(bitmap.isChanged(0));
    }

    @Test
    void testMarkChanged() {
        BlockBitmap bitmap = new BlockBitmap(4096 * 10); // 10 blocks
        bitmap.markChanged(5);
        assertTrue(bitmap.isChanged(5));
        assertFalse(bitmap.isChanged(4));
        assertFalse(bitmap.isChanged(6));
    }

    @Test
    void testMarkRangeChanged() {
        BlockBitmap bitmap = new BlockBitmap(4096 * 10);
        // Mark bytes 4096 to 8191 (Block 1)
        bitmap.markRangeChanged(4096, 4096);
        assertTrue(bitmap.isChanged(1));
        assertFalse(bitmap.isChanged(0));
        assertFalse(bitmap.isChanged(2));

        // Mark crossing boundary: 8190 to 8194 (Block 1 and 2)
        bitmap.markRangeChanged(8190, 4);
        assertTrue(bitmap.isChanged(1));
        assertTrue(bitmap.isChanged(2));
    }

    @Test
    void testSerializationRoundTripEmpty() {
        BlockBitmap original = new BlockBitmap(1024 * 1024);
        byte[] data = original.serialize();

        BlockBitmap loaded = BlockBitmap.deserialize(data);
        assertEquals(original.getTotalBlocks(), loaded.getTotalBlocks());
        for (int i = 0; i < original.getTotalBlocks(); i++) {
            assertFalse(loaded.isChanged(i));
        }
    }

    @Test
    void testSerializationRoundTripSparse() {
        BlockBitmap original = new BlockBitmap(1024 * 1024 * 10); // 10MB
        original.markChanged(0);
        original.markChanged(100);
        original.markChanged(2559);

        byte[] data = original.serialize();
        // Check reasonable compression: 2560 blocks. Uncompressed bitset ~320 bytes.
        // RLE: 0 (True, 1), 1 (False, 99), 100 (True, 1)...
        // We expect it to be larger than bitset for random access but smaller for large
        // sparse files?
        // Actually RLE is best for runs.

        BlockBitmap loaded = BlockBitmap.deserialize(data);
        assertTrue(loaded.isChanged(0));
        assertTrue(loaded.isChanged(100));
        assertTrue(loaded.isChanged(2559));
        assertFalse(loaded.isChanged(1));
    }

    @Test
    void testSerializationRoundTripFull() {
        BlockBitmap original = new BlockBitmap(4096 * 100);
        original.markAllChanged();

        byte[] data = original.serialize();
        BlockBitmap loaded = BlockBitmap.deserialize(data);

        for (int i = 0; i < 100; i++) {
            assertTrue(loaded.isChanged(i));
        }
    }
}
