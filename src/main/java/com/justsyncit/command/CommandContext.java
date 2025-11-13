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

import com.justsyncit.hash.Blake3Service;

/**
 * Context object that provides services to commands.
 * Follows Dependency Inversion Principle by providing abstractions to commands.
 */
public class CommandContext {

    /** BLAKE3 service instance. */
    private final Blake3Service blake3Service;

    /**
     * Creates a new CommandContext with the provided services.
     *
     * @param blake3Service the BLAKE3 service
     */
    public CommandContext(Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
    }

    /**
     * Gets the BLAKE3 service.
     *
     * @return the BLAKE3 service
     */
    public Blake3Service getBlake3Service() {
        return blake3Service;
    }
}