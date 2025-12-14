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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import java.util.concurrent.ExecutorService;

/**
 * Interface for executor services that support priority-based task submission.
 * Extends ExecutorService with priority-aware task scheduling capabilities.
 */
public interface PrioritizedExecutorService extends ExecutorService {

    /**
     * Submits a task with the specified priority.
     *
     * @param priority the priority level for the task
     * @param task the task to execute
     * @return a Future representing pending completion of the task
     */
    java.util.concurrent.Future<?> submit(ThreadPoolManager.ThreadPriority priority, Runnable task);

    /**
     * Submits a value-returning task with the specified priority.
     *
     * @param priority the priority level for the task
     * @param task the task to execute
     * @return a Future representing pending completion of the task
     */
    <T> java.util.concurrent.Future<T> submit(ThreadPoolManager.ThreadPriority priority, java.util.concurrent.Callable<T> task);

    /**
     * Executes a command with the specified priority at some time in the future.
     *
     * @param priority the priority level for the task
     * @param command the task to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @return a ScheduledFuture representing pending completion of the task
     */
    java.util.concurrent.ScheduledFuture<?> schedule(ThreadPoolManager.ThreadPriority priority,
                                               Runnable command, long delay, java.util.concurrent.TimeUnit unit);

    /**
     * Creates and executes a periodic action with the specified priority.
     *
     * @param priority the priority level for the task
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @param unit the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing pending completion of the task
     */
    java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(ThreadPoolManager.ThreadPriority priority,
                                                         Runnable command, long initialDelay,
                                                         long period, java.util.concurrent.TimeUnit unit);

    /**
     * Creates and executes a periodic action with the specified priority.
     *
     * @param priority the priority level for the task
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay the delay between the termination of one execution and the commencement of the next
     * @param unit the time unit of the initialDelay and delay parameters
     * @return a ScheduledFuture representing pending completion of the task
     */
    java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(ThreadPoolManager.ThreadPriority priority,
                                                           Runnable command, long initialDelay,
                                                           long delay, java.util.concurrent.TimeUnit unit);
}