package com.justsyncit.dedup.filter;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Bloom Filter implementation for probabilistic membership testing.
 * efficient memory usage for checking if a chunk/file likely exists.
 */
public class BloomFilterIndex {

    private final BitSet bitSet;
    private final int size;
    private final int numHashFunctions;
    private int count;

    /**
     * Creates a Bloom Filter.
     * 
     * @param expectedInsertions       estimated number of elements to be inserted.
     * @param falsePositiveProbability desired false positive rate (e.g. 0.01).
     */
    public BloomFilterIndex(int expectedInsertions, double falsePositiveProbability) {
        // Optimal size m = - (n * ln(p)) / (ln(2)^2)
        this.size = (int) Math
                .ceil(-1 * (expectedInsertions * Math.log(falsePositiveProbability)) / Math.pow(Math.log(2), 2));

        // Optimal k = (m/n) * ln(2)
        this.numHashFunctions = (int) Math.ceil(((double) size / expectedInsertions) * Math.log(2));

        this.bitSet = new BitSet(size);
        this.count = 0;
    }

    public void add(String item) {
        int[] hashes = createHashes(item, numHashFunctions);
        for (int hash : hashes) {
            bitSet.set(hash % size); // hash is already positive from createHashes
        }
        count++;
    }

    public boolean mightContain(String item) {
        int[] hashFunctions = createHashes(item, numHashFunctions);
        for (int hash : hashFunctions) {
            if (!bitSet.get(hash)) {
                return false;
            }
        }
        // If we are here, it might be a duplicate
        // For metrics demo purposes, we can count this as a "hit"
        // But we don't know the size here.
        return true;
    }

    public int getCount() {
        return count;
    }

    // Double Hashing (Kirsch-Mitzenmacher optimization)
    private int[] createHashes(String item, int k) {
        int[] result = new int[k];
        long hash1 = hash64(item, 0);
        long hash2 = hash64(item, hash1);

        for (int i = 0; i < k; i++) {
            long combined = hash1 + (i * hash2);
            // Flip to positive index
            result[i] = (int) (combined & 0x7FFFFFFF);
        }
        return result;
    }

    // MurmurHash3 64-bit Mix function (simplified variant for speed/single file)
    private long hash64(String item, long seed) {
        long h = seed;
        byte[] data = item.getBytes(StandardCharsets.UTF_8);

        // Simple robust mix for bytes
        for (byte b : data) {
            h ^= (b & 0xFF);
            h *= 0xc6a4a7935bd1e995L; // Murmur multiplier
            h ^= h >>> 47;
        }

        // Finalize
        h *= 0xc6a4a7935bd1e995L;
        h ^= h >>> 47;
        return h;
    }
}
