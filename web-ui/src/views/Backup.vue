<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useBackupStore } from '../stores/backup'
import { filesApi } from '../services/api'

const backupStore = useBackupStore()
const selectedSources = ref([])
const snapshotName = ref('')
const description = ref('')
const includeHidden = ref(false)
const verifyIntegrity = ref(true)
const browsePath = ref('')
const files = ref([])
const parentPath = ref(null)
const showFileBrowser = ref(false)
const error = ref(null)
let pollInterval = null

function formatFilePath(path) {
  if (!path) return ''
  const parts = path.split('/')
  return parts.length > 3 ? '.../' + parts.slice(-2).join('/') : path
}

async function browseDirectory(path = '') {
  try {
    const response = await filesApi.browse(path, true)
    files.value = response.data.entries || []
    browsePath.value = response.data.path || path
    parentPath.value = response.data.parent
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  }
}

function selectDirectory(entry) {
  if (entry.directory) {
    browseDirectory(entry.path)
  }
}

function addSource() {
  if (browsePath.value && !selectedSources.value.includes(browsePath.value)) {
    selectedSources.value.push(browsePath.value)
    showFileBrowser.value = false
  }
}

function removeSource(path) {
  selectedSources.value = selectedSources.value.filter(s => s !== path)
}

async function startBackup() {
  if (selectedSources.value.length === 0) {
    error.value = 'Please select at least one backup source'
    return
  }
  
  try {
    error.value = null
    await backupStore.startBackup({
      sourcePath: selectedSources.value[0],
      snapshotName: snapshotName.value || undefined,
      description: description.value || undefined,
      includeHidden: includeHidden.value,
      verifyIntegrity: verifyIntegrity.value
    })
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  }
}

