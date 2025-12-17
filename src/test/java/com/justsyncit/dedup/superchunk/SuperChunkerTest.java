package com.justsyncit.dedup.superchunk;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SuperChunkerTest {

    @Test
    public void testSuperChunkCreation() {
        SuperChunker chunker = new SuperChunker();
        List<String> chunks = new ArrayList<>();
        // Add 32 chunks
        for (int i = 0; i < 32; i++) {
            chunks.add("chunk" + i);
        }

        List<SuperChunker.SuperChunk> superChunks = chunker.createSuperChunks(chunks);

        // Default size is 16. Should likely get 2 or more super chunks depending on
        // boundary logic.
        // The logic has "forced max size" at divisor * 2 (32).
        // Wait, logic: `if (h.hashCode() % divisor == 0 || currentBatch.size() >=
        // divisor * 2)`
        // Divisor is 16. Max size 32.
        // It depends on hash codes.

        assertFalse(superChunks.isEmpty());

        int totalProcessed = 0;
        for (SuperChunker.SuperChunk sc : superChunks) {
            totalProcessed += sc.getChildChunkHashes().size();
            assertTrue(sc.getChildChunkHashes().size() <= 32);
            assertNotNull(sc.getSignature());
        }
        assertEquals(32, totalProcessed);
    }
}
