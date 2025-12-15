<script setup>
import { ref, onMounted } from 'vue'
import { useSchedulerStore } from '../stores/scheduler'
import { filesApi } from '../services/api'

const schedulerStore = useSchedulerStore()
const showCreateModal = ref(false)
const showFileBrowser = ref(false)
const files = ref([])
const browsePath = ref('')
const parentPath = ref(null)

const newSchedule = ref({
  name: '',
  sourcePath: '',
  type: 'interval',
  intervalMinutes: 60,
  enabled: true
})

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
    console.error('Failed to browse', e)
  }
}

function selectDirectory(entry) {
  if (entry.directory) {
    browseDirectory(entry.path)
  }
}

function selectCurrentDirectory() {
  if (browsePath.value) {
    newSchedule.value.sourcePath = browsePath.value
    showFileBrowser.value = false
  }
}

async function createSchedule() {
  try {
    await schedulerStore.createSchedule(newSchedule.value)
    showCreateModal.value = false
    // Reset form
    newSchedule.value = {
      name: '',
      sourcePath: '',
      type: 'interval',
      intervalMinutes: 60,
      enabled: true
    }
  } catch (e) {
    // Error handled in store
  }
}

async function deleteSchedule(id) {
  if (confirm('Are you sure you want to delete this schedule?')) {
    await schedulerStore.deleteSchedule(id)
  }
}

function openCreateModal() {
  showCreateModal.value = true
}

onMounted(() => {
  schedulerStore.fetchSchedules()
})
</script>

<template>
  <div class="schedule-page">
    <div class="page-header">
      <h1 class="page-title">Scheduled Backups</h1>
      <button class="btn btn-primary" @click="openCreateModal">
        + New Schedule
      </button>
    </div>

    <div v-if="schedulerStore.loading && !schedulerStore.schedules.length" class="loading-state">
      <div class="spinner"></div>
    </div>

    <div v-else-if="schedulerStore.schedules.length === 0" class="empty-state card">
      <div class="empty-icon">📅</div>
      <h3>No scheduled backups</h3>
      <p>Create a schedule to automatically back up your files regularly.</p>
    </div>

    <div v-else class="schedules-grid">
      <div v-for="schedule in schedulerStore.schedules" :key="schedule.id" class="schedule-card card">
        <div class="schedule-header">
          <h3>{{ schedule.name }}</h3>
          <span class="badge" :class="schedule.enabled ? 'badge-success' : 'badge-secondary'">
            {{ schedule.enabled ? 'Active' : 'Paused' }}
          </span>
        </div>
        
        <div class="schedule-details">
          <div class="detail-row">
            <span class="label">Source:</span>
            <span class="value" :title="schedule.sourcePath">{{ formatFilePath(schedule.sourcePath) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">Frequency:</span>
            <span class="value">Every {{ schedule.intervalMinutes }} minutes</span>
          </div>
          <div class="detail-row">
            <span class="label">Last Run:</span>
            <span class="value">{{ schedule.lastRun ? new Date(schedule.lastRun).toLocaleString() : 'Never' }}</span>
          </div>
          <div class="detail-row" v-if="schedule.lastResult">
             <span class="label">Last Result:</span>
             <span class="value" :class="schedule.lastResult.startsWith('Success') ? 'text-success' : 'text-error'">
               {{ schedule.lastResult }}
             </span>
          </div>
        </div>
        
        <div class="schedule-actions">
           <button class="btn btn-sm btn-danger-outline" @click="deleteSchedule(schedule.id)">Delete</button>
        </div>
      </div>
    </div>

    <!-- Create Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal card">
        <div class="modal-header">
          <h3>Create New Schedule</h3>
          <button type="button" class="btn-icon" @click="showCreateModal = false">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 18L18 6M6 6l12 12"/></svg>
          </button>
        </div>
        
        <form @submit.prevent="createSchedule" class="create-form">
          <div class="form-group">
            <label>Name</label>
            <input v-model="newSchedule.name" placeholder="e.g. Daily Documents" required>
          </div>
          
          <div class="form-group">
            <label>Source Directory</label>
            <div class="input-group">
              <input v-model="newSchedule.sourcePath" placeholder="/path/to/backup" required readonly>
              <button type="button" class="btn btn-secondary" @click="showFileBrowser = true; browseDirectory('/')">Browse</button>
            </div>
          </div>
          
          <div class="form-group">
            <label>Interval (minutes)</label>
            <input type="number" v-model.number="newSchedule.intervalMinutes" min="1" required>
            <span class="hint">e.g. 60 for hourly, 1440 for daily</span>
          </div>
          
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="showCreateModal = false">Cancel</button>
            <button type="submit" class="btn btn-primary" :disabled="schedulerStore.loading">Create Schedule</button>
          </div>
        </form>
      </div>
    </div>
    
    <!-- File Browser Modal (Reuse from Backup.vue logic essentially) -->
    <div v-if="showFileBrowser" class="modal-overlay" @click.self="showFileBrowser = false">
      <div class="modal card">
        <div class="modal-header">
          <h3>Select Source Directory</h3>
          <button type="button" class="btn-icon" @click="showFileBrowser = false">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 18L18 6M6 6l12 12"/></svg>
          </button>
        </div>
        
        <div class="browser-path">
          <button class="btn btn-secondary" @click="browseDirectory('/')" :disabled="!parentPath" title="Go to Root">Root</button>
          <button class="btn btn-secondary" @click="browseDirectory(parentPath)" :disabled="!parentPath" title="Go Up">Up</button>
          <span>{{ browsePath || '/' }}</span>
        </div>
        
        <div class="file-list">
          <div v-for="file in files" :key="file.path" class="file-item" :class="{ directory: file.directory }" @click="selectDirectory(file)">
            <span class="icon">{{ file.directory ? '📁' : '📄' }}</span>
            <span>{{ file.name }}</span>
          </div>
        </div>
        
        <div class="modal-actions">
          <button type="button" class="btn btn-secondary" @click="showFileBrowser = false">Cancel</button>
          <button type="button" class="btn btn-primary" @click="selectCurrentDirectory">Select This Directory</button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.schedules-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
}

.schedule-card {
  display: flex;
  flex-direction: column;
}

.schedule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-color);
}

.schedule-header h3 {
  margin: 0;
  font-size: 1.1rem;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

.detail-row .label {
  color: var(--text-secondary);
}

.detail-row .value {
  font-family: monospace;
}

.schedule-actions {
  margin-top: auto;
  padding-top: 1rem;
  display: flex;
  justify-content: flex-end;
}

.text-success { color: var(--success); }
.text-error { color: var(--error); }

.empty-state {
  text-align: center;
  padding: 3rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.empty-icon {
  font-size: 3rem;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  width: 90%;
  max-width: 500px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
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

.form-group input {
  width: 100%;
  padding: 0.75rem;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
}

.input-group {
  display: flex;
  gap: 0.5rem;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.file-list {
  flex: 1;
  overflow-y: auto;
  max-height: 300px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  margin-bottom: 1rem;
}

.file-item {
  padding: 0.5rem 1rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-bottom: 1px solid var(--border-color);
}

.file-item:hover {
  background: var(--bg-tertiary);
}

.browser-path {
  margin-bottom: 1rem;
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
</style>