function startPolling() {
  if (pollInterval) return
  // Fetch immediately then poll
  backupStore.fetchStatus()
  pollInterval = setInterval(() => {
    backupStore.fetchStatus()
  }, 1000)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

// Watch for backup running state to toggle polling
watch(() => backupStore.isRunning, (isRunning) => {
  if (isRunning) {
    startPolling()
  } else {
    // Continue polling for a few seconds after it stops to ensure we catch the final state
    setTimeout(() => {
      if (!backupStore.isRunning) {
        stopPolling()
        // One final fetch to ensure completion state is consistent
        backupStore.fetchStatus()
      }
    }, 2000)
  }
}, { immediate: true })

onMounted(() => {
  backupStore.fetchStatus()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="backup-page">
    <h1 class="page-title">Backup</h1>
    
    <!-- Backup in Progress -->
    <!-- Backup in Progress -->
    <div v-if="backupStore.isRunning">
      <div class="backup-running card">
          <div class="running-header">
            <h2>
               <span class="pulse-dot"></span>
               Backup in Progress
            </h2>
            <span class="badge badge-warning">Running</span>
          </div>
          
          <div class="progress-section">
            <div class="progress-info">
              <span class="percentage">{{ backupStore.progressPercent }}%</span>
            </div>
            <div class="progress-bar">
              <div class="progress-bar-fill" :style="{ width: backupStore.progressPercent + '%' }"></div>
            </div>
            <div class="progress-info" style="margin-top: 0.5rem; display: flex; flex-direction: column; align-items: flex-start;">
                 <span class="activity-status" style="font-weight: 600; font-size: 0.9em; margin-bottom: 0.25rem;">{{ backupStore.currentActivity || 'Preparing backup...' }}</span>
                 <span class="current-file" style="font-size: 0.85em; opacity: 0.8;" v-if="backupStore.currentFile">{{ formatFilePath(backupStore.currentFile) }}</span>
            </div>
            <div class="progress-stats">
              <span>{{ backupStore.filesProcessed }} files processed</span>
              <span>{{ formatBytes(backupStore.bytesProcessed) }}</span>
            </div>
          </div>
          
          <button class="btn btn-danger" @click="backupStore.cancelBackup">
            Stop Backup
          </button>
      </div>

       <!-- Activity Log -->
      <div class="card log-card">
        <div class="log-header">
           <h3>Live Activity</h3>
           <div class="log-actions">
             <span class="log-count">{{ backupStore.logs.length }} events</span>
           </div>
        </div>
        <div class="log-container">
          <div v-if="backupStore.logs.length === 0" class="log-entry">
               <span class="log-time">--:--:--</span>
               <span class="log-message" style="opacity: 0.5">Waiting for events...</span>
          </div>
          <div v-for="(log, index) in backupStore.logs" :key="index" class="log-entry" :class="log.level.toLowerCase()">
            <span class="log-time">{{ new Date(log.timestamp).toLocaleTimeString() }}</span>
            <span class="log-level">{{ log.level }}</span>
            <span class="log-message">
              {{ log.message }}
              <span v-if="log.file" class="log-file">{{ formatFilePath(log.file) }}</span>
            </span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Backup Form -->
    <form v-else class="backup-form" @submit.prevent="startBackup">
      <div v-if="error" class="error-message">
        {{ error }}
      </div>
      
      <!-- Source Selection -->
      <section class="form-section card">
        <h2>Source Directories</h2>
        <p class="section-desc">Select directories to include in this backup</p>
        
        <div class="selected-sources">
          <div v-for="source in selectedSources" :key="source" class="source-item">
            <span class="source-path">{{ source }}</span>
            <button type="button" class="btn-icon" @click="removeSource(source)">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
          
          <button type="button" class="btn btn-secondary" @click="showFileBrowser = true; browseDirectory(browsePath || '/')">
            + Add Directory
          </button>
        </div>
      </section>
      
      <!-- Options -->
      <section class="form-section card">
        <h2>Backup Options</h2>
        
        <div class="form-group">
          <label for="snapshot-name">Snapshot Name (optional)</label>
          <input id="snapshot-name" v-model="snapshotName" placeholder="Auto-generated if empty">
        </div>
        
        <div class="form-group">
          <label for="description">Description (optional)</label>
          <textarea id="description" v-model="description" rows="2" placeholder="Describe this backup"></textarea>
        </div>
        
        <div class="form-options">
          <label class="checkbox-label">
            <input type="checkbox" v-model="includeHidden">
            <span>Include hidden files</span>
          </label>
          
          <label class="checkbox-label">
            <input type="checkbox" v-model="verifyIntegrity">
            <span>Verify integrity after backup</span>
          </label>
        </div>
      </section>
      
      <button type="submit" class="btn btn-primary btn-large" :disabled="selectedSources.length === 0">
        Start Backup
      </button>
    </form>
    
    <!-- File Browser Modal -->
    <div v-if="showFileBrowser" class="modal-overlay" @click.self="showFileBrowser = false">
      <div class="modal card">
        <div class="modal-header">
          <h3>Select Directory</h3>
          <button type="button" class="btn-icon" @click="showFileBrowser = false">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
        
        <div class="browser-path">
          <button class="btn btn-secondary" @click="browseDirectory('/')" :disabled="!parentPath" title="Go to Root">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
            </svg>
          </button>
          <button class="btn btn-secondary" @click="browseDirectory(parentPath)" :disabled="!parentPath" title="Go Up">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 15l7-7 7 7"/>
            </svg>
          </button>
          <span>{{ browsePath || '/' }}</span>
        </div>
        
        <div class="file-list">
          <div 
            v-for="file in files" 
            :key="file.path" 
            class="file-item"
            :class="{ directory: file.directory }"
            @click="selectDirectory(file)"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path v-if="file.directory" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
              <path v-else d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <span>{{ file.name }}</span>
          </div>
        </div>
        
        <div class="modal-actions">
          <button type="button" class="btn btn-secondary" @click="showFileBrowser = false">Cancel</button>
          <button type="button" class="btn btn-primary" @click="addSource">Select This Directory</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
function formatBytes(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
.backup-page {
  max-width: 800px;
}

.backup-running {
  margin-bottom: 2rem;
}

.running-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.running-header h2 {
  margin: 0;
}

.progress-section {
  margin-bottom: 1.5rem;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.current-file {
  font-size: 0.9rem;
  color: var(--text-secondary);
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-percent {
  font-weight: 600;
  color: var(--accent-primary);
}

.progress-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
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
  margin-bottom: 0.5rem;
}

.section-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin-bottom: 1rem;
}

.selected-sources {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.source-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
}

.source-path {
  font-family: monospace;
  font-size: 0.9rem;
}

.btn-icon {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.btn-icon:hover {
  color: var(--error);
  background: rgba(239, 68, 68, 0.1);
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

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal {
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.modal-header h3 {
  margin: 0;
}

.browser-path {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  margin-bottom: 1rem;
  font-family: monospace;
  font-size: 0.9rem;
}

.file-list {
  flex: 1;
  overflow-y: auto;
  max-height: 300px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.file-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  cursor: pointer;
  border-bottom: 1px solid var(--border-color);
  transition: background var(--transition-fast);
}

.file-item:last-child {
  border-bottom: none;
}

.file-item:hover {
  background: var(--bg-tertiary);
}

.file-item.directory {
  color: var(--accent-primary);
}

/* Pulse Animation */
.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--warning);
  box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7);
  animation: pulse-orange 2s infinite;
  display: inline-block;
  margin-right: 0.5rem;
}

@keyframes pulse-orange {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(245, 158, 11, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
}

/* Activity Log Styles */
.log-card {
  margin-top: 1.5rem;
  margin-bottom: 2rem;
  max-height: 400px;
  display: flex;
  flex-direction: column;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.log-header h3 {
  font-size: 1.1rem;
  color: var(--text-primary);
  margin: 0;
}

.log-count {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.log-container {
  overflow-y: auto;
  flex: 1;
  background: rgba(0, 0, 0, 0.2);
  border-radius: var(--radius-sm);
  padding: 0.5rem;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  border: 1px solid var(--border-color);
  max-height: 300px;
}

.log-entry {
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  gap: 0.75rem;
  align-items: baseline;
}

.log-entry:last-child {
  border-bottom: none;
}

.log-time {
  color: var(--text-muted);
  font-size: 0.8em;
  white-space: nowrap;
}

.log-level {
  font-weight: 700;
  font-size: 0.8em;
  min-width: 45px;
}

.log-entry.info .log-level { color: var(--accent-primary); }
.log-entry.warn .log-level { color: var(--warning); }
.log-entry.error .log-level { color: var(--error); }

.log-message {
  color: var(--text-secondary);
  word-break: break-word;
}

.log-entry.warn .log-message { color: var(--text-primary); }
.log-entry.error .log-message { color: #fca5a5; }

.log-file {
  color: var(--text-muted);
  font-style: italic;
  margin-left: 0.5rem;
}

/* Modal and other styles remain... */
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1.5rem;
}
</style>
