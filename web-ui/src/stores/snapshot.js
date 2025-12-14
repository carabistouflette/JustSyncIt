import { defineStore } from 'pinia'
import { snapshotApi } from '../services/api'

export const useSnapshotStore = defineStore('snapshot', {
    state: () => ({
        snapshots: [],
        currentSnapshot: null,
        files: [],
        loading: false,
        error: null
    }),

    getters: {
        sortedSnapshots: (state) => {
            return [...state.snapshots].sort((a, b) =>
                new Date(b.createdAt) - new Date(a.createdAt)
            )
        }
    },

    actions: {
        async fetchSnapshots() {
            this.loading = true
            try {
                const response = await snapshotApi.list()
                this.snapshots = response.data.snapshots || []
            } catch (error) {
                this.error = error.response?.data?.message || error.message
            } finally {
                this.loading = false
            }
        },

        async fetchSnapshot(id) {
            this.loading = true
            try {
                const response = await snapshotApi.get(id)
                this.currentSnapshot = response.data
            } catch (error) {
                this.error = error.response?.data?.message || error.message
            } finally {
                this.loading = false
            }
        },

        async fetchSnapshotFiles(id, path = '') {
            this.loading = true
            try {
                const response = await snapshotApi.getFiles(id, path)
                this.files = response.data.files || []
            } catch (error) {
                this.error = error.response?.data?.message || error.message
            } finally {
                this.loading = false
            }
        },

        async deleteSnapshot(id) {
            try {
                await snapshotApi.delete(id)
                this.snapshots = this.snapshots.filter(s => s.id !== id)
            } catch (error) {
                this.error = error.response?.data?.message || error.message
                throw error
            }
        }
    }
})
