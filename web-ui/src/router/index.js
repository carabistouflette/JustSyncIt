import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/LoginView.vue'),
        meta: { public: true }
    },
    {
        path: '/setup',
        name: 'Setup',
        component: () => import('../views/SetupView.vue'),
        meta: { public: true }
    },
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
        path: '/schedule',
        name: 'Schedule',
        component: () => import('../views/Schedule.vue')
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
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(async (to, from, next) => {
    const authStore = useAuthStore()

    // Check if password is set on first load or periodically
    if (authStore.isSetup === true) {
        await authStore.checkStatus()
    }

    if (!authStore.isSetup && to.path !== '/setup') {
        return next('/setup')
    }

    if (authStore.isSetup && !authStore.isAuthenticated && !to.meta.public) {
        return next('/login')
    }

    if (authStore.isAuthenticated && to.meta.public) {
        return next('/')
    }

    next()
})

export default router
