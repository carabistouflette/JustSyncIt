<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useBackupStore } from '../stores/backup'
import { useSnapshotStore } from '../stores/snapshot'
import { useToast } from '../composables/useToast'
import { healthApi } from '../services/api'
import StorageChart from '../components/charts/StorageChart.vue'
import BackupHistoryChart from '../components/charts/BackupHistoryChart.vue'
import FileTypeDistChart from '../components/charts/FileTypeDistChart.vue'

const backupStore = useBackupStore()
const snapshotStore = useSnapshotStore()
const toast = useToast()

const serverHealth = ref(null)
const loading = ref(true)
const fileTypeStats = ref({})
const statsLoading = ref(false)

// ... existing code ...

const loadFileTypeStats = async () => {
    // Get latest snapshot ID
    const latest = snapshotStore.snapshots[0]
    if (latest) {
        statsLoading.value = true
        try {
            const { snapshotApi } = await import('../services/api')
            const response = await snapshotApi.getStats(latest.id)
            fileTypeStats.value = response.data.fileTypes
        } catch (e) {
            console.error('Failed to load stats', e)
        } finally {
            statsLoading.value = false
        }
    }
}

onMounted(async () => {
  try {
    await Promise.all([
      snapshotStore.fetchSnapshots(),
      backupStore.fetchHistory(),
      healthApi.check().then(res => { serverHealth.value = res.data })
    ])
    loadFileTypeStats()
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
    toast.error('Could not load dashboard data', 'Connection Error')
  } finally {
    loading.value = false
  }
})

// Stats for top cards
const stats = computed(() => [
  { 
    label: 'Total Snapshots', 
    value: snapshotStore.snapshots.length,
    icon: 'M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4',
    color: 'var(--accent-primary)'
  },
  { 
    label: 'Server Status', 
    value: serverHealth.value ? 'Online' : 'Offline',
    icon: 'M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2',
    color: serverHealth.value ? 'var(--success)' : 'var(--error)'
  }
])

function formatDate(timestamp) {
  return new Date(timestamp).toLocaleDateString('en-US', { 
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  })
}

