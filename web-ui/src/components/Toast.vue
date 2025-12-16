<script setup>
import { onMounted } from 'vue'

const props = defineProps({
  toast: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['close'])

const icons = {
  success: 'M5 13l4 4L19 7',
  error: 'M6 18L18 6M6 6l12 12',
  warning: 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z',
  info: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z'
}

const colors = {
  success: 'var(--success)',
  error: 'var(--error)',
  warning: 'var(--warning)',
  info: 'var(--info)'
}
</script>

<template>
  <div class="toast" :class="toast.type" @click="emit('close')">
    <div class="toast-icon">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path :d="icons[toast.type]" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>
    <div class="toast-content">
      <div class="toast-title">{{ toast.title }}</div>
      <div class="toast-message" v-if="toast.message">{{ toast.message }}</div>
    </div>
    <div class="toast-progress" v-if="toast.duration > 0">
      <div class="toast-progress-bar" :style="{ animationDuration: toast.duration + 'ms', background: colors[toast.type] }"></div>
    </div>
  </div>
</template>

<style scoped>
.toast {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--bg-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--border-color);
  border-left: 4px solid var(--info);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-lg);
  width: 350px;
  cursor: pointer;
  pointer-events: auto;
  overflow: hidden;
  transition: all var(--transition-fast);
}

.toast:hover {
  transform: translateX(-4px);
  background: var(--bg-secondary);
}

.toast.success { border-left-color: var(--success); }
.toast.error { border-left-color: var(--error); }
.toast.warning { border-left-color: var(--warning); }
.toast.info { border-left-color: var(--info); }

.toast-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}

.toast.success .toast-icon { color: var(--success); }
.toast.error .toast-icon { color: var(--error); }
.toast.warning .toast-icon { color: var(--warning); }
.toast.info .toast-icon { color: var(--info); }

.toast-content {
  flex: 1;
  min-width: 0;
}

.toast-title {
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--text-primary);
  margin-bottom: 0.25rem;
}

.toast-message {
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.4;
}

.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: rgba(255, 255, 255, 0.1);
}

.toast-progress-bar {
  height: 100%;
  width: 0;
  animation: progress linear forwards;
}

@keyframes progress {
  from { width: 100%; }
  to { width: 0%; }
}
</style>
