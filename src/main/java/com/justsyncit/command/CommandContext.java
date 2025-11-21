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
import com.justsyncit.network.NetworkService;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.ContentStore;

/**
 * Context object that provides services to commands.
 * Follows Dependency Inversion Principle by providing abstractions to commands.
 */
public class CommandContext {

    /** BLAKE3 service instance. */
    private final Blake3Service blake3Service;
    
    /** Network service instance. */
    private final NetworkService networkService;
    
    /** Metadata service instance. */
    private final MetadataService metadataService;
    
    /** Content store instance. */
    private final ContentStore contentStore;

    /**
     * Creates a new CommandContext with the provided services.
     *
     * @param blake3Service the BLAKE3 service
     */
    public CommandContext(Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
        this.networkService = null;
        this.metadataService = null;
        this.contentStore = null;
    }
    
    /**
     * Creates a new CommandContext with all provided services.
     *
     * @param blake3Service the BLAKE3 service
     * @param networkService the network service
     * @param metadataService the metadata service
     * @param contentStore the content store
     */
    public CommandContext(Blake3Service blake3Service, NetworkService networkService,
                        MetadataService metadataService, ContentStore contentStore) {
        this.blake3Service = blake3Service;
        this.networkService = networkService;
        this.metadataService = metadataService;
        this.contentStore = contentStore;
    }

    /**
     * Gets the BLAKE3 service.
     *
     * @return the BLAKE3 service
     */
    public Blake3Service getBlake3Service() {
        return blake3Service;
    }
    
    /**
     * Gets the network service.
     *
     * @return the network service
     */
    public NetworkService getNetworkService() {
        return networkService;
    }
    
    /**
     * Gets the metadata service.
     *
     * @return the metadata service
     */
    public MetadataService getMetadataService() {
        return metadataService;
    }
    
    /**
     * Gets the content store.
     *
     * @return the content store
     */
    public ContentStore getContentStore() {
        return contentStore;
    }
}