function formatBytes(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatFilePath(path) {
  if (!path) return ''
  const parts = path.split('/')
  return parts.length > 3 ? '.../' + parts.slice(-2).join('/') : path
}

// Watch for backup completion to show toast
watch(() => backupStore.status, (newStatus, oldStatus) => {
  if (newStatus === 'completed' && oldStatus === 'running') {
    toast.success('Backup completed successfully!', 'Backup Finished')
  } else if (newStatus === 'failed' && oldStatus === 'running') {
    toast.error('Backup failed. Check logs.', 'Backup Failed')
  }
})

onMounted(async () => {
  try {
    await Promise.all([
      snapshotStore.fetchSnapshots(),
      backupStore.fetchHistory(),
      healthApi.check().then(res => { serverHealth.value = res.data })
    ])
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
    toast.error('Could not load dashboard data', 'Connection Error')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h1 class="page-title">Dashboard</h1>
      <div class="last-updated" v-if="!loading">
        Updated: {{ new Date().toLocaleTimeString() }}
      </div>
    </div>
    
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>Loading overview...</p>
    </div>
    
    <template v-else>
      <!-- Top Stats Row -->
      <div class="stats-grid">
        <div v-for="stat in stats" :key="stat.label" class="stat-card card hover-effect">
          <div class="stat-icon" :style="{ background: stat.color + '20', color: stat.color }">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path :d="stat.icon" />
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>
        
        <!-- Quick Backup Action -->
        <router-link to="/backup" class="stat-card card hover-effect action-highlight">
          <div class="stat-icon" style="background: rgba(99, 102, 241, 0.2); color: var(--accent-primary)">
             <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">New Backup</div>
            <div class="stat-label">Start process</div>
          </div>
        </router-link>
        <div class="stat-card card hover-effect" :class="{ 'active-process': backupStore.isRunning }">
          <div class="stat-icon" style="background: rgba(245, 158, 11, 0.1); color: var(--warning)">
            <i class="fas fa-sync-alt" :class="{ 'fa-spin': backupStore.isRunning }"></i>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" v-if="!backupStore.isRunning">
              <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            <div v-else class="spinner-mini"></div>
          </div>
          <div class="stat-content">
            <div class="stat-header-flex">
              <div class="stat-label">Backup Status</div>
              <span class="status-badge" :class="backupStore.status">{{ backupStore.status }}</span>
            </div>
            
            <div v-if="backupStore.isRunning" class="progress-mini-container">
               <div class="progress-bar-sm">
                  <div class="progress-fill" :style="{ width: backupStore.progressPercent + '%' }"></div>
               </div>
               <div class="progress-details">
                  <span class="percent">{{ backupStore.progressPercent }}%</span>
                  <span class="current-file-sm" :title="backupStore.currentFile">
                    {{ formatFilePath(backupStore.currentFile) }}
                  </span>
               </div>
            </div>
            <div v-else class="stat-value">
              {{ backupStore.status === 'idle' ? 'Ready' : (backupStore.status === 'completed' ? 'Done' : 'Error') }}
            </div>
          </div>
        </div>
      </div>

      <!-- Charts Row -->
      <div class="charts-grid">
        <div class="card chart-card">
          <h3>Storage Overview</h3>
          <StorageChart 
            :files-processed="backupStore.filesProcessed" 
            :bytes-processed="backupStore.bytesProcessed" 
          />
        </div>
        <div class="card chart-card">
          <h3>File Types Distribution (Latest Snapshot)</h3>
          <FileTypeDistChart 
            :data="fileTypeStats"
            :loading="statsLoading"
          />
        </div>
        <div class="card chart-card full-width">
          <h3>Backup History (Last 7 Days)</h3>
          <BackupHistoryChart :history="backupStore.history" />
        </div>
      </div>
      
      <!-- Current Backup Status -->
      <Transition name="fade">
        <div v-if="backupStore.isRunning" class="backup-progress card">
          <div class="progress-header">
            <h3>
              <span class="pulse-dot"></span>
              Backup in Progress
            </h3>
            <span class="percentage">{{ backupStore.progressPercent }}%</span>
          </div>
          
          <div class="progress-bar">
            <div class="progress-bar-fill" :style="{ width: backupStore.progressPercent + '%' }"></div>
          </div>
          
          <div class="progress-info">
            <div class="activity-details">
               <span class="activity-status">{{ backupStore.currentActivity || 'Processing...' }}</span>
               <span class="current-file" v-if="backupStore.currentFile">{{ formatFilePath(backupStore.currentFile) }}</span>
            </div>
            <span class="progress-percent">{{ backupStore.progressPercent }}%</span>
          </div>
        </div>
      </Transition>
      
      <!-- Activity Log -->
      <div v-if="backupStore.logs.length > 0" class="card log-card">
        <div class="log-header">
           <h3>Live Activity</h3>
           <div class="log-actions">
             <span class="log-count">{{ backupStore.logs.length }} events</span>
           </div>
        </div>
        <div class="log-container">
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

      <!-- Recent Activity Table -->
      <div class="recent-backups">
        <h2>Recent Activity</h2>
        <div class="card table-card">
          <table class="data-table">
            <thead>
              <tr>
                <th>Snapshot ID</th>
                <th>Date</th>
                <th>Files</th>
                <th>Size</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="backup in backupStore.history.slice(0, 5)" :key="backup.snapshotId">
                <td class="font-mono">{{ backup.snapshotId.substring(0, 8) }}...</td>
                <td>{{ formatDate(backup.timestamp) }}</td>
                <td>{{ backup.filesProcessed }}</td>
                <td>{{ formatBytes(backup.bytesProcessed) }}</td>
                <td>
                  <span class="badge" :class="'badge-' + (backup.status === 'completed' ? 'success' : backup.status === 'failed' ? 'error' : 'warning')">
                    {{ backup.status }}
                  </span>
                </td>
              </tr>
              <tr v-if="backupStore.history.length === 0">
                <td colspan="5" class="empty-state">No backups found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.last-updated {
  font-size: 0.85rem;
  color: var(--text-muted);
}

/* Stats Row */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
}

.hover-effect:hover {
  transform: translateY(-4px);
  border-color: var(--border-glow);
  box-shadow: var(--shadow-glow);
}

.action-highlight {
  cursor: pointer;
  background: linear-gradient(145deg, var(--bg-secondary), var(--bg-tertiary));
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

/* Charts Row */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.full-width {
  grid-column: 1 / -1;
}

.chart-card {
  min-height: 300px;
  display: flex;
  flex-direction: column;
}

.chart-card h3 {
  font-size: 1rem;
  color: var(--text-secondary);
  margin-bottom: 1.5rem;
}

/* Progress Card */
.backup-progress {
  margin-bottom: 2rem;
  background: linear-gradient(145deg, rgba(16, 18, 27, 0.8), rgba(26, 27, 46, 0.4));
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.progress-header h3 {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 1.1rem;
  color: var(--text-primary);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--warning);
  box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7);
  animation: pulse-orange 2s infinite;
}

@keyframes pulse-orange {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(245, 158, 11, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
}

.percentage {
  font-family: monospace;
  font-weight: 700;
  color: var(--accent-primary);
}

.progress-stats {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.75rem;
  font-size: 0.85rem;
}

.current-file {
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60%;
}

.stats-detail {
  display: flex;
  gap: 1rem;
  color: var(--text-muted);
}

/* Data Table */
.recent-backups h2 {
  font-size: 1.25rem;
  margin-bottom: 1rem;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 1rem 1.5rem;
  text-align: left;
}

.data-table th {
  background: rgba(0, 0, 0, 0.2);
  color: var(--text-secondary);
  font-weight: 600;
}

.data-table td {
  border-bottom: 1px solid var(--border-color);
}

.data-table tr:hover {
  background: rgba(255, 255, 255, 0.02);
}

.font-mono {
  font-family: 'JetBrains Mono', monospace;
  color: var(--accent-secondary);
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-muted);
}

/* Loading */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: var(--text-secondary);
}

/* Transitions */
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* Progress styles for status card */
.active-process {
  border-color: var(--accent-primary) !important;
  box-shadow: 0 0 15px rgba(59, 130, 246, 0.2) !important;
}

.stat-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.progress-mini-container {
  margin-top: 0.5rem;
  width: 100%;
}

.progress-bar-sm {
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 0.5rem;
}

.progress-fill {
  height: 100%;
  background: var(--accent-primary);
  transition: width 0.3s ease;
  box-shadow: 0 0 10px var(--accent-primary);
}

.progress-details {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.percent {
  font-weight: bold;
  color: var(--accent-primary);
  margin-right: 0.5rem;
}

.current-file-sm {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
  opacity: 0.8;
}

.spinner-mini {
  width: 24px;
  height: 24px;
  border: 2px solid rgba(245, 158, 11, 0.3);
  border-radius: 50%;
  border-top-color: var(--warning);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
