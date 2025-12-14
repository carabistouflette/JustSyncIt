import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    {
        path: '/',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue')
    },
    {
        path: '/backup',
        name: 'Backup',
        component: () => import('../views/Backup.vue')
    },
    {
        path: '/snapshots',
        name: 'Snapshots',
        component: () => import('../views/Snapshots.vue')
    },
    {
        path: '/restore',
        name: 'Restore',
        component: () => import('../views/Restore.vue')
    },
    {
        path: '/files',
        name: 'FileBrowser',
        component: () => import('../views/FileBrowser.vue')
    },
    {
        path: '/settings',
        name: 'Settings',
        component: () => import('../views/Settings.vue')
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router
