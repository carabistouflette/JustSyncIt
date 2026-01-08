import { defineStore } from 'pinia'
import { networkApi } from '../services/api'

export const useNetworkStore = defineStore('network', {
  state: () => ({
    stats: {
      activeConnections: 0,
      bytesSent: 0,
      bytesReceived: 0,
      messagesSent: 0,
      messagesReceived: 0,
      completedTransfers: 0,
      failedTransfers: 0,
      averageRate: 0,
      uptime: 0
    },
    status: {
      online: false,
      serverRunning: false,
      port: -1,
      defaultTransport: 'TCP'
    },
    loading: false,
    error: null
  }),

  actions: {
    async fetchStats() {
      try {
        const response = await networkApi.getStats()
        this.stats = response.data
      } catch (error) {
        console.error('Failed to fetch network stats:', error)
      }
    },

    async fetchStatus() {
      this.loading = true
      try {
        const response = await networkApi.getStatus()
        this.status = response.data
      } catch (error) {
        this.error = error.response?.data?.message || error.message
      } finally {
        this.loading = false
      }
    }
  }
})
