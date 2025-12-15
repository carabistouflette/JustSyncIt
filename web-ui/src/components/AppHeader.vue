<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import ThemeToggle from './ThemeToggle.vue'

defineProps({
  connected: Boolean
})

const router = useRouter()
const scrolled = ref(false)
const hidden = ref(false)
let lastScrollY = 0

function handleScroll() {
  const currentScrollY = window.scrollY
  scrolled.value = currentScrollY > 20
  hidden.value = currentScrollY > lastScrollY && currentScrollY > 100
  lastScrollY = currentScrollY
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<template>
  <header class="app-header" :class="{ scrolled, hidden }">
    <div class="header-content">
      <div class="logo" @click="router.push('/')">
        <div class="logo-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <defs>
              <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#6366f1"/>
                <stop offset="100%" style="stop-color:#a855f7"/>
              </linearGradient>
            </defs>
            <path d="M16 2L28 8V24L16 30L4 24V8L16 2Z" stroke="url(#logoGrad)" stroke-width="2" fill="none"/>
            <path d="M16 10L22 13V19L16 22L10 19V13L16 10Z" fill="url(#logoGrad)"/>
          </svg>
        </div>
        <span class="logo-text">JustSyncIt</span>
      </div>
      <div class="header-right">
        <ThemeToggle />
        <div class="connection-status" :class="{ online: connected }">
          <div class="status-dot"></div>
          <span>{{ connected ? 'Connected' : 'Disconnected' }}</span>
        </div>
      </div>
    </div>
    <div class="shimmer-line"></div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: transparent;
  transition: all var(--transition-normal);
}

.app-header.scrolled {
  background: var(--bg-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color);
}

.app-header.hidden {
  transform: translateY(-100%);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  max-width: 1600px;
  margin: 0 auto;
}

.logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  transition: transform var(--transition-fast);
}

.logo:hover {
  transform: scale(1.02);
}

.logo-icon {
  animation: glow 3s ease-in-out infinite;
}

@keyframes glow {
  0%, 100% { filter: drop-shadow(0 0 8px rgba(99, 102, 241, 0.4)); }
  50% { filter: drop-shadow(0 0 16px rgba(99, 102, 241, 0.8)); }
}

.logo-text {
  font-size: 1.25rem;
  font-weight: 700;
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--error);
  animation: pulse 2s infinite;
}

.connection-status.online .status-dot {
  background: var(--success);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.shimmer-line {
  height: 2px;
  background: linear-gradient(90deg, 
    transparent 0%, 
    var(--accent-primary) 50%, 
    transparent 100%);
  background-size: 200% 100%;
  animation: shimmer 3s linear infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 640px) {
  .header-content {
    padding: 0.75rem 1rem;
  }
  
  .logo-text {
    font-size: 1rem;
  }
  
  .connection-status span {
    display: none;
  }
}
</style>
