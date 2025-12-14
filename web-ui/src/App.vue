<script setup>
import { onMounted } from 'vue'
import { useWebSocket } from './composables/useWebSocket'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'

const { connect, isConnected } = useWebSocket()

onMounted(() => {
  connect()
})
</script>

<template>
  <div class="app-container">
    <AppHeader v-if="!$route.path.includes('/login')" :connected="isConnected" />
    <div class="app-main">
      <AppSidebar v-if="!$route.path.includes('/login')" />
      <main :class="['app-content', { 'fullscreen': $route.path.includes('/login') }]">
        <router-view />
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

.app-content.fullscreen {
  padding: 0;
}

@media (max-width: 768px) {
  .app-main {
    flex-direction: column;
  }
}
</style>
