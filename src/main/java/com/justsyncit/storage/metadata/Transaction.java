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

package com.justsyncit.storage.metadata;

import com.justsyncit.storage.ClosableResource;

import java.io.IOException;

/**
 * Interface for database transactions.
 * Follows Interface Segregation Principle by focusing only on transaction management.
 */
public interface Transaction extends ClosableResource {

    /**
     * Commits the transaction, making all changes permanent.
     *
     * @throws IOException if the commit fails
     * @throws IllegalStateException if the transaction is already completed
     */
    void commit() throws IOException;

    /**
     * Rolls back the transaction, undoing all changes.
     *
     * @throws IOException if the rollback fails
     * @throws IllegalStateException if the transaction is already completed
     */
    void rollback() throws IOException;

    /**
     * Checks if the transaction is active (not yet committed or rolled back).
     *
     * @return true if the transaction is active, false otherwise
     */
    boolean isActive();
}