package com.justsyncit.storage.retention;

import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service to manage snapshot retention and pruning.
 */
public class RetentionService {
    private static final Logger logger = LoggerFactory.getLogger(RetentionService.class);
    private final MetadataService metadataService;

    public RetentionService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /**
     * Prunes snapshots based on the provided policies.
     * Union of pruned sets from all policies will be deleted.
     *
     * @param policies List of retention policies to apply.
     * @param dryRun   If true, will not actually delete snapshots, just list them.
     * @return List of pruned snapshots (or would-be pruned in dry run).
     * @throws IOException If metadata access fails.
     */
    public List<Snapshot> pruneSnapshots(List<RetentionPolicy> policies, boolean dryRun) throws IOException {
        if (policies == null || policies.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<Snapshot> allSnapshots = metadataService.listSnapshots();
        if (allSnapshots.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Set<String> toPruneIds = new HashSet<>();

        // This logic is slightly ambiguous: "Union of pruned sets" means if ANY policy
        // says "prune this", it gets pruned?
        // Or should valid snapshots be Intersection of "keep sets"?
        // Usually, retention policies are "Keep last N" AND "Keep last X days".
        // Those usually imply "Keep if (A or B)".
        // So "Prune if (!A and !B)".

        // Wait, the plan said: "Usage: snapshots prune --keep-last 5 or snapshots prune
        // --older-than 30d".
        // If I run prune --keep-last 5 --older-than 30d, what does the user expect?
        // Usually "Keep items that satisfy ANY retention policy".
        // So we prune items that satisfy NONE of the "Keep" criteria.

        // But my interface is `getSnapshotsToPrune`.
        // If `AgeRetentionPolicy` says "Prune older than 30d", it returns old
        // snapshots.
        // If `CountRetentionPolicy` says "Prune after 5th", it returns 6th..Nth
        // snapshots.

        // If I use "Union of Pruned", then:
        // Policy A (Keep 5) -> Prunes [6, 7, 8, ... 100] (Assume 100 snapshots, all
        // very old)
        // Policy B (Keep 30 days) -> Prunes [All 100 if all old]

        // If I prune the UNION of "to prune", I might prune something that another
        // policy wanted to keep?
        // Example: Only 2 snapshots exist, both very old.
        // CountPolicy(Keep 5) -> Returns [] (Keep both because count < 5)
        // AgePolicy(Keep 30 days) -> Returns [Snap1, Snap2] (Prune both because old)

        // If I union the prune sets -> I prune both.
        // But CountPolicy implicitly wanted to KEEP them?

        // Implementation DETAIL:
        // Usually users want "Keep last 5 regardless of age" AND "Keep everything newer
        // than 30 days".
        // So a file is KEPT if (Index < 5) OR (Age < 30 days).
        // A file is PRUNED if (Index >= 5) AND (Age >= 30 days).

        // So "getSnapshotsToPrune" logic in policies is slightly tricky if we combine
        // them.
        // My interface definition `getSnapshotsToPrune` is essentially asserting "I
        // want this gone".

        // Let's redefine the Service logic to correspond to standard backup retention
        // expectation:
        // "Retain if it meets ANY policy".
        // So we should calculate the set of snapshots to KEEP for each policy, union
        // them, and prune the rest.

        Set<String> toKeepIds = new HashSet<>();

        for (RetentionPolicy policy : policies) {
            // This is inefficient loop if getSnapshotsToPrune implementation iterates all.
            // But let's invert.
            // A policy creates a "Prune List". The complementary is the "Keep List".

            List<Snapshot> pruneCandidates = policy.getSnapshotsToPrune(allSnapshots);
            Set<String> policyPruneIds = new HashSet<>();
            for (Snapshot s : pruneCandidates) {
                policyPruneIds.add(s.getId());
            }

            for (Snapshot s : allSnapshots) {
                if (!policyPruneIds.contains(s.getId())) {
                    toKeepIds.add(s.getId());
                }
            }
        }

        // Now prune everything NOT in toKeepIds
        List<Snapshot> pruned = new java.util.ArrayList<>();

        for (Snapshot s : allSnapshots) {
            if (!toKeepIds.contains(s.getId())) {
                pruned.add(s);
                if (!dryRun) {
                    logger.info("Pruning snapshot: {} ({})", s.getId(), s.getName());
                    metadataService.deleteSnapshot(s.getId());
                }
            }
        }

        return pruned;
    }
}
