package com.justsyncit.storage.retention;

import com.justsyncit.storage.metadata.Snapshot;
import java.util.List;

/**
 * Interface for defining snapshot retention policies.
 */
public interface RetentionPolicy {
    /**
     * Identifies which snapshots should be pruned (deleted) based on the policy.
     *
     * @param snapshots The list of all available snapshots.
     * @return A list of snapshots that should be pruned.
     */
    List<Snapshot> getSnapshotsToPrune(List<Snapshot> snapshots);
}
