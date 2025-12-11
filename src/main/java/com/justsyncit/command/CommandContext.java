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

    /** BLAKE3 service instance (required). */
    private final Blake3Service blake3Service;

    /** Network service instance (optional). */
    private final NetworkService networkService;

    /** Metadata service instance (optional). */
    private final MetadataService metadataService;

    /** Content store instance (optional). */
    private final ContentStore contentStore;

    /**
     * Creates a new CommandContext with the provided services.
     * Use the Builder for more flexible construction.
     *
     * @param blake3Service the BLAKE3 service (required)
     * @throws IllegalArgumentException if blake3Service is null
     */
    public CommandContext(Blake3Service blake3Service) {
        this(blake3Service, null, null, null);
    }

    /**
     * Creates a new CommandContext with all provided services.
     *
     * @param blake3Service   the BLAKE3 service (required)
     * @param networkService  the network service (optional, may be null)
     * @param metadataService the metadata service (optional, may be null)
     * @param contentStore    the content store (optional, may be null)
     * @throws IllegalArgumentException if blake3Service is null
     */
    public CommandContext(Blake3Service blake3Service, NetworkService networkService,
            MetadataService metadataService, ContentStore contentStore) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("blake3Service cannot be null");
        }
        this.blake3Service = blake3Service;
        this.networkService = networkService;
        this.metadataService = metadataService;
        this.contentStore = contentStore;
    }

    /**
     * Private constructor for builder.
     *
     * @param builder the builder
     */
    private CommandContext(Builder builder) {
        this(builder.blake3Service, builder.networkService, builder.metadataService, builder.contentStore);
    }

    /**
     * Gets the BLAKE3 service.
     *
     * @return the BLAKE3 service (never null)
     */
    public Blake3Service getBlake3Service() {
        return blake3Service;
    }

    /**
     * Gets the network service.
     *
     * @return the network service (may be null)
     */
    public NetworkService getNetworkService() {
        return networkService;
    }

    /**
     * Gets the metadata service.
     *
     * @return the metadata service (may be null)
     */
    public MetadataService getMetadataService() {
        return metadataService;
    }

    /**
     * Gets the content store.
     *
     * @return the content store (may be null)
     */
    public ContentStore getContentStore() {
        return contentStore;
    }

    /**
     * Creates a new builder for CommandContext.
     *
     * @param blake3Service the BLAKE3 service (required)
     * @return a new builder
     */
    public static Builder builder(Blake3Service blake3Service) {
        return new Builder(blake3Service);
    }

    /**
     * Builder for CommandContext.
     * Provides a fluent API for constructing CommandContext instances with optional
     * services.
     */
    public static class Builder {
        private final Blake3Service blake3Service;
        private NetworkService networkService;
        private MetadataService metadataService;
        private ContentStore contentStore;

        /**
         * Creates a new builder with the required BLAKE3 service.
         *
         * @param blake3Service the BLAKE3 service (required)
         * @throws IllegalArgumentException if blake3Service is null
         */
        public Builder(Blake3Service blake3Service) {
            if (blake3Service == null) {
                throw new IllegalArgumentException("blake3Service cannot be null");
            }
            this.blake3Service = blake3Service;
        }

        /**
         * Sets the network service.
         *
         * @param networkService the network service
         * @return this builder
         */
        public Builder networkService(NetworkService networkService) {
            this.networkService = networkService;
            return this;
        }

        /**
         * Sets the metadata service.
         *
         * @param metadataService the metadata service
         * @return this builder
         */
        public Builder metadataService(MetadataService metadataService) {
            this.metadataService = metadataService;
            return this;
        }

        /**
         * Sets the content store.
         *
         * @param contentStore the content store
         * @return this builder
         */
        public Builder contentStore(ContentStore contentStore) {
            this.contentStore = contentStore;
            return this;
        }

        /**
         * Builds the CommandContext.
         *
         * @return a new CommandContext instance
         */
        public CommandContext build() {
            return new CommandContext(this);
        }
    }
}