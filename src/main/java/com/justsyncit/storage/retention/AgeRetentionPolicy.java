package com.justsyncit.storage.retention;

import com.justsyncit.storage.metadata.Snapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Retention policy that keeps snapshots newer than a specific age (in days).
 */
public class AgeRetentionPolicy implements RetentionPolicy {
    private final int retentionDays;

    public AgeRetentionPolicy(int retentionDays) {
        if (retentionDays < 0) {
            throw new IllegalArgumentException("retentionDays must be non-negative");
        }
        this.retentionDays = retentionDays;
    }

    @Override
    public List<Snapshot> getSnapshotsToPrune(List<Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        return snapshots.stream()
                .filter(s -> s.getCreatedAt().isBefore(cutoff))
                .collect(Collectors.toList());
    }
}
