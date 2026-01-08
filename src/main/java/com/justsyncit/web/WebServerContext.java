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
    private final com.justsyncit.auth.MasterPasswordService masterPasswordService;
    private com.justsyncit.web.controller.AuthController authController;
    private final com.justsyncit.scheduler.SchedulerService schedulerService;
    private final com.justsyncit.network.NetworkService networkService;

    private WebServerContext(Builder builder) {
        this.backupService = builder.backupService;
        this.restoreService = builder.restoreService;
        this.contentStore = builder.contentStore;
        this.metadataService = builder.metadataService;
        this.blake3Service = builder.blake3Service;
        this.masterPasswordService = builder.masterPasswordService;
        this.schedulerService = builder.schedulerService;
        this.networkService = builder.networkService;
    }

    /**
     * Returns the scheduler service.
     *
     * @return the scheduler service
     */
    public com.justsyncit.scheduler.SchedulerService getSchedulerService() {
        return schedulerService;
    }

    /**
     * Returns the network service.
     *
     * @return the network service
     */
    public com.justsyncit.network.NetworkService getNetworkService() {
        return networkService;
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
     * Returns the master password service.
     *
     * @return the master password service
     */
    public com.justsyncit.auth.MasterPasswordService getMasterPasswordService() {
        return masterPasswordService;
    }

    /**
     * Returns the auth controller.
     *
     * @return the auth controller
     */
    public com.justsyncit.web.controller.AuthController getAuthController() {
        return authController;
    }

    /**
     * Sets the auth controller.
     *
     * @param authController the auth controller
     */
    public void setAuthController(com.justsyncit.web.controller.AuthController authController) {
        this.authController = authController;
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
        private com.justsyncit.auth.MasterPasswordService masterPasswordService;
        private com.justsyncit.scheduler.SchedulerService schedulerService;
        private com.justsyncit.network.NetworkService networkService;

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
         * Sets the master password service.
         *
         * @param masterPasswordService the master password service
         * @return this builder
         */
        public Builder withMasterPasswordService(com.justsyncit.auth.MasterPasswordService masterPasswordService) {
            this.masterPasswordService = masterPasswordService;
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

        /**
         * Sets the network service.
         *
         * @param networkService the network service
         * @return this builder
         */
        public Builder withNetworkService(com.justsyncit.network.NetworkService networkService) {
            this.networkService = networkService;
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
