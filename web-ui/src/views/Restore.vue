<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useSnapshotStore } from '../stores/snapshot'
import { restoreApi } from '../services/api'

const route = useRoute()
const snapshotStore = useSnapshotStore()

const selectedSnapshot = ref(route.query.snapshot || '')
const targetPath = ref('')
const overwriteExisting = ref(false)
const preserveAttributes = ref(true)
const verifyIntegrity = ref(true)
const restoreStatus = ref('idle')
const progress = ref(0)
const currentFile = ref('')
const error = ref(null)

async function startRestore() {
  if (!selectedSnapshot.value) {
    error.value = 'Please select a snapshot to restore'
    return
  }
  if (!targetPath.value) {
    error.value = 'Please specify a target directory'
    return
  }
  
  try {
    error.value = null
    restoreStatus.value = 'running'
    
    await restoreApi.start({
      snapshotId: selectedSnapshot.value,
      targetPath: targetPath.value,
      overwriteExisting: overwriteExisting.value,
      preserveAttributes: preserveAttributes.value,
      verifyIntegrity: verifyIntegrity.value
    })
    
    // Poll for status
    pollStatus()
  } catch (e) {
    restoreStatus.value = 'failed'
    error.value = e.response?.data?.message || e.message
  }
}

async function pollStatus() {
  try {
    const response = await restoreApi.getStatus()
    const data = response.data
    
    restoreStatus.value = data.status
    progress.value = data.progress || 0
    currentFile.value = data.currentFile || ''
    
    if (data.status === 'running') {
      setTimeout(pollStatus, 1000)
    }
  } catch (e) {
    console.error('Failed to poll restore status:', e)
  }
}

async function cancelRestore() {
  try {
    await restoreApi.cancel()
    restoreStatus.value = 'cancelled'
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  }
}

onMounted(() => {
  snapshotStore.fetchSnapshots()
})
</script>

<template>
  <div class="restore-page">
    <h1 class="page-title">Restore</h1>
    
    <!-- Restore in Progress -->
    <div v-if="restoreStatus === 'running'" class="restore-running card">
      <div class="running-header">
        <h2>Restore in Progress</h2>
        <span class="badge badge-warning">Running</span>
      </div>
      
      <div class="progress-section">
        <div class="progress-info">
          <span>{{ currentFile || 'Restoring...' }}</span>
          <span>{{ progress }}%</span>
        </div>
        <div class="progress-bar">
          <div class="progress-bar-fill" :style="{ width: progress + '%' }"></div>
        </div>
      </div>
      
      <button class="btn btn-danger" @click="cancelRestore">Cancel Restore</button>
    </div>
    
    <!-- Restore Complete -->
    <div v-else-if="restoreStatus === 'completed'" class="restore-complete card">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--success)" stroke-width="2">
        <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
      </svg>
      <h2>Restore Complete!</h2>
      <p>Files have been restored to {{ targetPath }}</p>
      <button class="btn btn-primary" @click="restoreStatus = 'idle'">Start New Restore</button>
    </div>
    
    <!-- Restore Form -->
    <form v-else class="restore-form" @submit.prevent="startRestore">
      <div v-if="error" class="error-message">{{ error }}</div>
      
      <section class="form-section card">
        <h2>Select Snapshot</h2>
        
        <div class="snapshot-select">
          <label for="snapshot">Snapshot to restore</label>
          <select id="snapshot" v-model="selectedSnapshot">
            <option value="">Choose a snapshot...</option>
            <option v-for="s in snapshotStore.sortedSnapshots" :key="s.id" :value="s.id">
              {{ s.name || s.id }} - {{ new Date(s.createdAt).toLocaleDateString() }}
            </option>
          </select>
        </div>
      </section>
      
      <section class="form-section card">
        <h2>Restore Location</h2>
        
        <div class="form-group">
          <label for="target-path">Target directory</label>
          <input id="target-path" v-model="targetPath" placeholder="/path/to/restore/location">
        </div>
      </section>
      
      <section class="form-section card">
        <h2>Options</h2>
        
        <div class="form-options">
          <label class="checkbox-label">
            <input type="checkbox" v-model="overwriteExisting">
            <span>Overwrite existing files</span>
          </label>
          
          <label class="checkbox-label">
            <input type="checkbox" v-model="preserveAttributes">
            <span>Preserve file attributes</span>
          </label>
          
          <label class="checkbox-label">
            <input type="checkbox" v-model="verifyIntegrity">
            <span>Verify integrity after restore</span>
          </label>
        </div>
      </section>
      
      <button type="submit" class="btn btn-primary btn-large" :disabled="!selectedSnapshot">
        Start Restore
      </button>
    </form>
  </div>
</template>

<style scoped>
.restore-page {
  max-width: 800px;
}

.restore-running,
.restore-complete {
  margin-bottom: 2rem;
}

.running-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.progress-section {
  margin-bottom: 1.5rem;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.restore-complete {
  text-align: center;
  padding: 3rem;
}

.restore-complete h2 {
  color: var(--success);
  margin: 1rem 0 0.5rem;
}

.restore-complete p {
  color: var(--text-secondary);
  margin-bottom: 1.5rem;
}

.error-message {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid var(--error);
  color: var(--error);
  padding: 1rem;
  border-radius: var(--radius-sm);
  margin-bottom: 1.5rem;
}

.form-section {
  margin-bottom: 1.5rem;
}

.form-section h2 {
  font-size: 1.1rem;
  margin-bottom: 1rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.snapshot-select label {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.form-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
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

.btn-large {
  width: 100%;
  padding: 1rem;
  font-size: 1rem;
}

.btn-large:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
