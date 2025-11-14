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

package com.justsyncit.hash;

/**
 * Adapter to bridge IncrementalHasherFactory.IncrementalHasher to Blake3Service.Blake3IncrementalHasher.
 * Follows Adapter pattern to maintain compatibility while using new interfaces.
 */
public class IncrementalHasherAdapter implements Blake3Service.Blake3IncrementalHasher {

    /** The adapted hasher instance. */
    private final IncrementalHasherFactory.IncrementalHasher adaptedHasher;

    /**
     * Creates a new IncrementalHasherAdapter.
     *
     * @param adaptedHasher the hasher to adapt
     */
    public IncrementalHasherAdapter(IncrementalHasherFactory.IncrementalHasher adaptedHasher) {
        this.adaptedHasher = adaptedHasher;
    }

    @Override
    public void update(byte[] data) {
        adaptedHasher.update(data);
    }

    @Override
    public void update(byte[] data, int offset, int length) {
        adaptedHasher.update(data, offset, length);
    }

    @Override
    public String digest() throws HashingException {
        return adaptedHasher.digest();
    }

    @Override
    public void reset() {
        adaptedHasher.reset();
    }
}