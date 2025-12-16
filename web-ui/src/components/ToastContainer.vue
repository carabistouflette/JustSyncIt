<script setup>
import { useToast } from '../composables/useToast'
import Toast from './Toast.vue'

const { toasts, remove } = useToast()
</script>

<template>
  <div class="toast-container">
    <TransitionGroup name="toast-anim">
      <Toast 
        v-for="toast in toasts" 
        :key="toast.id" 
        :toast="toast" 
        @close="remove(toast.id)" 
      />
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 1rem;
  right: 1rem;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  pointer-events: none; /* Allow clicking through the container */
}

/* Transitions */
.toast-anim-enter-active,
.toast-anim-leave-active {
  transition: all 0.3s ease;
}

.toast-anim-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.toast-anim-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
