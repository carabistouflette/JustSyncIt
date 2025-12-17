package com.justsyncit.command;

import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.snapshot.MerkleTreeDiffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Command to show differences between two snapshots.
 */
public class DiffCommand implements Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiffCommand.class);
    private final MetadataService metadataService;

    public DiffCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public String getName() {
        return "diff";
    }

    @Override
    public String getDescription() {
        return "Show differences between two snapshots";
    }

    @Override
    public String getUsage() {
        return "diff <snapshotId1> [snapshotId2]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (args.length < 1) {
            System.err.println("Usage: " + getUsage());
            return false;
        }

        String snapshotId1 = args[0];
        String snapshotId2 = args.length > 1 ? args[1] : null;

        try {
            // Validate snapshot 1
            if (metadataService.getSnapshot(snapshotId1).isEmpty()) {
                System.err.println("Snapshot not found: " + snapshotId1);
                return false;
            }

            // If snapshot 2 is not provided, try to compare with parent (if incremental)
            // But MerkleNode logic handles explicit comparison easily.
            // If user wants to compare with "previous", they usually know it or we look it
            // up.
            // For now, if snapshotId2 is null, we can try to find the "parent" of
            // snapshotId1?
            // Assuming we stored parentId? We store "description" mainly.
            // Let's stick to explicit comparison or imply "previous in time" if 2nd arg
            // missing?
            // Simpler: require 2 args or if 1 arg, compare with its PARENT if we can find
            // it,
            // OR compare with NOTHING (show all files as added)?
            // Let's implement: if 2nd arg missing, error for now "Please specify source and
            // target".
            // Actually, "diff OLD NEW" is standard.

            if (snapshotId2 == null) {
                System.err.println("Usage: " + getUsage());
                System.err.println("Please provide two snapshot IDs to compare.");
                return false;
            }

            if (metadataService.getSnapshot(snapshotId2).isEmpty()) {
                System.err.println("Snapshot not found: " + snapshotId2);
                return false;
            }

            System.out.println("Comparing " + snapshotId1 + " -> " + snapshotId2);

            List<MerkleTreeDiffer.DiffEntry> diffs = metadataService.compareSnapshots(snapshotId1, snapshotId2);

            if (diffs.isEmpty()) {
                System.out.println("No differences found.");
            } else {
                for (MerkleTreeDiffer.DiffEntry entry : diffs) {
                    String prefix = "";
                    switch (entry.getType()) {
                        case ADDED:
                            prefix = "+";
                            break;
                        case DELETED:
                            prefix = "-";
                            break;
                        case MODIFIED:
                            prefix = "M";
                            break;
                    }
                    System.out.println(String.format("%s %s", prefix, entry.getPath()));
                }
            }
            return true;

        } catch (IOException e) {
            handleError("Failed to perform diff", e, LOGGER);
            return false;
        }
    }
}
