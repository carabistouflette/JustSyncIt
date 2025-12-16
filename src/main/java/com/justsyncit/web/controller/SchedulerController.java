/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.web.controller;

import com.justsyncit.scheduler.SchedulerService;
import com.justsyncit.scheduler.SchedulerService.BackupSchedule;
import com.justsyncit.web.WebServerContext;
import io.javalin.http.Context;

import java.util.List;

public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(WebServerContext context) {
        this.schedulerService = context.getSchedulerService();
    }

    public void listSchedules(Context ctx) {
        List<BackupSchedule> schedules = schedulerService.getSchedules();
        ctx.json(java.util.Map.of("schedules", schedules));
    }

    public void createSchedule(Context ctx) {
        try {
            BackupSchedule schedule = ctx.bodyAsClass(BackupSchedule.class);

            // Basic validation
            if (schedule.getName() == null || schedule.getName().isEmpty()) {
                ctx.status(400).json(java.util.Map.of("error", "Name is required"));
                return;
            }
            if (schedule.getSourcePath() == null || schedule.getSourcePath().isEmpty()) {
                ctx.status(400).json(java.util.Map.of("error", "Source path is required"));
                return;
            }
            if (schedule.getIntervalMinutes() <= 0) {
                ctx.status(400).json(java.util.Map.of("error", "Interval must be greater than 0"));
                return;
            }

            BackupSchedule created = schedulerService.createSchedule(schedule);
            ctx.status(201).json(created);
        } catch (Exception e) {
            ctx.status(400).json(java.util.Map.of("error", "Invalid request body", "details", e.getMessage()));
        }
    }

    public void deleteSchedule(Context ctx) {
        String id = ctx.pathParam("id");
        schedulerService.deleteSchedule(id);
        ctx.status(204);
    }
}
