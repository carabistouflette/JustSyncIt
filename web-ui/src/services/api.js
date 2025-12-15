import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

const api = axios.create({
    baseURL: API_BASE,
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// Add auth token to requests
api.interceptors.request.use(config => {
    const token = localStorage.getItem('auth_token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// Handle errors globally
api.interceptors.response.use(
    response => response,
    error => {
        if (error.response?.status === 401) {
            localStorage.removeItem('auth_token')
            window.location.href = '/login'
        }
        return Promise.reject(error)
    }
)

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

export const usersApi = {
    list: () => api.get('/users'),
    create: (data) => api.post('/users', data),
    update: (id, data) => api.put(`/users/${id}`, data),
    delete: (id) => api.delete(`/users/${id}`)
}

export const authApi = {
    login: (username, password) => api.post('/auth/login', { username, password }),
    logout: () => api.post('/auth/logout')
}

export const healthApi = {
    check: () => api.get('/health')
}

export default api
