package com.justsyncit.dedup.similarity;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SimilarityServiceTest {

    @Test
    public void testSimilarityIndexingAndSearch() {
        SimilarityService service = new SimilarityService();

        List<String> fileA = Arrays.asList("chunk1", "chunk2", "chunk3", "chunk4");
        List<String> fileB = Arrays.asList("chunk1", "chunk2", "chunk3", "chunk5"); // 75% similar
        List<String> fileC = Arrays.asList("chunkA", "chunkB", "chunkC", "chunkD"); // Dissimilar

        long[] sigA = service.computeSignature(fileA);
        long[] sigB = service.computeSignature(fileB);
        long[] sigC = service.computeSignature(fileC);

        service.indexItem("fileA", sigA);
        service.indexItem("fileB", sigB);
        service.indexItem("fileC", sigC);

        // Search for similar to fileA
        List<String> similarToA = service.findSimilar(sigA, 0.5);
        assertTrue(similarToA.contains("fileA")); // Self
        assertTrue(similarToA.contains("fileB")); // Similar
        assertFalse(similarToA.contains("fileC")); // Dissimilar

        // Search for similar to fileC
        List<String> similarToC = service.findSimilar(sigC, 0.5);
        assertTrue(similarToC.contains("fileC"));
        assertFalse(similarToC.contains("fileA"));
    }
}
