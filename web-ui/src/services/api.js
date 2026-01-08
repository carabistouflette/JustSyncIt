import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

const api = axios.create({
    baseURL: API_BASE,
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// Inject auth token
api.interceptors.request.use(
    config => {
        const authStore = useAuthStore()
        if (authStore.token) {
            config.headers.Authorization = `Bearer ${authStore.token}`
        }
        return config
    },
    error => Promise.reject(error)
)

// Handle errors globally
api.interceptors.response.use(
    response => response,
    error => {
        if (error.response && error.response.status === 401) {
            const authStore = useAuthStore()
            authStore.logout()
            router.push('/login')
        }
        console.error('API error:', error)
        return Promise.reject(error)
    }
)

export const authApi = {
    getStatus: () => api.get('/auth/status'),
    setup: (password) => api.post('/auth/setup', { password }),
    login: (password) => api.post('/auth/login', { password }),
    logout: () => api.post('/auth/logout')
}

export const backupApi = {
    start: (data) => api.post('/backup', data),
    getStatus: () => api.get('/backup/status'),
    getHistory: (limit = 10) => api.get(`/backup/history?limit=${limit}`),
    cancel: () => api.post('/backup/cancel')
}

export const snapshotApi = {
    list: () => api.get('/snapshots'),
    get: (id) => api.get(`/snapshots/${id}`),
    getFiles: (id, path = '', limit = 100) =>
        api.get(`/snapshots/${id}/files?path=${path}&limit=${limit}`),
    getStats: (id) => api.get(`/snapshots/${id}/stats`),
    delete: (id) => api.delete(`/snapshots/${id}`),
    verify: (id) => api.post(`/snapshots/${id}/verify`)
}

export const restoreApi = {
    start: (data) => api.post('/restore', data),
    getStatus: () => api.get('/restore/status'),
    cancel: () => api.post('/restore/cancel')
}

export const filesApi = {
    browse: (path = '', showHidden = false) =>
        api.get(`/files?path=${encodeURIComponent(path)}&showHidden=${showHidden}`),
    search: (path, pattern, limit = 100) =>
        api.get(`/files/search?path=${encodeURIComponent(path)}&pattern=${pattern}&limit=${limit}`)
}

export const configApi = {
    get: () => api.get('/config'),
    update: (data) => api.put('/config', data),
    getBackupSources: () => api.get('/config/backup-sources'),
    addBackupSource: (path) => api.post('/config/backup-sources', { path })
}

export const schedulerApi = {
    list: () => api.get('/schedules'),
    create: (data) => api.post('/schedules', data),
    delete: (id) => api.delete(`/schedules/${id}`)
}

export const healthApi = {
    check: () => api.get('/health')
}

export const networkApi = {
    getStats: () => api.get('/network/stats'),
    getStatus: () => api.get('/network/status')
}

export default api
