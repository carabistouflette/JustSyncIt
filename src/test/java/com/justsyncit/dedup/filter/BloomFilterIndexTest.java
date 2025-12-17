package com.justsyncit.dedup.filter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BloomFilterIndexTest {

    @Test
    public void testBloomFilterCreateAndCheck() {
        BloomFilterIndex filter = new BloomFilterIndex(1000, 0.01);

        filter.add("item1");
        filter.add("item2");

        assertTrue(filter.mightContain("item1"));
        assertTrue(filter.mightContain("item2"));
        // false positive possible but low probability
    }

    @Test
    public void testFalsePositiveRate() {
        // Statistical test, loosely check
        int n = 10000;
        double p = 0.05;
        BloomFilterIndex filter = new BloomFilterIndex(n, p);

        for (int i = 0; i < n; i++) {
            filter.add("key" + i);
        }

        int falsePositives = 0;
        int trials = 10000;
        for (int i = 0; i < trials; i++) {
            if (filter.mightContain("missing" + i)) {
                falsePositives++;
            }
        }

        double rate = (double) falsePositives / trials;
        // Rate should be close to p (0.05)
        // Allow some variance
        assertTrue(rate < p * 2.0, "False positive rate too high: " + rate);
    }
}
