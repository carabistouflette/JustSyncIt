package com.justsyncit.storage.retention;

import com.justsyncit.storage.metadata.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Retention policy that keeps a specific number of the most recent snapshots.
 */
public class CountRetentionPolicy implements RetentionPolicy {
    private final int keepCount;

    public CountRetentionPolicy(int keepCount) {
        if (keepCount < 1) {
            throw new IllegalArgumentException("keepCount must be at least 1");
        }
        this.keepCount = keepCount;
    }

    @Override
    public List<Snapshot> getSnapshotsToPrune(List<Snapshot> snapshots) {
        if (snapshots == null || snapshots.size() <= keepCount) {
            return new ArrayList<>();
        }

        // Sort by creation time descending (newest first)
        List<Snapshot> sortedSnapshots = snapshots.stream()
                .sorted(Comparator.comparing(Snapshot::getCreatedAt).reversed())
                .collect(Collectors.toList());

        // The ones to prune are from index 'keepCount' onwards
        return sortedSnapshots.subList(keepCount, sortedSnapshots.size());
    }
}
