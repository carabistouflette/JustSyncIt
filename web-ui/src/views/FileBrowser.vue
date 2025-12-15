<script setup>
import { ref, computed } from 'vue'
import { filesApi } from '../services/api'
import { useToast } from '../composables/useToast'

const toast = useToast()
const currentPath = ref('/')
const files = ref([])
const loading = ref(false)
const error = ref(null)
const searchQuery = ref('')
const searchResults = ref([])
const showHidden = ref(false)

const parentPath = ref(null)

// Sorting
const sortBy = ref('name') // name, size, modifiedAt
const sortDesc = ref(false)

// Selection
const selectedFiles = ref(new Set())

async function browse(path = '/') {
  loading.value = true
  error.value = null
  selectedFiles.value.clear()
  try {
    const response = await filesApi.browse(path, showHidden.value)
    files.value = response.data.entries || []
    currentPath.value = response.data.path || path
    parentPath.value = response.data.parent
    searchResults.value = []
    searchQuery.value = ''
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  } finally {
    loading.value = false
  }
}

async function search() {
  if (!searchQuery.value.trim()) {
    searchResults.value = []
    return
  }
  
  loading.value = true
  try {
    const response = await filesApi.search(currentPath.value, searchQuery.value)
    searchResults.value = response.data.results || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message
  } finally {
    loading.value = false
  }
}

function navigateUp() {
  if (parentPath.value) {
    browse(parentPath.value)
  }
}

function handleClick(entry) {
  if (entry.directory) {
    browse(entry.path)
  } else {
    toggleSelection(entry)
  }
}

function toggleSelection(entry) {
  if (selectedFiles.value.has(entry.path)) {
    selectedFiles.value.delete(entry.path)
  } else {
    selectedFiles.value.add(entry.path)
  }
}

function selectAll() {
  if (selectedFiles.value.size === processedFiles.value.length) {
    selectedFiles.value.clear()
  } else {
    processedFiles.value.forEach(f => selectedFiles.value.add(f.path))
  }
}

function formatSize(bytes) {
  if (!bytes) return '-'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatDate(timestamp) {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString()
}

function toggleSort(field) {
  if (sortBy.value === field) {
    sortDesc.value = !sortDesc.value
  } else {
    sortBy.value = field
    sortDesc.value = false
  }
}

const processedFiles = computed(() => {
  let result = [...files.value]
  
  // Sorting
  result.sort((a, b) => {
    // Directories usually first
    if (a.directory !== b.directory) {
      return a.directory ? -1 : 1
    }
    
    let aVal = a[sortBy.value]
    let bVal = b[sortBy.value]
    
    // Case insensitive string sort
    if (typeof aVal === 'string') {
      aVal = aVal.toLowerCase()
      bVal = bVal.toLowerCase()
    }
    
    if (aVal < bVal) return sortDesc.value ? 1 : -1
    if (aVal > bVal) return sortDesc.value ? -1 : 1
    return 0
  })

  // Client-side filtering if search is empty but user types something? 
  // For now search is explicit backend search. 
  // We can add a "Filter" box separate from Search if needed, but keeping it simple.
  
  return result
})

function copySelectedPaths() {
  const paths = Array.from(selectedFiles.value).join('\n')
  navigator.clipboard.writeText(paths)
  toast.success(`Copied ${selectedFiles.value.size} paths to clipboard`)
  selectedFiles.value.clear()
}

// Initial load
browse('/')
</script>

<template>
  <div class="file-browser-page">
    <h1 class="page-title">File Browser</h1>
    
    <!-- Toolbar -->
    <div class="toolbar card">
      <div class="path-bar">
        <button class="btn btn-secondary" @click="browse('/')" :disabled="currentPath === '/'">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
          </svg>
        </button>
        <button class="btn btn-secondary" @click="navigateUp" :disabled="currentPath === '/'">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M5 15l7-7 7 7"/>
          </svg>
        </button>
        <span class="current-path">{{ currentPath }}</span>
      </div>
      
      <div class="search-bar">
        <input 
          v-model="searchQuery" 
          @keyup.enter="search"
          placeholder="Deep search..."
        >
        <button class="btn btn-secondary" @click="search">Search</button>
      </div>
      
      <label class="checkbox-label hidden-toggle">
        <input type="checkbox" v-model="showHidden" @change="browse(currentPath)">
        <span>Show hidden</span>
      </label>
    </div>
    
    <!-- Bulk Actions -->
    <div v-if="selectedFiles.size > 0" class="bulk-actions card">
      <span>{{ selectedFiles.size }} selected</span>
      <div class="actions">
        <button class="btn btn-sm btn-secondary" @click="copySelectedPaths">Copy Paths</button>
        <button class="btn btn-sm btn-secondary" @click="selectedFiles.clear()">Clear Selection</button>
      </div>
    </div>
    
    <!-- Error -->
    <div v-if="error" class="error-message">{{ error }}</div>
    
    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
    </div>
    
    <!-- File List -->
    <div v-else class="file-list card">
      <div class="file-header">
        <span class="col-check">
          <input type="checkbox" 
            :checked="processedFiles.length > 0 && selectedFiles.size === processedFiles.length"
            :indeterminate="selectedFiles.size > 0 && selectedFiles.size < processedFiles.length"
            @change="selectAll"
          >
        </span>
        <span class="col-name sortable" @click="toggleSort('name')">
           Name 
           <span v-if="sortBy === 'name'" class="sort-icon">{{ sortDesc ? '↓' : '↑' }}</span>
        </span>
        <span class="col-size sortable" @click="toggleSort('size')">
           Size
           <span v-if="sortBy === 'size'" class="sort-icon">{{ sortDesc ? '↓' : '↑' }}</span>
        </span>
        <span class="col-date sortable" @click="toggleSort('modifiedAt')">
           Modified
           <span v-if="sortBy === 'modifiedAt'" class="sort-icon">{{ sortDesc ? '↓' : '↑' }}</span>
        </span>
      </div>
      
      <!-- Search Results -->
      <template v-if="searchResults.length > 0">
        <div class="search-label">Search Results ({{ searchResults.length }})</div>
        <div 
          v-for="file in searchResults" 
          :key="file.path" 
          class="file-row"
          :class="{ selected: selectedFiles.has(file.path) }"
          @click.stop="handleClick(file)"
        >
          <span class="col-check" @click.stop="">
             <input type="checkbox" :checked="selectedFiles.has(file.path)" @change="toggleSelection(file)">
          </span>
          <span class="col-name">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path v-if="file.directory" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"/>
              <path v-else d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            {{ file.path }}
          </span>
          <span class="col-size">{{ formatSize(file.size) }}</span>
          <span class="col-date">{{ formatDate(file.modifiedAt) }}</span>
        </div>
      </template>
      
      <!-- Directory Contents -->
      <template v-else>
        <div 
          v-for="file in processedFiles" 
          :key="file.path" 
          class="file-row"
          :class="{ directory: file.directory, selected: selectedFiles.has(file.path) }"
          @click.stop="handleClick(file)"
        >
          <span class="col-check" @click.stop="">
             <input type="checkbox" :checked="selectedFiles.has(file.path)" @change="toggleSelection(file)">
          </span>
          <span class="col-name">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path v-if="file.directory" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"/>
              <path v-else d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            {{ file.name }}
          </span>
          <span class="col-size">{{ formatSize(file.size) }}</span>
          <span class="col-date">{{ formatDate(file.modifiedAt) }}</span>
        </div>
        
        <div v-if="processedFiles.length === 0" class="empty-dir">
          <p>This directory is empty</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.file-browser-page {
  max-width: 1000px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  margin-bottom: 1.5rem;
  padding: 1rem;
}

.path-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
  min-width: 200px;
}

