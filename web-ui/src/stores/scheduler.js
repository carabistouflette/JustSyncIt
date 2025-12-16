import { defineStore } from 'pinia'
import { schedulerApi } from '../services/api'

export const useSchedulerStore = defineStore('scheduler', {
    state: () => ({
        schedules: [],
        loading: false,
        error: null
    }),

    actions: {
        async fetchSchedules() {
            this.loading = true
            this.error = null
            try {
                const response = await schedulerApi.list()
                this.schedules = response.data.schedules
            } catch (e) {
                this.error = e.response?.data?.message || e.message
                console.error('Failed to fetch schedules:', e)
            } finally {
                this.loading = false
            }
        },

        async createSchedule(schedule) {
            this.loading = true
            this.error = null
            try {
                const response = await schedulerApi.create(schedule)
                this.schedules.push(response.data)
                return response.data
            } catch (e) {
                this.error = e.response?.data?.message || e.message
                throw e
            } finally {
                this.loading = false
            }
        },

        async deleteSchedule(id) {
            this.loading = true
            this.error = null
            try {
                await schedulerApi.delete(id)
                this.schedules = this.schedules.filter(s => s.id !== id)
            } catch (e) {
                this.error = e.response?.data?.message || e.message
                throw e
            } finally {
                this.loading = false
            }
        }
    }
})
