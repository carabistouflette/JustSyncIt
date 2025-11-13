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

package com.justsyncit.storage;

import java.io.IOException;

/**
 * Interface for resources that can be closed.
 * Follows Interface Segregation Principle by focusing only on resource cleanup.
 */
public interface ClosableResource {

    /**
     * Closes the resource and releases any associated resources.
     *
     * @throws IOException if an I/O error occurs during closing
     */
    void close() throws IOException;
}