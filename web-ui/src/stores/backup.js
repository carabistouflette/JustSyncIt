import { defineStore } from 'pinia'
import { backupApi } from '../services/api'

export const useBackupStore = defineStore('backup', {
    state: () => ({
        status: 'idle',
        snapshotId: null,
        progress: 0,
        filesProcessed: 0,
        bytesProcessed: 0,
        totalFiles: 0,
        totalBytes: 0,
        currentFile: '',
        error: null,
        history: []
    }),

    getters: {
        isRunning: (state) => state.status === 'running' || state.status === 'starting',
        progressPercent: (state) => {
            if (state.totalBytes === 0) return 0
            return Math.round((state.bytesProcessed / state.totalBytes) * 100)
        }
    },

    actions: {
        async startBackup(config) {
            try {
                this.status = 'starting'
                this.error = null
                const response = await backupApi.start(config)
                this.snapshotId = response.data.snapshotId
                this.status = 'running'
                return response.data
            } catch (error) {
                this.status = 'failed'
                this.error = error.response?.data?.message || error.message
                throw error
            }
        },

        async fetchStatus() {
            try {
                const response = await backupApi.getStatus()
                const { progressPercent, ...data } = response.data
                Object.assign(this, data)
            } catch (error) {
                console.error('Failed to fetch backup status:', error)
            }
        },

        async fetchHistory(limit = 10) {
            try {
                const response = await backupApi.getHistory(limit)
                this.history = response.data.history || []
            } catch (error) {
                console.error('Failed to fetch backup history:', error)
            }
        },

        async cancelBackup() {
            try {
                await backupApi.cancel()
                this.status = 'cancelled'
            } catch (error) {
                console.error('Failed to cancel backup:', error)
            }
        },

        updateFromWebSocket(data) {
            if (data.type === 'backup:started') {
                this.status = 'running'
                this.snapshotId = data.data.snapshotId
            } else if (data.type === 'backup:progress') {
                this.filesProcessed = data.data.filesProcessed
                this.bytesProcessed = data.data.bytesProcessed
                this.currentFile = data.data.currentFile
            } else if (data.type === 'backup:completed') {
                this.status = 'completed'
                this.progress = 100
            } else if (data.type === 'backup:failed') {
                this.status = 'failed'
                this.error = data.data.error
            }
        }
    }
})
