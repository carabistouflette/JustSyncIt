<script setup>
import { onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useConnectionStore } from './stores/connection'
import { useWebSocket } from './composables/useWebSocket'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
import ToastContainer from './components/ToastContainer.vue'

const route = useRoute()
const authStore = useAuthStore()
const connectionStore = useConnectionStore()
const { connect } = useWebSocket()

// Only show layout components on protected pages
const showLayout = computed(() => {
    return !route.meta.public && authStore.isAuthenticated
})

// Connect WebSocket and start health checks
onMounted(async () => {
    connectionStore.startHealthCheck()
    await authStore.checkStatus()
    if (authStore.isAuthenticated) {
        connect()
    }
})
</script>

<template>
  <div class="app-container">
    <ToastContainer />
    <AppHeader v-if="showLayout" />
    <div class="app-main">
      <AppSidebar v-if="showLayout" />
      <main class="app-content" :class="{ 'no-layout': !showLayout }">
        <router-view v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-primary);
}

.app-main {
  display: flex;
  flex: 1;
}

.app-content {
  flex: 1;
  padding: 2rem;
  overflow-y: auto;
}

.app-content.no-layout {
  padding: 0;
}

@media (max-width: 768px) {
  .app-main {
    flex-direction: column;
  }
}

/* Page Transitions */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-enter-from,
.page-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
