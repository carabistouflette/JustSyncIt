import { defineStore } from 'pinia'
import { usersApi } from '../services/api'

export const useUserStore = defineStore('users', {
    state: () => ({
        users: [],
        loading: false,
        error: null
    }),

    actions: {
        async fetchUsers() {
            this.loading = true
            this.error = null
            try {
                const response = await usersApi.list()
                this.users = response.data.users
            } catch (e) {
                this.error = e.response?.data?.message || e.message
                console.error('Failed to fetch users:', e)
            } finally {
                this.loading = false
            }
        },

        async createUser(userData) {
            this.loading = true
            this.error = null
            try {
                const response = await usersApi.create(userData)
                // Backend returns the created user object in response body or we fetch list again
                // UserController returns: ctx.status(201).json(userMap)
                // Adjust based on actual response structure. 
                // Assuming response.data is the user object directly or we push to list
                this.users.push(response.data)
                return response.data
            } catch (e) {
                this.error = e.response?.data?.message || e.message
                throw e
            } finally {
                this.loading = false
            }
        },

        async updateUser(id, userData) {
            this.loading = true
            this.error = null
            try {
                const response = await usersApi.update(id, userData)
                const index = this.users.findIndex(u => u.id === id)
                if (index !== -1) {
                    this.users[index] = response.data
                }
                return response.data
            } catch (e) {
                this.error = e.response?.data?.message || e.message
                throw e
            } finally {
                this.loading = false
            }
        },

        async deleteUser(id) {
            this.loading = true
            this.error = null
            try {
                await usersApi.delete(id)
                this.users = this.users.filter(u => u.id !== id)
            } catch (e) {
                this.error = e.response?.data?.message || e.message
                throw e
            } finally {
                this.loading = false
            }
        }
    }
})
