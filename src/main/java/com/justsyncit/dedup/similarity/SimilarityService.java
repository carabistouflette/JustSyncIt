package com.justsyncit.dedup.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service to manage MinHash signatures and perform similarity searches.
 */
public class SimilarityService {

    private final MinHash minHash;
    // In-memory index: Signature ID -> Signature
    // Ideally this would be an LSH (Locality Sensitive Hashing) index for
    // efficiency
    // But for "Standard" MinHash search in this scope, linear scan or simple LSH is
    // implied.
    // Let's us a simple Map for now, can be optimized with LSH later.
    private final Map<String, long[]> index;

    public SimilarityService() {
        this.minHash = new MinHash();
        this.index = new ConcurrentHashMap<>();
    }

    public SimilarityService(int numHashFunctions) {
        this.minHash = new MinHash(numHashFunctions);
        this.index = new ConcurrentHashMap<>();
    }

    /**
     * Computes the signature for a set of string features (e.g. chunk hashes).
     */
    public long[] computeSignature(List<String> features) {
        Set<Integer> hashedFeatures = features.stream()
                .map(MinHash::hashString)
                .collect(Collectors.toSet());
        return minHash.computeSignature(hashedFeatures);
    }

    /**
     * Index a file/item by its ID and signature.
     */
    public void indexItem(String id, long[] signature) {
        index.put(id, signature);
    }

    /**
     * Find items similar to the given signature above a threshold.
     * 
     * @param querySignature signature to search for
     * @param threshold      Jaccard similarity threshold (0.0 to 1.0)
     * @return List of IDs of similar items
     */
    public List<String> findSimilar(long[] querySignature, double threshold) {
        List<String> results = new ArrayList<>();

        for (Map.Entry<String, long[]> entry : index.entrySet()) {
            double sim = minHash.similarity(querySignature, entry.getValue());
            if (sim >= threshold) {
                results.add(entry.getKey());
            }
        }
        return results;
    }

    public int getIndexSize() {
        return index.size();
    }
}
