import { ref } from 'vue'

const toasts = ref([])
let idCounter = 0

export function useToast() {
    function add(toast) {
        const id = idCounter++
        const newToast = {
            id,
            title: toast.title,
            message: toast.message,
            type: toast.type || 'info', // 'success', 'error', 'warning', 'info'
            duration: toast.duration || 5000,
            timestamp: Date.now()
        }

        toasts.value.push(newToast)

        if (newToast.duration > 0) {
            setTimeout(() => remove(id), newToast.duration)
        }

        return id
    }

    function remove(id) {
        const index = toasts.value.findIndex(t => t.id === id)
        if (index !== -1) {
            toasts.value.splice(index, 1)
        }
    }

    function success(message, title = 'Success') {
        return add({ type: 'success', title, message })
    }

    function error(message, title = 'Error') {
        return add({ type: 'error', title, message })
    }

    function warning(message, title = 'Warning') {
        return add({ type: 'warning', title, message })
    }

    function info(message, title = 'Info') {
        return add({ type: 'info', title, message })
    }

    return {
        toasts,
        add,
        remove,
        success,
        error,
        warning,
        info
    }
}
