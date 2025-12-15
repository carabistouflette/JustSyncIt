<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '../stores/users'

const userStore = useUserStore()
const showModal = ref(false)
const modalMode = ref('create') // 'create' or 'edit'

const formData = ref({
  id: null,
  username: '',
  password: '',
  displayName: '',
  role: 'user'
})

function openCreateModal() {
  modalMode.value = 'create'
  formData.value = {
    id: null,
    username: '',
    password: '',
    displayName: '',
    role: 'user'
  }
  showModal.value = true
}

function openEditModal(user) {
  modalMode.value = 'edit'
  formData.value = {
    id: user.id,
    username: user.username,
    password: '', // Leave empty to keep unchanged
    displayName: user.displayName,
    role: user.role
  }
  showModal.value = true
}

async function handleSubmit() {
  try {
    if (modalMode.value === 'create') {
      await userStore.createUser({
        username: formData.value.username,
        password: formData.value.password,
        displayName: formData.value.displayName,
        role: formData.value.role
      })
    } else {
      const updateData = {
        displayName: formData.value.displayName,
        role: formData.value.role
      }
      if (formData.value.password) {
        updateData.password = formData.value.password
      }
      await userStore.updateUser(formData.value.id, updateData)
    }
    showModal.value = false
  } catch (e) {
    // Error handling in store, displayed via userStore.error if needed
    // But we might want to alert here
    alert('Operation failed: ' + (userStore.error || 'Unknown error'))
  }
}

async function deleteUser(user) {
  if (confirm(`Are you sure you want to delete user "${user.username}"?`)) {
    try {
      await userStore.deleteUser(user.id)
    } catch (e) {
      alert('Delete failed: ' + e.message)
    }
  }
}

onMounted(() => {
  userStore.fetchUsers()
})
</script>

<template>
  <div class="users-page">
    <div class="page-header">
      <h1 class="page-title">User Management</h1>
      <button class="btn btn-primary" @click="openCreateModal">
        + Add User
      </button>
    </div>

    <div v-if="userStore.loading && userStore.users.length === 0" class="loading-state">
      <div class="spinner"></div>
    </div>

    <div v-else class="card table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>Username</th>
            <th>Display Name</th>
            <th>Role</th>
            <th class="text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in userStore.users" :key="user.id">
            <td class="font-bold">{{ user.username }}</td>
            <td>{{ user.displayName }}</td>
            <td>
              <span class="badge" :class="user.role === 'admin' ? 'badge-primary' : 'badge-secondary'">
                {{ user.role }}
              </span>
            </td>
            <td class="actions-cell">
              <button class="btn-icon" @click="openEditModal(user)" title="Edit">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              </button>
              <button class="btn-icon text-danger" @click="deleteUser(user)" title="Delete" :disabled="user.role === 'admin' && user.username === 'admin'">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
              </button>
            </td>
          </tr>
          <tr v-if="userStore.users.length === 0">
            <td colspan="4" class="empty-state">No users found</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- User Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal card">
        <div class="modal-header">
          <h3>{{ modalMode === 'create' ? 'Create User' : 'Edit User' }}</h3>
          <button type="button" class="btn-icon" @click="showModal = false">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 18L18 6M6 6l12 12"/></svg>
          </button>
        </div>
        
        <form @submit.prevent="handleSubmit">
          <div class="form-group" v-if="modalMode === 'create'">
            <label>Username</label>
            <input v-model="formData.username" required :disabled="modalMode === 'edit'">
          </div>
           <div class="form-group" v-else>
            <label>Username</label>
            <input :value="formData.username" disabled class="disabled-input">
          </div>
          
          <div class="form-group">
            <label>Display Name</label>
            <input v-model="formData.displayName" required>
          </div>
          
          <div class="form-group">
            <label>Role</label>
            <select v-model="formData.role">
              <option value="user">User</option>
              <option value="admin">Administrator</option>
            </select>
          </div>
          
          <div class="form-group">
            <label>{{ modalMode === 'create' ? 'Password' : 'New Password (optional)' }}</label>
            <input type="password" v-model="formData.password" :required="modalMode === 'create'">
          </div>
          
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="showModal = false">Cancel</button>
            <button type="submit" class="btn btn-primary" :disabled="userStore.loading">
              {{ modalMode === 'create' ? 'Create' : 'Save Changes' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.users-page {
  max-width: 1000px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
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

.font-bold {
  font-weight: 600;
  color: var(--text-primary);
}

.text-right {
  text-align: right;
}

.actions-cell {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.btn-icon {
  padding: 0.5rem;
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  transition: all 0.2s;
}

.btn-icon:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.text-danger:hover {
  color: var(--error);
  background: rgba(239, 68, 68, 0.1);
}

.disabled-input {
  opacity: 0.7;
  cursor: not-allowed;
}

/* Modal Styles Reuse */
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

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.75rem;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}
</style>
