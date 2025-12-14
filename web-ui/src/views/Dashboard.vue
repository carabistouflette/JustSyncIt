<script setup>
import { ref, onMounted, computed } from 'vue'
import { useBackupStore } from '../stores/backup'
import { useSnapshotStore } from '../stores/snapshot'
import { healthApi } from '../services/api'

const backupStore = useBackupStore()
const snapshotStore = useSnapshotStore()

const serverHealth = ref(null)
const loading = ref(true)

const stats = computed(() => [
  { 
    label: 'Snapshots', 
    value: snapshotStore.snapshots.length,
    icon: 'M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4',
    color: 'var(--accent-primary)'
  },
  { 
    label: 'Backup Status', 
    value: backupStore.status,
    icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
    color: backupStore.isRunning ? 'var(--warning)' : 'var(--success)'
  },
  { 
    label: 'Server', 
    value: serverHealth.value ? 'Online' : 'Offline',
    icon: 'M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2',
    color: serverHealth.value ? 'var(--success)' : 'var(--error)'
  },
  { 
    label: 'Last Backup', 
    value: backupStore.history[0]?.timestamp ? formatDate(backupStore.history[0].timestamp) : 'Never',
    icon: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z',
    color: 'var(--info)'
  }
])

function formatDate(timestamp) {
  return new Date(timestamp).toLocaleDateString('en-US', { 
    month: 'short', 
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(async () => {
  try {
    await Promise.all([
      snapshotStore.fetchSnapshots(),
      backupStore.fetchHistory(),
      healthApi.check().then(res => { serverHealth.value = res.data })
    ])
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dashboard">
    <h1 class="page-title">Dashboard</h1>
    
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>Loading dashboard...</p>
    </div>
    
    <template v-else>
      <!-- Stats Grid -->
      <div class="stats-grid">
        <div v-for="stat in stats" :key="stat.label" class="stat-card card">
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
      </div>
      
      <!-- Backup Progress (if running) -->
      <div v-if="backupStore.isRunning" class="backup-progress card">
        <h3>Backup in Progress</h3>
        <div class="progress-info">
          <span>{{ backupStore.currentFile || 'Starting...' }}</span>
          <span>{{ backupStore.progressPercent }}%</span>
        </div>
        <div class="progress-bar">
          <div class="progress-bar-fill" :style="{ width: backupStore.progressPercent + '%' }"></div>
        </div>
        <div class="progress-stats">
          <span>{{ backupStore.filesProcessed }} files</span>
          <span>{{ formatBytes(backupStore.bytesProcessed) }}</span>
        </div>
      </div>
      
      <!-- Quick Actions -->
      <div class="quick-actions">
        <h2>Quick Actions</h2>
        <div class="actions-grid">
          <router-link to="/backup" class="action-card card">
            <div class="action-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
              </svg>
            </div>
            <span>Start Backup</span>
          </router-link>
          
          <router-link to="/restore" class="action-card card">
            <div class="action-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
              </svg>
            </div>
            <span>Restore Files</span>
          </router-link>
          
          <router-link to="/files" class="action-card card">
            <div class="action-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
              </svg>
            </div>
            <span>Browse Files</span>
          </router-link>
          
          <router-link to="/snapshots" class="action-card card">
            <div class="action-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4" />
              </svg>
            </div>
            <span>View Snapshots</span>
          </router-link>
        </div>
      </div>
      
      <!-- Recent Backups -->
      <div class="recent-backups" v-if="backupStore.history.length">
        <h2>Recent Backups</h2>
        <div class="backup-list">
          <div v-for="backup in backupStore.history.slice(0, 5)" :key="backup.snapshotId" class="backup-item card">
            <div class="backup-info">
              <span class="backup-id">{{ backup.snapshotId }}</span>
              <span class="backup-stats">{{ backup.filesProcessed }} files • {{ formatBytes(backup.bytesProcessed) }}</span>
            </div>
            <span class="badge" :class="'badge-' + (backup.status === 'completed' ? 'success' : backup.status === 'failed' ? 'error' : 'warning')">
              {{ backup.status }}
            </span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
function formatBytes(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 4rem;
  color: var(--text-secondary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1.5rem;
  margin-bottom: 2rem;
}

@media (max-width: 1024px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 640px) {
  .stats-grid { grid-template-columns: 1fr; }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 1rem;
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
  font-weight: 600;
  text-transform: capitalize;
}

.stat-label {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.backup-progress {
  margin-bottom: 2rem;
}

.backup-progress h3 {
  margin-bottom: 1rem;
  color: var(--warning);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.progress-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.quick-actions h2,
.recent-backups h2 {
  font-size: 1.25rem;
  margin-bottom: 1rem;
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin-bottom: 2rem;
}

@media (max-width: 1024px) {
  .actions-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 640px) {
  .actions-grid { grid-template-columns: 1fr; }
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 2rem 1.5rem;
  text-align: center;
  cursor: pointer;
}

.action-card:hover {
  transform: translateY(-4px);
}

.action-icon {
  color: var(--accent-primary);
}

.action-card span {
  font-weight: 500;
}

.backup-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.backup-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
}

.backup-id {
  font-family: monospace;
  font-size: 0.9rem;
}

.backup-stats {
  font-size: 0.85rem;
  color: var(--text-secondary);
  display: block;
  margin-top: 0.25rem;
}
</style>
