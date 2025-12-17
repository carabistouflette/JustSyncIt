package com.justsyncit.command;

import com.justsyncit.dedup.dashboard.DedupMetricsService;
import com.justsyncit.dedup.dashboard.DedupMetricsService.DedupStats;

public class DedupStatsCommand implements Command {

    @Override
    public String getName() {
        return "dedup-stats";
    }

    @Override
    public String getDescription() {
        return "Show deduplication statistics dashboard";
    }

    @Override
    public String getUsage() {
        return "dedup-stats";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (isHelpRequested(args)) {
            System.out.println(getUsage());
            return true;
        }

        DedupStats stats = DedupMetricsService.getInstance().getStats();

        System.out.println("=== Deduplication Dashboard ===");
        System.out.println();
        System.out.printf("Total Processed:    %10s (%d bytes)%n", formatSize(stats.totalBytes()), stats.totalBytes());
        System.out.printf("Unique Stored:      %10s (%d bytes)%n", formatSize(stats.uniqueBytes()),
                stats.uniqueBytes());
        System.out.printf("Savings:            %10s (%d bytes)%n", formatSize(stats.savedBytes()), stats.savedBytes());
        System.out.println();
        System.out.printf("Chunks Processed:   %10d%n", stats.totalChunks());
        System.out.printf("Unique Chunks:      %10d%n", stats.uniqueChunks());
        System.out.println();
        System.out.printf("Deduplication Ratio: %.2fx%n", stats.dedupRatio());
        System.out.printf("Storage Savings:     %.2f%%%n", stats.savingsPercentage());
        System.out.println("===============================");

        return true;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
