package com.justsyncit.web;

import com.justsyncit.backup.BackupService;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.web.service.AuthService;
import com.justsyncit.web.service.SqliteAuthStore;

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
    private final com.justsyncit.scheduler.SchedulerService schedulerService;

    private final SqliteAuthStore authStore;
    private final AuthService authService;

    private WebServerContext(Builder builder) {
        this.backupService = builder.backupService;
        this.restoreService = builder.restoreService;
        this.contentStore = builder.contentStore;
        this.metadataService = builder.metadataService;
        this.blake3Service = builder.blake3Service;
        this.schedulerService = builder.schedulerService;
        this.authStore = builder.authStore;
        this.authService = builder.authService;
    }

    /**
     * Returns the scheduler service.
     *
     * @return the scheduler service
     */
    public com.justsyncit.scheduler.SchedulerService getSchedulerService() {
        return schedulerService;
    }

    // ... existing getters ...

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

    public SqliteAuthStore getAuthStore() {
        return authStore;
    }

    public AuthService getAuthService() {
        return authService;
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
        private com.justsyncit.scheduler.SchedulerService schedulerService;
        private SqliteAuthStore authStore;
        private AuthService authService;

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
         * Sets the scheduler service.
         *
         * @param schedulerService the scheduler service
         * @return this builder
         */
        public Builder withSchedulerService(com.justsyncit.scheduler.SchedulerService schedulerService) {
            this.schedulerService = schedulerService;
            return this;
        }

        public Builder withAuthStore(SqliteAuthStore authStore) {
            this.authStore = authStore;
            return this;
        }

        public Builder withAuthService(AuthService authService) {
            this.authService = authService;
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
