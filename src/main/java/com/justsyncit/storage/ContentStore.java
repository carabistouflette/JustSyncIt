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

/**
 * Interface for content-addressable storage system.
 * Provides deduplication by storing chunks identified by their cryptographic hash.
 * Follows Interface Segregation Principle by composing multiple focused interfaces.
 */
public interface ContentStore extends
        ChunkStorage,
        StorageStatistics,
        GarbageCollectible,
        ClosableResource {
    // This interface now combines multiple focused interfaces
    // No additional methods needed - all functionality is inherited
}