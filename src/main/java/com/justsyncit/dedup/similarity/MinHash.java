package com.justsyncit.dedup.similarity;

import java.util.Random;
import java.util.Set;

/**
 * Implements MinHash algorithm for estimating Jaccard similarity between sets.
 * Uses a family of universal hash functions.
 */
public class MinHash {

    private final int numHashFunctions;
    private final int[] seeds;

    /**
     * Creates a MinHash instance with the default number of hash functions (128).
     */
    public MinHash() {
        this(128);
    }

    /**
     * Creates a MinHash instance with the specified number of hash functions.
     *
     * @param numHashFunctions the number of hash functions to use for the
     *                         signature.
     */
    public MinHash(int numHashFunctions) {
        if (numHashFunctions <= 0) {
            throw new IllegalArgumentException("Number of hash functions must be positive");
        }
        this.numHashFunctions = numHashFunctions;
        this.seeds = new int[numHashFunctions];

        // Initialize seeds
        Random r = new Random(0x5EED);
        for (int i = 0; i < numHashFunctions; i++) {
            seeds[i] = r.nextInt();
        }
    }

    /**
     * Computes the MinHash signature for a set of integer tokens.
     *
     * @param elements the set of elements (hashed as integers).
     * @return the MinHash signature as an array of longs.
     */
    public long[] computeSignature(Set<Integer> elements) {
        long[] signature = new long[numHashFunctions];

        // Initialize signature with max value
        for (int i = 0; i < numHashFunctions; i++) {
            signature[i] = Long.MAX_VALUE;
        }

        if (elements == null || elements.isEmpty()) {
            return signature;
        }

        for (Integer element : elements) {
            for (int i = 0; i < numHashFunctions; i++) {
                // Use XOR + Mix to simulate random permutation
                // Treating result as unsigned 32-bit int, then promoting to long
                long hash = fmix32(element ^ seeds[i]) & 0xFFFFFFFFL;
                if (hash < signature[i]) {
                    signature[i] = hash;
                }
            }
        }
        return signature;
    }

    /**
     * MurmurHash3 32-bit finalizer to mix input bits.
     */
    private static int fmix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    /**
     * Estimates the Jaccard similarity between two signatures.
     *
     * @param sig1 the first signature.
     * @param sig2 the second signature.
     * @return the estimated Jaccard similarity (0.0 to 1.0).
     */
    public double similarity(long[] sig1, long[] sig2) {
        if (sig1.length != sig2.length) {
            throw new IllegalArgumentException("Signatures must have the same length");
        }

        if (sig1.length == 0)
            return 0.0;

        int matchCount = 0;
        for (int i = 0; i < sig1.length; i++) {
            if (sig1[i] == sig2[i]) {
                matchCount++;
            }
        }

        return (double) matchCount / sig1.length;
    }

    /**
     * Helper to hash a string to an integer for use in computeSignature.
     */
    public static int hashString(String s) {
        // Using MurmurHash3 like finalizer or simply hashCode for simplicity in this
        // helper
        // Ideally use a robust hash
        return s.hashCode();
    }
}
