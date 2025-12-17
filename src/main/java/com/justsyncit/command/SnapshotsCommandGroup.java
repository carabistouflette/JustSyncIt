/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.command;

/**
 * Command group for snapshot management operations.
 * This command delegates to specific snapshot subcommands.
 * Follows Composite Pattern by grouping related commands together.
 */
public class SnapshotsCommandGroup implements Command {

    private static final String SUBCOMMAND_LIST = "list";
    private static final String SUBCOMMAND_INFO = "info";
    private static final String SUBCOMMAND_DELETE = "delete";
    private static final String SUBCOMMAND_VERIFY = "verify";
    private static final String SUBCOMMAND_VERIFY_CHAIN = "verify-chain";
    private static final String SUBCOMMAND_PRUNE = "prune";
    private static final String SUBCOMMAND_ROLLBACK = "rollback";

    private final SnapshotsListCommand listCommand;
    private final SnapshotsInfoCommand infoCommand;
    private final SnapshotsDeleteCommand deleteCommand;
    private final SnapshotsVerifyCommand verifyCommand;
    private final SnapshotsVerifyChainCommand verifyChainCommand;
    private final SnapshotsPruneCommand pruneCommand;
    private final SnapshotsRollbackCommand rollbackCommand;

    /**
     * Creates a snapshots command group.
     */
    public SnapshotsCommandGroup() {
        this.listCommand = new SnapshotsListCommand(null);
        this.infoCommand = new SnapshotsInfoCommand(null);
        this.deleteCommand = new SnapshotsDeleteCommand(null);
        this.verifyCommand = new SnapshotsVerifyCommand(null);
        this.verifyChainCommand = new SnapshotsVerifyChainCommand(null);
        this.pruneCommand = new SnapshotsPruneCommand(null);
        this.rollbackCommand = new SnapshotsRollbackCommand(null);
    }

    @Override
    public String getName() {
        return "snapshots";
    }

    @Override
    public String getDescription() {
        return "Manage backup snapshots (list, info, delete, verify, prune, rollback)";
    }

    @Override
    public String getUsage() {
        return "snapshots <subcommand> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Check for help first
        if (isHelpRequested(args)) {
            displayHelp();
            return true;
        }

        if (args.length == 0) {
            displayMissingSubcommandError();
            return false;
        }

        String subcommand = args[0];
        String[] subcommandArgs = java.util.Arrays.copyOfRange(args, 1, args.length);

        switch (subcommand) {
            case SUBCOMMAND_LIST:
                return listCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_INFO:
                return infoCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_DELETE:
                return deleteCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_VERIFY:
                return verifyCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_VERIFY_CHAIN:
                return verifyChainCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_PRUNE:
                return pruneCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_ROLLBACK:
                return rollbackCommand.execute(subcommandArgs, context);
            case "help":
                displayHelp();
                return true;
            default:
                displayUnknownSubcommandError(subcommand);
                return false;
        }
    }

    /**
     * Displays error message for missing subcommand.
     */
    private void displayMissingSubcommandError() {
        System.err.println("Error: Missing subcommand");
        displayAvailableSubcommands();
    }

    /**
     * Displays error message for unknown subcommand.
     *
     * @param subcommand the unknown subcommand
     */
    private void displayUnknownSubcommandError(String subcommand) {
        System.err.println("Error: Unknown subcommand: " + subcommand);
        displayAvailableSubcommands();
    }

    /**
     * Displays the list of available subcommands.
     */
    private void displayAvailableSubcommands() {
        System.err.println("Available subcommands: " + SUBCOMMAND_LIST + ", "
                + SUBCOMMAND_INFO + ", " + SUBCOMMAND_DELETE + ", " + SUBCOMMAND_VERIFY + ", "
                + SUBCOMMAND_VERIFY_CHAIN + ", " + SUBCOMMAND_PRUNE + ", " + SUBCOMMAND_ROLLBACK);
        System.err.println("Use 'help snapshots' for more information");
    }

