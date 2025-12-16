<script setup>
import { ref, onMounted } from 'vue'
import { configApi } from '../services/api'

const config = ref({})
const backupSources = ref([])
const loading = ref(true)
const saving = ref(false)
const error = ref(null)
const success = ref(null)
const newSourcePath = ref('')

async function loadConfig() {
  try {
    const [configRes, sourcesRes] = await Promise.all([
      configApi.get(),
      configApi.getBackupSources()
    ])
    config.value = configRes.data
    backupSources.value = sourcesRes.data.sources || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  saving.value = true
  error.value = null
  success.value = null
  
  try {
    await configApi.update(config.value)
    success.value = 'Settings saved successfully'
    setTimeout(() => { success.value = null }, 3000)
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  } finally {
    saving.value = false
  }
}

async function addBackupSource() {
  if (!newSourcePath.value.trim()) return
  
  try {
    await configApi.addBackupSource(newSourcePath.value)
    backupSources.value.push(newSourcePath.value)
    newSourcePath.value = ''
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  }
}

onMounted(loadConfig)
</script>

<template>
  <div class="settings-page">
    <h1 class="page-title">Settings</h1>
    
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
    </div>
    
    <template v-else>
      <div v-if="error" class="error-message">{{ error }}</div>
      <div v-if="success" class="success-message">{{ success }}</div>
      
      <!-- General Settings -->
      <section class="settings-section card">
        <h2>General</h2>
        
        <div class="form-group">
          <label>Web server port</label>
          <input type="number" v-model.number="config.webPort" min="1" max="65535">
        </div>
        
        <div class="form-group">
          <label>Retention days</label>
          <input type="number" v-model.number="config.retentionDays" min="1">
          <span class="hint">Number of days to keep old snapshots</span>
        </div>
        
        <div class="form-group">
          <label>Max concurrent backups</label>
          <input type="number" v-model.number="config.maxConcurrentBackups" min="1" max="10">
        </div>
      </section>
      
      <!-- Backup Settings -->
      <section class="settings-section card">
        <h2>Backup</h2>
        
        <div class="form-group">
          <label>Default chunk size (bytes)</label>
          <select v-model.number="config.defaultChunkSize">
            <option :value="1024 * 1024">1 MB</option>
            <option :value="4 * 1024 * 1024">4 MB</option>
            <option :value="8 * 1024 * 1024">8 MB</option>
            <option :value="16 * 1024 * 1024">16 MB</option>
          </select>
        </div>
        
        <div class="form-options">
          <label class="checkbox-label">
            <input type="checkbox" v-model="config.compressionEnabled">
            <span>Enable compression</span>
          </label>
        </div>
        
        <div class="form-group" v-if="config.compressionEnabled">
          <label>Compression level (1-22)</label>
          <input type="range" v-model.number="config.compressionLevel" min="1" max="22">
          <span class="range-value">{{ config.compressionLevel }}</span>
        </div>
        
        <div class="form-options">
          <label class="checkbox-label">
            <input type="checkbox" v-model="config.encryptionEnabled">
            <span>Enable encryption</span>
          </label>
        </div>
      </section>
      
      <!-- Backup Sources -->
      <section class="settings-section card">
        <h2>Default Backup Sources</h2>
        
        <div class="source-list">
          <div v-for="source in backupSources" :key="source" class="source-item">
            <span>{{ source }}</span>
          </div>
          
          <div v-if="backupSources.length === 0" class="empty-sources">
            No default backup sources configured
          </div>
        </div>
        
        <div class="add-source">
          <input v-model="newSourcePath" placeholder="/path/to/directory">
          <button class="btn btn-secondary" @click="addBackupSource">Add Source</button>
        </div>
      </section>
      
      <div class="save-actions">
        <button class="btn btn-primary" @click="saveConfig" :disabled="saving">
          {{ saving ? 'Saving...' : 'Save Settings' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 800px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 3rem;
}

.error-message {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid var(--error);
  color: var(--error);
  padding: 1rem;
  border-radius: var(--radius-sm);
  margin-bottom: 1.5rem;
}

.success-message {
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid var(--success);
  color: var(--success);
  padding: 1rem;
  border-radius: var(--radius-sm);
  margin-bottom: 1.5rem;
}

.settings-section {
  margin-bottom: 1.5rem;
}

.settings-section h2 {
  font-size: 1.1rem;
  margin-bottom: 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border-color);
}

.form-group {
  margin-bottom: 1.25rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.form-group input[type="range"] {
  width: calc(100% - 50px);
  display: inline-block;
  vertical-align: middle;
}

.range-value {
  display: inline-block;
  width: 40px;
  text-align: right;
  font-weight: 600;
  color: var(--accent-primary);
}

.hint {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.form-options {
  margin-bottom: 1rem;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: var(--accent-primary);
}

.source-list {
  margin-bottom: 1rem;
}

.source-item {
  padding: 0.75rem 1rem;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  margin-bottom: 0.5rem;
  font-family: monospace;
  font-size: 0.9rem;
}

.empty-sources {
  padding: 1.5rem;
  text-align: center;
  color: var(--text-muted);
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
}

.add-source {
  display: flex;
  gap: 0.5rem;
}

.add-source input {
  flex: 1;
}

.save-actions {
  margin-top: 2rem;
}

.save-actions .btn {
  width: 100%;
  padding: 1rem;
}

.save-actions .btn:disabled {
  opacity: 0.7;
}
</style>
