import { onUnmounted } from 'vue'
import { useBackupStore } from '../stores/backup'
import { useConnectionStore } from '../stores/connection'

const getWsUrl = () => {
    if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host || 'localhost:8080'
    return `${protocol}//${host}/ws`
}

let socket = null
let reconnectTimer = null

export function useWebSocket() {
    const backupStore = useBackupStore()
    const connectionStore = useConnectionStore()

    function connect() {
        if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
            return
        }

        const url = getWsUrl()
        console.log(`Connecting to WebSocket: ${url}`)

        try {
            socket = new WebSocket(url)

            socket.onopen = () => {
                connectionStore.setWsConnected(true)
                console.log('WebSocket connected')
            }

            socket.onclose = () => {
                connectionStore.setWsConnected(false)
                console.log('WebSocket disconnected')
                scheduleReconnect()
            }

            socket.onerror = (error) => {
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
            console.error('WebSocket setup error:', error)
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
    }

    function send(message) {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify(message))
        }
    }

    onUnmounted(() => {
        // Shared connection, don't close on individual component unmount
    })

    return {
        connect,
        disconnect,
        send
    }
}
