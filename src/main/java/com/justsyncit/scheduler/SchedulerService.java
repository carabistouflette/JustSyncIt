/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justsyncit.backup.BackupOptions;
import com.justsyncit.backup.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing automated backup schedules.
 */
public class SchedulerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerService.class.getName());
    private static final String STORAGE_FILE = "storage/schedules.json";

    private final BackupService backupService;
    private final ScheduledExecutorService executorService;
    private final Map<String, BackupSchedule> schedules;
    private final Map<String, ScheduledFuture<?>> activeTasks;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running;

    public SchedulerService(BackupService backupService) {
        this.backupService = backupService;
        this.executorService = Executors.newScheduledThreadPool(2); // Initial pool
        this.schedules = new ConcurrentHashMap<>();
        this.activeTasks = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting SchedulerService...");
            loadSchedules();
            rescheduleAll();
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping SchedulerService...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    public List<BackupSchedule> getSchedules() {
        return new ArrayList<>(schedules.values());
    }

    public BackupSchedule getSchedule(String id) {
        return schedules.get(id);
    }

    public BackupSchedule createSchedule(BackupSchedule schedule) {
        if (schedule.getId() == null) {
            schedule.setId(UUID.randomUUID().toString());
        }
        schedule.setCreatedAt(Instant.now());

        schedules.put(schedule.getId(), schedule);
        saveSchedules();
        scheduleTask(schedule);

        LOGGER.info("Created schedule: " + schedule.getId());
        return schedule;
    }

    public void deleteSchedule(String id) {
        BackupSchedule removed = schedules.remove(id);
        if (removed != null) {
            cancelTask(id);
            saveSchedules();
            LOGGER.info("Deleted schedule: " + id);
        }
    }

    private void scheduleTask(BackupSchedule schedule) {
        cancelTask(schedule.getId()); // Cancel existing if any

        if (!schedule.isEnabled()) {
            return;
        }

        long initialDelay = 0;
        long period = 0;
        TimeUnit unit = TimeUnit.MINUTES; // Default

        Instant now = Instant.now();
        Instant nextRun = schedule.getNextRun();

        if (nextRun == null || nextRun.isBefore(now)) {
            // If next run is missing or passed, calculate new next run based on frequency
            // For simplicity in MVP, we might just start 'now' + period if it's interval
            // based
            // But let's support a basic interval

            if ("interval".equalsIgnoreCase(schedule.getType())) {
                // Interval logic handled below in scheduleAtFixedRate
                initialDelay = 0; // Start immediately if no specific next run
            }
        } else {
            initialDelay = ChronoUnit.MILLIS.between(now, nextRun);
        }

        if (initialDelay < 0)
            initialDelay = 0;

        Runnable task = () -> executeBackup(schedule);

        ScheduledFuture<?> future;
        if ("interval".equalsIgnoreCase(schedule.getType())) {
            period = schedule.getIntervalMinutes();
            unit = TimeUnit.MINUTES;
            future = executorService.scheduleAtFixedRate(task, initialDelay, period, unit);
        } else {
            // "cron" or "daily" - for MVP let's just do interval or simple daily logic
            // Supporting full cron is complex without a library like Quartz
            // We'll stick to a simple internal "minutes" interval for now to correspond
            // with user request options being flexible
            // If user selects "Daily", frontend sends 1440 minutes.
            period = schedule.getIntervalMinutes();
            if (period <= 0)
                period = 1440; // Default to daily

            future = executorService.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MINUTES);
        }

        activeTasks.put(schedule.getId(), future);
    }

    private void cancelTask(String scheduleId) {
        ScheduledFuture<?> future = activeTasks.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void executeBackup(BackupSchedule schedule) {
        LOGGER.info("Executing scheduled backup: " + schedule.getId());

        // Update last run info
        schedule.setLastRun(Instant.now());
        // For simple fixed rate, next run is approximately now + period
        // But better to just update it after execution or let it be 'null' and dynamic

        try {
            Path sourcePath = Paths.get(schedule.getSourcePath());
            BackupOptions options = new BackupOptions.Builder()
                    .snapshotName("auto-" + schedule.getId().substring(0, 8) + "-" + Instant.now().getEpochSecond())
                    .description("Scheduled backup: " + schedule.getName())
                    .build();

            // We don't wait for the result here, just trigger it
            backupService.backup(sourcePath, options).thenAccept(result -> {
                if (result.isSuccess()) {
                    LOGGER.info("Scheduled backup success: " + result.getSnapshotId());
                    schedule.setLastResult("Success");
                } else {
                    LOGGER.error("Scheduled backup failed: " + result.getError());
                    schedule.setLastResult("Failed: " + result.getError());
                }
                saveSchedules(); // Save status update
            });

        } catch (Exception e) {
            LOGGER.error("Error triggering backup task", e);
            schedule.setLastResult("Error: " + e.getMessage());
            saveSchedules();
        }
    }

    private void rescheduleAll() {
        activeTasks.values().forEach(f -> f.cancel(false));
        activeTasks.clear();
        schedules.values().forEach(this::scheduleTask);
    }

    private void loadSchedules() {
        File file = new File(STORAGE_FILE);
        if (file.exists()) {
            try {
                List<BackupSchedule> list = objectMapper.readValue(file, new TypeReference<List<BackupSchedule>>() {
                });
                list.forEach(s -> schedules.put(s.getId(), s));
                LOGGER.info("Loaded " + schedules.size() + " schedules.");
            } catch (IOException e) {
                LOGGER.error("Failed to load schedules", e);
            }
        }
    }

    private void saveSchedules() {
        try {
            File file = new File(STORAGE_FILE);
            if (file.getParentFile() != null)
                file.getParentFile().mkdirs();
            objectMapper.writeValue(file, new ArrayList<>(schedules.values()));
        } catch (IOException e) {
            LOGGER.error("Failed to save schedules", e);
        }
    }

    // Inner class for Schedule DTO
    public static class BackupSchedule {
        private String id;
        private String name;
        private String sourcePath;
        private String type; // "interval"
        private long intervalMinutes;
        private boolean enabled = true;
        private Instant createdAt;
        private Instant lastRun;
        private Instant nextRun; // Optional, for initial delay
        private String lastResult;

        public BackupSchedule() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public long getIntervalMinutes() {
            return intervalMinutes;
        }

        public void setIntervalMinutes(long intervalMinutes) {
            this.intervalMinutes = intervalMinutes;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getLastRun() {
            return lastRun;
        }

        public void setLastRun(Instant lastRun) {
            this.lastRun = lastRun;
        }

        public Instant getNextRun() {
            return nextRun;
        }

        public void setNextRun(Instant nextRun) {
            this.nextRun = nextRun;
        }

        public String getLastResult() {
            return lastResult;
        }

        public void setLastResult(String lastResult) {
            this.lastResult = lastResult;
        }
    }
}