.current-path {
  padding: 0.5rem 1rem;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  font-family: monospace;
  font-size: 0.9rem;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-bar {
  display: flex;
  gap: 0.5rem;
  min-width: 200px;
}

.search-bar input {
  width: 200px;
}

.hidden-toggle {
  font-size: 0.85rem;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: var(--accent-primary);
}

.bulk-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  margin-bottom: 1rem;
  background: rgba(99, 102, 241, 0.1);
  border-left: 4px solid var(--accent-primary);
}

.error-message {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid var(--error);
  color: var(--error);
  padding: 1rem;
  border-radius: var(--radius-sm);
  margin-bottom: 1rem;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 3rem;
}

.file-list {
  overflow: hidden;
}

.file-header {
  display: grid;
  grid-template-columns: 40px 1fr 100px 150px;
  gap: 1rem;
  padding: 0.75rem 1rem;
  background: var(--bg-tertiary);
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.sortable {
  cursor: pointer;
  user-select: none;
}
.sortable:hover {
  color: var(--text-primary);
}
.sort-icon {
  margin-left: 0.25rem;
  font-size: 0.8rem;
}

.search-label {
  padding: 0.75rem 1rem;
  background: rgba(99, 102, 241, 0.1);
  font-size: 0.9rem;
  color: var(--accent-primary);
}

.file-row {
  display: grid;
  grid-template-columns: 40px 1fr 100px 150px;
  gap: 1rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.file-row:last-child {
  border-bottom: none;
}

.file-row:hover {
  background: var(--bg-tertiary);
}

.file-row.selected {
  background: rgba(99, 102, 241, 0.1);
}

.file-row.directory .col-name {
  color: var(--accent-primary);
}

.col-check {
  display: flex;
  align-items: center;
  justify-content: center;
}

.col-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-size,
.col-date {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.empty-dir {
  padding: 3rem;
  text-align: center;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .file-header,
  .file-row {
    grid-template-columns: 40px 1fr;
  }
  
  .col-size,
  .col-date {
    display: none;
  }
}
</style>
