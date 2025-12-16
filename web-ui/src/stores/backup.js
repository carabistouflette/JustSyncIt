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
        currentActivity: '',
        error: null,
        logs: [], // Detailed event logs
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
                this.logs = [] // Clear logs for new backup
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
                // The instruction seems to imply a change in data structure or target for progress updates.
                // Assuming the intent is to update the store's state directly with the new data structure.
                // The original code used data.data.property, the instruction uses data.property.
                // Also, the instruction introduces 'progressPercent' directly from the websocket data.
                this.filesProcessed = data.data.filesProcessed
                this.bytesProcessed = data.data.bytesProcessed
                this.currentFile = data.data.currentFile
                this.currentActivity = data.data.currentActivity
                // Ensure progress percent is updated if provided
                if (data.data.progressPercent !== undefined) {
                    this.progress = data.data.progressPercent
                }
            } else if (data.type === 'backup:completed') {
                this.status = 'completed'
                this.progress = 100
                this.logs.unshift({
                    timestamp: new Date().toISOString(),
                    level: 'INFO',
                    message: 'Backup completed successfully',
                    type: 'SYSTEM'
                })
            } else if (data.type === 'backup:failed') {
                this.status = 'failed'
                this.error = data.data.error
                this.logs.unshift({
                    timestamp: new Date().toISOString(),
                    level: 'ERROR',
                    message: data.data.error,
                    type: 'SYSTEM'
                })
            } else if (data.type === 'backup:event') {
                // Add event to logs (keep last 100)
                this.logs.unshift({
                    timestamp: new Date().toISOString(),
                    level: data.data.level,
                    message: data.data.message,
                    file: data.data.file,
                    type: data.data.type
                })
                if (this.logs.length > 100) {
                    this.logs.pop()
                }
            }
        }
    }
})