    /**
     * Displays detailed help information for the snapshots command group.
     */
    private void displayHelp() {
        System.out.println("Snapshots Command Group Help");
        System.out.println("=============================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Subcommands:");
        System.out.println("  " + SUBCOMMAND_LIST + "        List all available snapshots");
        System.out.println("  " + SUBCOMMAND_INFO + "        Show detailed information about a specific snapshot");
        System.out.println("  " + SUBCOMMAND_DELETE + "      Delete a specific snapshot");
        System.out.println("  " + SUBCOMMAND_VERIFY + "      Verify integrity of a snapshot");
        System.out.println("  " + SUBCOMMAND_VERIFY_CHAIN + " Verify the integrity of a snapshot chain");
        System.out.println("  " + SUBCOMMAND_PRUNE + "       Prune snapshots based on retention policies");
        System.out.println("  " + SUBCOMMAND_ROLLBACK + "    Rollback directory to snapshot state (DESTRUCTIVE)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  snapshots " + SUBCOMMAND_LIST);
        System.out.println("  snapshots " + SUBCOMMAND_INFO + " abc123-def456");
        System.out.println("  snapshots " + SUBCOMMAND_DELETE + " abc123-def456");
        System.out.println("  snapshots " + SUBCOMMAND_VERIFY + " abc123-def456");
        System.out.println("  snapshots " + SUBCOMMAND_VERIFY_CHAIN + " abc123-def456");
        System.out.println("  snapshots " + SUBCOMMAND_PRUNE + " --keep-last 5 --older-than 30d");
        System.out.println("  snapshots " + SUBCOMMAND_ROLLBACK + " abc123-def456 --target /path/to/restore");
        System.out.println();
        System.out.println("For detailed help on a specific subcommand, use:");
        System.out.println("  help snapshots " + SUBCOMMAND_LIST);
        System.out.println("  help snapshots " + SUBCOMMAND_INFO);
        System.out.println("  help snapshots " + SUBCOMMAND_DELETE);
        System.out.println("  help snapshots " + SUBCOMMAND_VERIFY);
        System.out.println("  help snapshots " + SUBCOMMAND_VERIFY_CHAIN);
        System.out.println("  help snapshots " + SUBCOMMAND_PRUNE);
        System.out.println("  help snapshots " + SUBCOMMAND_ROLLBACK);
    }

    private static class SnapshotsRollbackCommand implements Command {
        private final com.justsyncit.restore.RestoreService restoreService;

        public SnapshotsRollbackCommand(com.justsyncit.restore.RestoreService restoreService) {
            this.restoreService = restoreService;
        }

        @Override
        public String getName() {
            return "rollback";
        }

        @Override
        public String getDescription() {
            return "Rollback directory to snapshot state (DESTRUCTIVE)";
        }

        @Override
        public String getUsage() {
            return "snapshots rollback <snapshotId> [--target <path>] [--dry-run]";
        }

        @Override
        public boolean execute(String[] args, CommandContext context) {
            if (args.length < 1) {
                System.err.println("Usage: " + getUsage());
                return false;
            }

            com.justsyncit.restore.RestoreService service = this.restoreService;
            if (service == null) {
                service = context.getRestoreService();
            }

            if (service == null) {
                handleError("Restore service not available",
                        new IllegalStateException("Restore service not initialized"), null);
                return false;
            }

            String snapshotId = args[0];
            String targetPath = null;
            boolean dryRun = false;

            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if ("--target".equals(arg)) {
                    if (i + 1 < args.length) {
                        targetPath = args[++i];
                    } else {
                        System.err.println("Missing value for --target");
                        return false;
                    }
                } else if ("--dry-run".equals(arg)) {
                    dryRun = true;
                }
            }

            if (targetPath == null) {
                // Infer from snapshot?
                // The service logic tries to infer root, but rollback usually requires explicit
                // target for safety?
                // Or we can pass null and let service fail if it can't determine.
                // However, RestoreService.rollback takes Path targetDirectory.
                // I'll require it for safety unless we want to parse snapshot desc here.
                // Let's require it to be safe.
                System.err.println("Error: --target <path> is required for rollback.");
                return false;
            }

