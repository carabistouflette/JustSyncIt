<template>
  <div class="setup-container">
    <div class="setup-card glass">
      <div class="header-section">
        <div class="setup-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 15L12 9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M9 12L15 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1>Initialize Security</h1>
        <p class="subtitle">Set your master password to enable encryption.</p>
      </div>

      <div class="info-alert">
        <div class="alert-icon">!</div>
        <p>This password will be used to derive your 256-bit AES encryption key. <strong>It cannot be recovered if lost.</strong></p>
      </div>

      <form @submit.prevent="handleSetup" class="setup-form">
        <div class="input-group">
          <label for="password">New Master Password</label>
          <input 
            type="password" 
            id="password" 
            v-model="password" 
            placeholder="At least 8 characters"
            required
            minlength="8"
            :disabled="authStore.loading"
          />
        </div>

        <div class="input-group" :class="{ error: passwordMismatch }">
          <label for="confirmPassword">Confirm Password</label>
          <input 
            type="password" 
            id="confirmPassword" 
            v-model="confirmPassword" 
            placeholder="Repeat for confirmation"
            required
            :disabled="authStore.loading"
          />
          <p v-if="passwordMismatch" class="error-message">Passwords do not match</p>
          <p v-else-if="authStore.error" class="error-message">{{ authStore.error }}</p>
        </div>

        <button type="submit" class="submit-btn" :disabled="authStore.loading || !isValid">
          <span v-if="!authStore.loading">Set Master Password</span>
          <span v-else class="loader"></span>
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const password = ref('')
const confirmPassword = ref('')

const passwordMismatch = computed(() => {
  return confirmPassword.value && password.value !== confirmPassword.value
})

const isValid = computed(() => {
  return password.value.length >= 8 && password.value === confirmPassword.value
})

const handleSetup = async () => {
  if (!isValid.value) return
  
  try {
    await authStore.setup(password.value)
    // After setup, redirect to login
    router.push('/login')
  } catch (error) {
    console.error('Setup failed:', error)
  }
}
</script>

<style scoped>
.setup-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at bottom right, #1e1b4b, #0f172a);
  padding: 20px;
}

.setup-card {
  width: 100%;
  max-width: 480px;
  padding: 40px;
  border-radius: 28px;
  text-align: center;
  animation: fadeIn 0.8s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.header-section {
  margin-bottom: 32px;
}

.setup-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 20px;
  color: #10b981;
  filter: drop-shadow(0 0 10px rgba(16, 185, 129, 0.3));
}

h1 {
  font-size: 1.75rem;
  font-weight: 700;
  color: #fff;
  margin: 0;
}

.subtitle {
  color: #94a3b8;
  font-size: 0.9375rem;
  margin-top: 8px;
}

.info-alert {
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  gap: 16px;
  align-items: flex-start;
  text-align: left;
  margin-bottom: 32px;
}

.alert-icon {
  background: #f59e0b;
  color: #000;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  flex-shrink: 0;
}

.info-alert p {
  color: #fcd34d;
  font-size: 0.8125rem;
  line-height: 1.4;
  margin: 0;
}

.setup-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.input-group {
  text-align: left;
}

.input-group label {
  display: block;
  font-size: 0.875rem;
  font-weight: 500;
  color: #cbd5e1;
  margin-bottom: 8px;
}

input {
  width: 100%;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: #fff;
  font-size: 1rem;
  transition: all 0.2s;
}

input:focus {
  outline: none;
  border-color: #10b981;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.1);
}

.error input {
  border-color: #ef4444;
}

.error-message {
  color: #ef4444;
  font-size: 0.8125rem;
  margin-top: 6px;
}

.submit-btn {
  width: 100%;
  padding: 16px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
  border-radius: 14px;
  color: #fff;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.3);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.glass {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.loader {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
