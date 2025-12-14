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

package com.justsyncit.web;

import com.justsyncit.backup.BackupService;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.hash.Blake3Service;

/**
 * Context object containing all services needed by web controllers.
 * Follows Dependency Injection pattern for testability.
 */
public final class WebServerContext {

    private final BackupService backupService;
    private final RestoreService restoreService;
    private final ContentStore contentStore;
    private final MetadataService metadataService;
    private final Blake3Service blake3Service;

    private WebServerContext(Builder builder) {
        this.backupService = builder.backupService;
        this.restoreService = builder.restoreService;
        this.contentStore = builder.contentStore;
        this.metadataService = builder.metadataService;
        this.blake3Service = builder.blake3Service;
    }

    /**
     * Returns the backup service.
     *
     * @return the backup service
     */
    public BackupService getBackupService() {
        return backupService;
    }

    /**
     * Returns the restore service.
     *
     * @return the restore service
     */
    public RestoreService getRestoreService() {
        return restoreService;
    }

    /**
     * Returns the content store.
     *
     * @return the content store
     */
    public ContentStore getContentStore() {
        return contentStore;
    }

    /**
     * Returns the metadata service.
     *
     * @return the metadata service
     */
    public MetadataService getMetadataService() {
        return metadataService;
    }

    /**
     * Returns the BLAKE3 service.
     *
     * @return the BLAKE3 service
     */
    public Blake3Service getBlake3Service() {
        return blake3Service;
    }

    /**
     * Creates a new builder for WebServerContext.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WebServerContext.
     */
    public static final class Builder {
        private BackupService backupService;
        private RestoreService restoreService;
        private ContentStore contentStore;
        private MetadataService metadataService;
        private Blake3Service blake3Service;

        private Builder() {
        }

        /**
         * Sets the backup service.
         *
         * @param backupService the backup service
         * @return this builder
         */
        public Builder withBackupService(BackupService backupService) {
            this.backupService = backupService;
            return this;
        }

        /**
         * Sets the restore service.
         *
         * @param restoreService the restore service
         * @return this builder
         */
        public Builder withRestoreService(RestoreService restoreService) {
            this.restoreService = restoreService;
            return this;
        }

        /**
         * Sets the content store.
         *
         * @param contentStore the content store
         * @return this builder
         */
        public Builder withContentStore(ContentStore contentStore) {
            this.contentStore = contentStore;
            return this;
        }

        /**
         * Sets the metadata service.
         *
         * @param metadataService the metadata service
         * @return this builder
         */
        public Builder withMetadataService(MetadataService metadataService) {
            this.metadataService = metadataService;
            return this;
        }

        /**
         * Sets the BLAKE3 service.
         *
         * @param blake3Service the BLAKE3 service
         * @return this builder
         */
        public Builder withBlake3Service(Blake3Service blake3Service) {
            this.blake3Service = blake3Service;
            return this;
        }

        /**
         * Builds the WebServerContext.
         *
         * @return the built context
         */
        public WebServerContext build() {
            return new WebServerContext(this);
        }
    }
}
