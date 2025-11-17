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

package com.justsyncit.scanner;

/**
 * Enumeration of strategies for handling symbolic links during filesystem scanning.
 * Follows Open/Closed Principle by allowing extension without modification.
 */
public enum SymlinkStrategy {

    /**
     * Follow symbolic links and process the target files.
     * May lead to infinite loops if links create cycles.
     */
    FOLLOW("Follow symbolic links"),

    /**
     * Do not follow symbolic links, but record them as entries.
     * The link itself is processed, not the target.
     */
    RECORD("Record symbolic links without following"),

    /**
     * Skip symbolic links entirely.
     * Neither the link nor its target is processed.
     */
    SKIP("Skip symbolic links entirely");

    private final String description;

    /**
     * Creates a new SymlinkStrategy with the specified description.
     *
     * @param description human-readable description of the strategy
     */
    SymlinkStrategy(String description) {
        this.description = description;
    }

    /**
     * Gets the description of this strategy.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}