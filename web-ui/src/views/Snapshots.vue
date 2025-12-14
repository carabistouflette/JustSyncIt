<script setup>
import { onMounted, computed } from 'vue'
import { useSnapshotStore } from '../stores/snapshot'

const snapshotStore = useSnapshotStore()

const formattedSnapshots = computed(() => 
  snapshotStore.sortedSnapshots.map(s => ({
    ...s,
    formattedDate: new Date(s.createdAt).toLocaleString(),
    formattedSize: formatBytes(s.totalBytes)
  }))
)

function formatBytes(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

async function deleteSnapshot(id) {
  if (confirm('Are you sure you want to delete this snapshot?')) {
    await snapshotStore.deleteSnapshot(id)
  }
}

onMounted(() => {
  snapshotStore.fetchSnapshots()
})
</script>

<template>
  <div class="snapshots-page">
    <h1 class="page-title">Snapshots</h1>
    
    <div v-if="snapshotStore.loading" class="loading-state">
      <div class="spinner"></div>
      <p>Loading snapshots...</p>
    </div>
    
    <div v-else-if="formattedSnapshots.length === 0" class="empty-state card">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--text-muted)" stroke-width="2">
        <path d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4"/>
      </svg>
      <p>No snapshots found</p>
      <router-link to="/backup" class="btn btn-primary">Create First Backup</router-link>
    </div>
    
    <div v-else class="snapshot-list">
      <div v-for="snapshot in formattedSnapshots" :key="snapshot.id" class="snapshot-card card">
        <div class="snapshot-header">
          <div class="snapshot-info">
            <h3>{{ snapshot.name || snapshot.id }}</h3>
            <span class="snapshot-id">{{ snapshot.id }}</span>
          </div>
          <span class="badge badge-success">Complete</span>
        </div>
        
        <div class="snapshot-meta">
          <div class="meta-item">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <span>{{ snapshot.formattedDate }}</span>
          </div>
          <div class="meta-item">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            <span>{{ snapshot.fileCount }} files</span>
          </div>
          <div class="meta-item">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4"/>
            </svg>
            <span>{{ snapshot.formattedSize }}</span>
          </div>
        </div>
        
        <p v-if="snapshot.description" class="snapshot-desc">{{ snapshot.description }}</p>
        
        <div class="snapshot-actions">
          <router-link :to="`/restore?snapshot=${snapshot.id}`" class="btn btn-secondary">
            Restore
          </router-link>
          <button class="btn btn-secondary" @click="deleteSnapshot(snapshot.id)">
            Delete
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.snapshots-page {
  max-width: 900px;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 4rem;
  color: var(--text-secondary);
  text-align: center;
}

.snapshot-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.snapshot-card {
  padding: 1.5rem;
}

.snapshot-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.snapshot-info h3 {
  font-size: 1.1rem;
  margin-bottom: 0.25rem;
}

.snapshot-id {
  font-family: monospace;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.snapshot-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  margin-bottom: 1rem;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.snapshot-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin-bottom: 1rem;
  padding: 0.75rem;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
}

.snapshot-actions {
  display: flex;
  gap: 0.75rem;
}

@media (max-width: 640px) {
  .snapshot-meta {
    flex-direction: column;
    gap: 0.75rem;
  }
  
  .snapshot-actions {
    flex-direction: column;
  }
  
  .snapshot-actions .btn {
    width: 100%;
  }
}
</style>
