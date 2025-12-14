import { ref, onUnmounted } from 'vue'
import { useBackupStore } from '../stores/backup'

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws'

let socket = null
let reconnectTimer = null
const isConnected = ref(false)
const connectionError = ref(null)

export function useWebSocket() {
    const backupStore = useBackupStore()

    function connect() {
        if (socket && socket.readyState === WebSocket.OPEN) {
            return
        }

        try {
            socket = new WebSocket(WS_URL)

            socket.onopen = () => {
                isConnected.value = true
                connectionError.value = null
                console.log('WebSocket connected')
            }

            socket.onclose = () => {
                isConnected.value = false
                console.log('WebSocket disconnected')
                scheduleReconnect()
            }

            socket.onerror = (error) => {
                connectionError.value = 'WebSocket connection failed'
                console.error('WebSocket error:', error)
            }

            socket.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data)
                    handleMessage(data)
                } catch (error) {
                    console.error('Failed to parse WebSocket message:', error)
                }
            }
        } catch (error) {
            connectionError.value = error.message
        }
    }

    function disconnect() {
        if (reconnectTimer) {
            clearTimeout(reconnectTimer)
            reconnectTimer = null
        }
        if (socket) {
            socket.close()
            socket = null
        }
    }

    function scheduleReconnect() {
        if (reconnectTimer) return
        reconnectTimer = setTimeout(() => {
            reconnectTimer = null
            connect()
        }, 5000)
    }

    function handleMessage(data) {
        // Route messages to appropriate stores
        if (data.type?.startsWith('backup:')) {
            backupStore.updateFromWebSocket(data)
        }
        // Add more message handlers as needed
    }

    function send(message) {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify(message))
        }
    }

    onUnmounted(() => {
        // Don't disconnect on unmount, keep connection alive
    })

    return {
        connect,
        disconnect,
        send,
        isConnected,
        connectionError
    }
}
