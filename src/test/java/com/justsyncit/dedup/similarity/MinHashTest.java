package com.justsyncit.dedup.similarity;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MinHashTest {

    @Test
    public void testMinHashSignatureCreation() {
        MinHash minHash = new MinHash(100);
        Set<Integer> set1 = new HashSet<>();
        set1.add(1);
        set1.add(2);
        set1.add(3);

        long[] sig1 = minHash.computeSignature(set1);
        assertEquals(100, sig1.length);

        for (long s : sig1) {
            assertTrue(s >= 0);
        }
    }

    @Test
    public void testExactSimilarity() {
        MinHash minHash = new MinHash();
        Set<Integer> set1 = new HashSet<>();
        set1.add(10);
        set1.add(20);
        set1.add(30);

        long[] sig1 = minHash.computeSignature(set1);
        long[] sig2 = minHash.computeSignature(set1); // Same set

        assertEquals(1.0, minHash.similarity(sig1, sig2), 0.001);
    }

    @Test
    public void testNoSimilarity() {
        MinHash minHash = new MinHash(200); // Higher count for better precision if needed, but 0 should be exact for
                                            // disjoint
        Set<Integer> set1 = new HashSet<>();
        set1.add(1);
        set1.add(2);

        Set<Integer> set2 = new HashSet<>();
        set2.add(3);
        set2.add(4);

        long[] sig1 = minHash.computeSignature(set1);
        long[] sig2 = minHash.computeSignature(set2);

        // Probability of collision exists but is very low with 128 functions and random
        // coefficients
        // for disjoint small sets. But stricly speaking, MinHash *estimates*
        // similarity.
        // For disjoint sets it *should* be 0 unless hash collision.
        // With MODULUS ~4B, collision is rare.

        assertEquals(0.0, minHash.similarity(sig1, sig2), 0.05);
    }

    @Test
    public void testPartialSimilarity() {
        // Sets with 50% overlap (Jaccard = 0.5 / (1.0 + 0.5 - 0.5) if union?
        // J(A,B) = |A int B| / |A union B|
        // A={1,2,3,4}, B={3,4,5,6} -> Int={3,4} (2), Union={1,2,3,4,5,6} (6) -> J = 1/3
        // = 0.333...

        MinHash minHash = new MinHash(500); // Use enough hashes for statistical stability

        Set<Integer> set1 = new HashSet<>();
        for (int i = 0; i < 40; i++)
            set1.add(i); // 0-39

        Set<Integer> set2 = new HashSet<>();
        for (int i = 20; i < 60; i++)
            set2.add(i); // 20-59

        // Intersection: 20-39 (20 items)
        // Union: 0-59 (60 items)
        // Expected Jaccard: 20/60 = 1/3 ~= 0.333

        long[] sig1 = minHash.computeSignature(set1);
        long[] sig2 = minHash.computeSignature(set2);

        double sim = minHash.similarity(sig1, sig2);
        System.out.println("Estimated similarity: " + sim);

        assertEquals(1.0 / 3.0, sim, 0.15); // Allow 15% error margin
    }
}
