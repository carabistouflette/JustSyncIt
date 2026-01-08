import { defineStore } from 'pinia'
import { healthApi } from '../services/api'

export const useConnectionStore = defineStore('connection', {
  state: () => ({
    isWsConnected: false,
    isHttpHealthy: true, // Optimistic default
    lastError: null,
    checkInterval: null
  }),

  getters: {
    status: (state) => {
      if (!state.isHttpHealthy) return 'offline'
      if (!state.isWsConnected) return 'limited'
      return 'connected'
    },
    statusLabel: (state) => {
      if (!state.isHttpHealthy) return 'Offline'
      if (!state.isWsConnected) return 'Online (No Live Updates)'
      return 'Connected'
    },
    statusColor: (state) => {
      if (!state.isHttpHealthy) return 'var(--error)'
      if (!state.isWsConnected) return 'var(--warning)'
      return 'var(--success)'
    }
  },

  actions: {
    setWsConnected(value) {
      this.isWsConnected = value
    },
    async checkHealth() {
      try {
        await healthApi.check()
        this.isHttpHealthy = true
        this.lastError = null
      } catch (error) {
        this.isHttpHealthy = false
        this.lastError = error.message
      }
    },
    startHealthCheck() {
      if (this.checkInterval) return
      this.checkHealth()
      this.checkInterval = setInterval(() => this.checkHealth(), 10000)
    },
    stopHealthCheck() {
      if (this.checkInterval) {
        clearInterval(this.checkInterval)
        this.checkInterval = null
      }
    }
  }
})
