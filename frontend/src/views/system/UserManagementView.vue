<template>
  <div class="user-management-view">
    <!-- Header -->
    <div class="config-header">
      <span class="config-title">User Management</span>
      <div class="header-actions">
        <el-button v-permission="'sys:user:add'" size="small" class="btn-primary" :icon="Plus" @click="openCreate">
          Add User
        </el-button>
        <el-button size="small" class="btn-secondary" :icon="Refresh" @click="loadUsers">
          Refresh
        </el-button>
      </div>
    </div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">Username:</span>
        <el-input
          v-model="query.username"
          size="small"
          placeholder="Filter username"
          clearable
          style="width: 140px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">Real Name:</span>
        <el-input
          v-model="query.realName"
          size="small"
          placeholder="Filter name"
          clearable
          style="width: 140px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">Status:</span>
        <el-select
          v-model="query.status"
          size="small"
          placeholder="All"
          clearable
          style="width: 100px"
          popper-class="alarm-config-popper"
          @change="handleSearch"
        >
          <el-option label="Active" :value="1" />
          <el-option label="Disabled" :value="0" />
        </el-select>
      </div>
      <div class="filter-actions">
        <el-button size="small" class="btn-primary" @click="handleSearch">Search</el-button>
        <el-button size="small" class="btn-secondary" @click="handleReset">Reset</el-button>
      </div>
    </div>

    <!-- User Table -->
    <div class="table-wrapper">
      <el-table
        :data="users"
        height="100%"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        size="small"
        border
        class="custom-table"
        v-loading="loading"
      >
        <el-table-column prop="userId" label="ID" width="55" align="center" header-align="center" />
        <el-table-column prop="username" label="Username" min-width="120" show-overflow-tooltip />
        <el-table-column prop="realName" label="Real Name" min-width="120" show-overflow-tooltip />
        <el-table-column label="Roles" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-for="g in (row as User).groups || []" :key="g.groupId" class="role-badge">
              {{ g.groupName }}
            </span>
            <span v-if="!row.groups || row.groups.length === 0" class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="Email" min-width="170" show-overflow-tooltip />
        <el-table-column prop="phone" label="Phone" width="110" show-overflow-tooltip />
        <el-table-column label="Status" width="75" align="center" header-align="center">
          <template #default="{ row }">
            <span :class="['status-badge', row.status === 1 ? 'active' : 'disabled']">
              {{ row.status === 1 ? 'Active' : 'Disabled' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="Last Login" width="145" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column prop="createTime" label="Created At" width="145" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="Actions" width="180" align="center" header-align="center">
          <template #default="{ row }">
            <div class="action-btn-grid">
              <div class="action-col">
                <el-button v-permission="'sys:user:edit'" type="primary" link size="small" @click="openEdit(row as User)">
                  Edit
                </el-button>
              </div>
              <div class="action-col">
                <el-button v-permission="'sys:user:edit'" type="primary" link size="small" @click="openGroups(row as User)">
                  Roles
                </el-button>
              </div>
              <div class="action-col">
                <el-button
                  v-permission="'sys:user:edit'"
                  :type="row.status === 1 ? 'warning' : 'success'"
                  link
                  size="small"
                  @click="toggleStatus(row as User)"
                >
                  {{ row.status === 1 ? 'Disable' : 'Enable' }}
                </el-button>
              </div>
              <div class="action-col">
                <el-button v-permission="'sys:user:delete'" type="danger" link size="small" @click="removeUser(row as User)">
                  Delete
                </el-button>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        size="small"
        @change="loadUsers"
      />
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="formVisible"
      :title="editingUser ? 'Edit User' : 'Add User'"
      width="540px"
      class="dark-dialog"
      :close-on-click-modal="false"
    >
      <el-form :model="form" size="small" label-width="120px" label-position="right">
        <el-form-item label="Username" required>
          <el-input v-model="form.username" :disabled="Boolean(editingUser)" placeholder="Login username" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="Initial Password" required>
          <el-input v-model="form.password" type="password" show-password placeholder="Minimum 6 characters" />
        </el-form-item>
        <el-form-item label="Real Name">
          <el-input v-model="form.realName" placeholder="Display name" />
        </el-form-item>
        <el-form-item label="Email">
          <el-input v-model="form.email" placeholder="user@domain.com" />
        </el-form-item>
        <el-form-item label="Phone">
          <el-input v-model="form.phone" placeholder="Phone number" />
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="Active" inactive-text="Disabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" class="btn-secondary" @click="formVisible = false">Cancel</el-button>
        <el-button size="small" class="btn-primary" :loading="saving" @click="saveUser">Save</el-button>
      </template>
    </el-dialog>

    <!-- Assign Roles Dialog -->
    <el-dialog v-model="groupVisible" title="Assign Roles" width="460px" class="dark-dialog" :close-on-click-modal="false">
      <div class="roles-dialog-body">
        <p class="dialog-tip">Select roles to assign for user <strong>{{ currentUser?.username }}</strong>:</p>
        <el-checkbox-group v-model="selectedGroupIds" class="roles-checkbox-group">
          <div v-for="group in groups" :key="group.groupId" class="role-checkbox-item">
            <el-checkbox :value="group.groupId">
              <span class="role-name">{{ group.groupName }}</span>
              <span class="role-desc">({{ group.groupCode }})</span>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button size="small" class="btn-secondary" @click="groupVisible = false">Cancel</el-button>
        <el-button size="small" class="btn-primary" :loading="saving" @click="saveGroups">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import { groupApi } from '@/api/group'
import type { Group, User, UserForm } from '@/api/types'

const loading = ref(false)
const saving = ref(false)
const users = ref<User[]>([])
const groups = ref<Group[]>([])
const total = ref(0)

const query = reactive<{ username?: string; realName?: string; status?: number; page: number; pageSize: number }>({
  username: '',
  realName: '',
  status: undefined,
  page: 1,
  pageSize: 20,
})

const formVisible = ref(false)
const groupVisible = ref(false)
const editingUser = ref<User | null>(null)
const currentUser = ref<User | null>(null)
const selectedGroupIds = ref<number[]>([])
const form = reactive<UserForm>({ username: '', password: '', realName: '', email: '', phone: '', status: 1 })

const headerCellStyle = {
  backgroundColor: '#0d1117',
  color: '#e5e7eb',
  fontSize: '10px',
  fontWeight: '600',
  borderBottom: '1px solid #2d3748',
}

const cellStyle = {
  backgroundColor: '#111827',
  color: '#e5e7eb',
  fontSize: '10px',
  borderBottom: '1px solid #1f2937',
}

async function loadUsers() {
  loading.value = true
  try {
    const data = await userApi.page(query)
    users.value = data.list || data.records || []
    total.value = data.total
  } catch (e) {
    console.error('Failed to load users:', e)
    ElMessage.error('Failed to load users')
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  try {
    groups.value = await groupApi.list({ status: 1 })
  } catch (e) {
    console.error('Failed to load groups:', e)
  }
}

function handleSearch() {
  query.page = 1
  loadUsers()
}

function handleReset() {
  query.username = ''
  query.realName = ''
  query.status = undefined
  query.page = 1
  loadUsers()
}

function openCreate() {
  editingUser.value = null
  Object.assign(form, { username: '', password: '', realName: '', email: '', phone: '', status: 1 })
  formVisible.value = true
}

function openEdit(row: User) {
  editingUser.value = row
  Object.assign(form, { username: row.username, realName: row.realName, email: row.email, phone: row.phone, status: row.status })
  formVisible.value = true
}

async function saveUser() {
  if (!form.username?.trim()) {
    ElMessage.warning('Username is required')
    return
  }
  saving.value = true
  try {
    if (editingUser.value) {
      await userApi.update(editingUser.value.userId, form)
    } else {
      if (!form.password) {
        ElMessage.warning('Password is required')
        saving.value = false
        return
      }
      if (form.password.length < 6) {
        ElMessage.warning('Password must be at least 6 characters long')
        saving.value = false
        return
      }
      await userApi.create(form)
    }
    ElMessage.success('Saved successfully')
    formVisible.value = false
    loadUsers()
  } catch (e) {
    console.error('Failed to save user:', e)
    ElMessage.error('Failed to save user')
  } finally {
    saving.value = false
  }
}

async function openGroups(row: User) {
  currentUser.value = row
  await loadGroups()
  const current = await userApi.groups(row.userId)
  selectedGroupIds.value = current.map((g) => g.groupId)
  groupVisible.value = true
}

async function saveGroups() {
  if (!currentUser.value) return
  saving.value = true
  try {
    await userApi.assignGroups(currentUser.value.userId, selectedGroupIds.value)
    ElMessage.success('Roles assigned successfully')
    groupVisible.value = false
    loadUsers()
  } catch (e) {
    console.error('Failed to assign roles:', e)
    ElMessage.error('Failed to assign roles')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: User) {
  const nextStatus = row.status === 1 ? 0 : 1
  const label = nextStatus === 1 ? 'Enable' : 'Disable'
  try {
    await ElMessageBox.confirm(`Are you sure you want to ${label.toLowerCase()} user "${row.username}"?`, 'Confirm Status Change', {
      type: 'warning',
      confirmButtonText: label,
      cancelButtonText: 'Cancel',
    })
    await userApi.updateStatus(row.userId, nextStatus)
    ElMessage.success(`User ${label.toLowerCase()}d successfully`)
    loadUsers()
  } catch {}
}

async function removeUser(row: User) {
  try {
    await ElMessageBox.confirm(`Are you sure you want to delete user "${row.username}"? This action cannot be undone.`, 'Confirm Delete', {
      type: 'warning',
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
    })
    await userApi.delete(row.userId)
    ElMessage.success('Deleted successfully')
    loadUsers()
  } catch {}
}

onMounted(() => {
  loadUsers()
  loadGroups()
})
</script>

<style scoped lang="scss">
.user-management-view {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  overflow: hidden;
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.config-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 4px 10px;
  background-color: #111827;
  border: 1px solid #1f2937;
  border-radius: 4px;
  flex-shrink: 0;

  .filter-item {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .filter-label {
    font-size: 10px !important;
    color: $text-secondary;
    white-space: nowrap;
    line-height: 20px !important;
  }

  .filter-actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    background-color: #0b1119 !important;
    height: 20px !important;
    min-height: 20px !important;
    line-height: 20px !important;
    padding: 0 6px !important;
    border-radius: 4px !important;
    border: 1px solid #1e293b !important;
    box-shadow: none !important;
    font-size: 10px !important;
  }

  :deep(.el-input__inner),
  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    color: #e5e7eb !important;

    &::placeholder {
      font-size: 10px !important;
      color: #64748b !important;
    }
  }
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 4px 0 0;
  flex-shrink: 0;
}

/* ========================================================== */
/* ABSOLUTE CSS GRID ACTION BUTTON MATRIX                     */
/* ========================================================== */
.action-btn-grid {
  display: grid;
  grid-template-columns: 28px 32px 44px 36px;
  column-gap: 6px;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin: 0 auto;

  .action-col {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 20px;
  }
}

/* ========================================================== */
/* BORDERLESS ROUNDED CAPSULE TAGS & BADGES                  */
/* ========================================================== */
.role-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  margin-right: 4px;
  margin-bottom: 2px;
  height: 16px;
  line-height: 16px;
  font-size: 10px;
  font-weight: 400;
  border-radius: 8px;
  border: none !important;
  background-color: rgba($accent-blue, 0.14) !important;
  color: #7dd3fc !important;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  line-height: 16px;
  font-size: 10px;
  font-weight: 400;
  border-radius: 8px;
  border: none !important;
  transition: all 0.2s ease-in-out;

  &.active {
    background-color: rgba($accent-green, 0.14) !important;
    color: #4ade80 !important;
  }
  &.disabled {
    background-color: rgba($accent-red, 0.16) !important;
    color: #fca5a5 !important;
  }
}

.text-muted {
  color: $text-muted;
  font-size: 10px;
}

.roles-dialog-body {
  padding: 4px 0;
}

.dialog-tip {
  font-size: 11px;
  color: $text-secondary;
  margin-bottom: 12px;
  strong {
    color: $accent-green;
  }
}

.roles-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-checkbox-item {
  padding: 6px 10px;
  background-color: #0b1119;
  border: 1px solid #1e293b;
  border-radius: 4px;
  .role-name {
    color: $text-primary;
    font-size: 11px;
    font-weight: 500;
  }
  .role-desc {
    color: $text-muted;
    font-size: 10px;
    margin-left: 6px;
  }
}

/* ========================================================== */
/* BORDERLESS TRANSLUCENT CAPSULE BUTTONS                     */
/* ========================================================== */
:deep(.el-button) {
  border-radius: 9px !important;
  border: none !important;
  font-size: 10px !important;
  font-weight: 400 !important;
  padding: 2px 8px !important;
  height: 20px !important;
  line-height: 16px !important;
  transition: all 0.2s ease-in-out;
  margin-left: 0 !important;

  &:hover {
    transform: translateY(-0.5px);
  }

  &.is-link {
    background-color: transparent !important;
    padding: 0 2px !important;
    border-radius: 4px !important;
    height: auto !important;
  }
}

.btn-primary, :deep(.el-button--primary:not(.is-link)) {
  background-color: rgba($accent-green, 0.16) !important;
  color: #34d399 !important;

  &:hover {
    background-color: rgba($accent-green, 0.28) !important;
    color: #6ee7b7 !important;
  }
}

.btn-secondary, :deep(.el-button--default:not(.is-link)), :deep(.el-button--info:not(.is-link)) {
  background-color: rgba($accent-blue, 0.16) !important;
  color: #93c5fd !important;

  &:hover {
    background-color: rgba($accent-blue, 0.28) !important;
    color: #bfdbfe !important;
  }
}

.btn-danger, :deep(.el-button--danger:not(.is-link)) {
  background-color: rgba($accent-red, 0.16) !important;
  color: #fca5a5 !important;

  &:hover {
    background-color: rgba($accent-red, 0.28) !important;
    color: #fecaca !important;
  }
}

// Dark table overrides (matching AlarmConfigView and AlarmTable)
:deep(.el-table) {
  background-color: #111827;
  color: #e5e7eb;
  --el-table-bg-color: #111827;
  --el-table-tr-bg-color: #111827;
  --el-table-row-hover-bg-color: #1a2332;
  --el-table-border-color: #1f2937;
  --el-table-header-bg-color: #0d1117;
  --el-table-text-color: #e5e7eb;
  --el-table-header-text-color: #e5e7eb;

  .el-table__inner-wrapper { background-color: #111827; }
  .el-table__body-wrapper { background-color: #111827; }
  .el-table__header-wrapper { background-color: #0d1117; }
  .el-table__row { background-color: #111827; }
  .el-table__row:hover > td { background-color: #1a2332 !important; }
  td, th { border-bottom: 1px solid #1f2937; }

  .el-table__cell { padding: 0 !important; }
  .el-table__cell .cell { padding: 0 4px !important; }
  .el-table__header-cell { padding: 0 4px !important; }
  td.el-table__cell .cell,
  th.el-table__cell .cell {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    display: flex;
    align-items: center;
    font-size: 10px !important;
  }
  td.el-table__cell.is-center .cell {
    justify-content: center;
  }
}

:deep(.el-table .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.el-table__empty-block) {
  background-color: #111827;
}

:deep(.el-loading-mask) {
  background-color: rgba(10, 14, 23, 0.7);
}
</style>
