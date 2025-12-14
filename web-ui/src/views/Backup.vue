<script setup>
import { ref, onMounted } from 'vue'
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
const showFileBrowser = ref(false)
const error = ref(null)

async function browseDirectory(path = '') {
  try {
    const response = await filesApi.browse(path, true)
    files.value = response.data.entries || []
    browsePath.value = path
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

onMounted(() => {
  backupStore.fetchStatus()
})
</script>

<template>
  <div class="backup-page">
    <h1 class="page-title">Backup</h1>
    
    <!-- Backup in Progress -->
    <div v-if="backupStore.isRunning" class="backup-running card">
      <div class="running-header">
        <h2>Backup in Progress</h2>
        <span class="badge badge-warning">Running</span>
      </div>
      
      <div class="progress-section">
        <div class="progress-info">
          <span class="current-file">{{ backupStore.currentFile || 'Processing...' }}</span>
          <span class="progress-percent">{{ backupStore.progressPercent }}%</span>
        </div>
        <div class="progress-bar">
          <div class="progress-bar-fill" :style="{ width: backupStore.progressPercent + '%' }"></div>
        </div>
        <div class="progress-stats">
          <span>{{ backupStore.filesProcessed }} files processed</span>
          <span>{{ formatBytes(backupStore.bytesProcessed) }}</span>
        </div>
      </div>
      
      <button class="btn btn-danger" @click="backupStore.cancelBackup">
        Cancel Backup
      </button>
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
          
          <button type="button" class="btn btn-secondary" @click="showFileBrowser = true; browseDirectory('/')">
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
          <button class="btn btn-secondary" @click="browseDirectory('/')" v-if="browsePath !== '/'">
            Root
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

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1.5rem;
}
</style>
