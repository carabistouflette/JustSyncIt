package com.justsyncit.command;

import com.justsyncit.integrity.service.IntegrityCheckService;
import com.justsyncit.integrity.service.IntegrityCheckService.IntegrityCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for managing data integrity.
 * Supports checking and repairing storage integrity.
 */
public class IntegrityCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(IntegrityCommand.class);

    private final IntegrityCheckService integrityCheckService;

    public IntegrityCommand(IntegrityCheckService integrityCheckService) {
        this.integrityCheckService = integrityCheckService;
    }

    @Override
    public String getName() {
        return "integrity";
    }

    @Override
    public String getDescription() {
        return "Manage data integrity (check, repair, parity)";
    }

    @Override
    public String getUsage() {
        return "integrity <action>";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (args.length < 1) {
            System.err.println("Usage: " + getUsage());
            System.err.println("Actions: check");
            return false;
        }

        String action = args[0];

        switch (action) {
            case "check":
                return runCheck();
            default:
                System.err.println("Unknown integrity action: " + action);
                return false;
        }
    }

    private boolean runCheck() {
        System.out.println("Starting integrity check...");
        System.out.println("Scanning all chunks and verifying checksums...");
        System.out.println("This may take a while depending on storage size.");

        try {
            IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();

            System.out.println("\nIntegrity Check Completed");
            System.out.println("-------------------------");
            System.out.println("Duration: " + result.durationMs + " ms");
            System.out.println("Chunks Checked: " + result.checkedChunks);
            System.out.println("Corrupt/Missing Chunks: " + result.failedChunks + " (Repairs attempted)");
            System.out.println("New Parity Groups Created: " + result.parityGroupsCreated);

            if (result.failedChunks > 0) {
                System.out.println("\nWARNING: Some chunks were found to be corrupt.");
                System.out.println("Check logs for details on repair success/failure.");
            } else {
                System.out.println("\nAll chunks verified successfully.");
            }

            return true;
        } catch (Exception e) {
            logger.error("Integrity check failed with exception", e);
            System.err.println("Integrity check failed: " + e.getMessage());
            return false;
        }
    }
}
