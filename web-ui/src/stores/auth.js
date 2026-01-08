import { defineStore } from 'pinia'
import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem('auth_token') || null,
        isSetup: true, // Default to true until checked
        loading: false,
        error: null
    }),

    getters: {
        isAuthenticated: (state) => !!state.token
    },

    actions: {
        async checkStatus() {
            try {
                const response = await axios.get(`${API_BASE}/auth/status`)
                this.isSetup = response.data.isSet || response.data.isSetup
                return response.data
            } catch (error) {
                console.error('Failed to check auth status:', error)
            }
        },

        async setup(password) {
            this.loading = true
            this.error = null
            try {
                const response = await axios.post(`${API_BASE}/auth/setup`, { password })
                // After setup, we might want to automatically log in or wait for login
                this.isSetup = true
                return response.data
            } catch (error) {
                this.error = error.response?.data?.error || 'Setup failed'
                throw error
            } finally {
                this.loading = false
            }
        },

        async login(password) {
            this.loading = true
            this.error = null
            try {
                const response = await axios.post(`${API_BASE}/auth/login`, { password })
                this.token = response.data.token
                localStorage.setItem('auth_token', this.token)
                return response.data
            } catch (error) {
                this.error = error.response?.data?.error || 'Login failed'
                throw error
            } finally {
                this.loading = false
            }
        },

        logout() {
            this.token = null
            localStorage.removeItem('auth_token')
            // Optionally call backend logout
            axios.post(`${API_BASE}/auth/logout`)
        }
    }
})