            try {
                System.out.println("Initiating rollback to snapshot: " + snapshotId);
                System.out.println("Target: " + targetPath);
                if (dryRun)
                    System.out.println("(DRY RUN MODE)");
                else
                    System.out.println("WARNING: This will DELETE extraneous files in target directory!");

                com.justsyncit.restore.RestoreOptions options = new com.justsyncit.restore.RestoreOptions();
                options.setDryRun(dryRun);

                java.nio.file.Path targetDir = java.nio.file.Paths.get(targetPath);

                java.util.concurrent.CompletableFuture<com.justsyncit.restore.RestoreService.RestoreResult> future = service
                        .rollback(snapshotId, targetDir, options);

                com.justsyncit.restore.RestoreService.RestoreResult result = future.join();

                if (result.isSuccess()) {
                    System.out.println("Rollback successful!");
                } else {
                    System.err.println("Rollback completed with errors.");
                }
                return result.isSuccess();

            } catch (Exception e) {
                handleError("Rollback failed", e, null);
                return false;
            }
        }
    }

    private static class SnapshotsVerifyChainCommand implements Command {
        private final com.justsyncit.storage.metadata.MetadataService metadataService;

        public SnapshotsVerifyChainCommand(com.justsyncit.storage.metadata.MetadataService metadataService) {
            this.metadataService = metadataService;
        }

        @Override
        public String getName() {
            return "verify-chain";
        }

        @Override
        public String getDescription() {
            return "Verify the integrity of a snapshot chain";
        }

        @Override
        public String getUsage() {
            return "snapshots verify-chain <snapshotId>";
        }

        @Override
        public boolean execute(String[] args, CommandContext context) {
            com.justsyncit.storage.metadata.MetadataService service = this.metadataService;

            if (service == null) {
                service = context.getMetadataService();
            }

            if (service == null) {
                try {
                    handleError("Metadata service not available",
                            new IllegalStateException("Metadata service not initialized"), null);
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }

            if (args.length < 1) {
                System.err.println("Usage: " + getUsage());
                return false;
            }
            String snapshotId = args[0];
            System.out.println("Verifying chain for snapshot: " + snapshotId);

            try {
                boolean valid = service.validateSnapshotChain(snapshotId);
                if (valid) {
                    System.out.println("Snapshot chain is VALID.");
                    return true;
                } else {
                    System.err.println("Snapshot chain is INVALID.");
                    return false;
                }
            } catch (java.io.IOException e) {
                handleError("Verification failed", e, null);
                return false;
            }
        }
    }

    private static class SnapshotsPruneCommand implements Command {
        private final com.justsyncit.storage.metadata.MetadataService metadataService;

        public SnapshotsPruneCommand(com.justsyncit.storage.metadata.MetadataService metadataService) {
            this.metadataService = metadataService;
        }

        @Override
        public String getName() {
            return "prune";
        }

        @Override
        public String getDescription() {
            return "Prune snapshots based on retention policies";
        }

        @Override
        public String getUsage() {
            return "snapshots prune [--keep-last <N>] [--older-than <N>d] [--dry-run]";
        }

        @Override
        public boolean execute(String[] args, CommandContext context) {
            com.justsyncit.storage.metadata.MetadataService service = this.metadataService;
            if (service == null) {
                service = context.getMetadataService();
            }
            if (service == null) {
                try {
                    handleError("Metadata service not available",
                            new IllegalStateException("Metadata service not initialized"), null);
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }

            // Parse args
            Integer keepLast = null;
            Integer olderThanDays = null;
            boolean dryRun = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--dry-run".equals(arg)) {
                    dryRun = true;
                } else if ("--keep-last".equals(arg)) {
                    if (i + 1 < args.length) {
                        try {
                            keepLast = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid number for --keep-last");
                            return false;
                        }
                    } else {
                        System.err.println("Missing value for --keep-last");
                        return false;
                    }
                } else if ("--older-than".equals(arg)) {
                    if (i + 1 < args.length) {
                        String val = args[++i];
                        if (val.endsWith("d")) {
                            val = val.substring(0, val.length() - 1);
                        }
                        try {
                            olderThanDays = Integer.parseInt(val);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid number for --older-than");
                            return false;
                        }
                    } else {
                        System.err.println("Missing value for --older-than");
                        return false;
                    }
                }
            }

            if (keepLast == null && olderThanDays == null) {
                System.err.println("At least one retention policy must be specified.");
                System.err.println("Usage: " + getUsage());
                return false;
            }

            try {
                java.util.List<com.justsyncit.storage.retention.RetentionPolicy> policies = new java.util.ArrayList<>();
                if (keepLast != null) {
                    policies.add(new com.justsyncit.storage.retention.CountRetentionPolicy(keepLast));
                }
                if (olderThanDays != null) {
                    policies.add(new com.justsyncit.storage.retention.AgeRetentionPolicy(olderThanDays));
                }

                com.justsyncit.storage.retention.RetentionService retentionService = new com.justsyncit.storage.retention.RetentionService(
                        service);

                System.out.println("Running pruning..." + (dryRun ? " (DRY RUN)" : ""));
                java.util.List<com.justsyncit.storage.metadata.Snapshot> pruned = retentionService
                        .pruneSnapshots(policies, dryRun);

                System.out.println("Pruned " + pruned.size() + " snapshots.");
                if (dryRun) {
                    for (com.justsyncit.storage.metadata.Snapshot s : pruned) {
                        System.out.println(" - " + s.getId() + " (" + s.getName() + ", " + s.getCreatedAt() + ")");
                    }
                }
                return true;

            } catch (java.io.IOException e) {
                handleError("Pruning failed", e, null);
                return false;
            }
        }
    }
}