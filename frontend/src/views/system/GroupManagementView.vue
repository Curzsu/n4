<template>
  <div class="group-management-view">
    <!-- Header -->
    <div class="config-header">
      <span class="config-title">Role Management</span>
      <div class="header-actions">
        <el-button v-permission="'sys:group:add'" size="small" class="btn-primary" :icon="Plus" @click="openCreate">
          Add Role
        </el-button>
        <el-button size="small" class="btn-secondary" :icon="Refresh" @click="loadGroups">
          Refresh
        </el-button>
      </div>
    </div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">Role Name:</span>
        <el-input
          v-model="query.groupName"
          size="small"
          placeholder="Filter role name"
          clearable
          style="width: 150px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">Role Code:</span>
        <el-input
          v-model="query.groupCode"
          size="small"
          placeholder="Filter code"
          clearable
          style="width: 150px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-actions">
        <el-button size="small" class="btn-primary" @click="handleSearch">Search</el-button>
        <el-button size="small" class="btn-secondary" @click="handleReset">Reset</el-button>
      </div>
    </div>

    <!-- Table -->
    <div class="table-wrapper">
      <el-table
        :data="filteredGroups"
        height="100%"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        size="small"
        border
        class="custom-table"
        v-loading="loading"
      >
        <el-table-column prop="groupId" label="ID" width="55" align="left" header-align="center" :resizable="false" />
        <el-table-column prop="groupName" label="Role Name" width="160" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
        <el-table-column prop="groupCode" label="Role Code" width="160" align="left" header-align="center" show-overflow-tooltip :resizable="false">
          <template #default="{ row }">
            <span class="code-badge">{{ row.groupCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="Description" min-width="220" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
        <el-table-column label="Status" width="85" align="left" header-align="center" :resizable="false">
          <template #default="{ row }">
            <span :class="['status-badge', row.status === 1 ? 'active' : 'disabled']">
              {{ row.status === 1 ? 'Active' : 'Disabled' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="Created At" width="160" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
        <el-table-column label="Actions" width="180" align="center" header-align="center" :resizable="false">
          <template #default="{ row }">
            <div class="action-btn-grid">
              <div class="action-col">
                <el-button v-permission="'sys:group:edit'" type="primary" link size="small" @click="openEdit(row as Group)">
                  Edit
                </el-button>
              </div>
              <div class="action-col">
                <el-button v-permission="'sys:group:edit'" type="primary" link size="small" @click="openPermissions(row as Group)">
                  Permissions
                </el-button>
              </div>
              <div class="action-col">
                <el-button
                  v-if="row.groupId !== 1"
                  v-permission="'sys:group:delete'"
                  type="danger"
                  link
                  size="small"
                  @click="removeGroup(row as Group)"
                >
                  Delete
                </el-button>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="formVisible"
      :title="editingGroup ? 'Edit Role' : 'Add Role'"
      width="520px"
      class="dark-dialog"
      :close-on-click-modal="false"
    >
      <el-form :model="form" size="small" label-width="110px" label-position="right">
        <el-form-item label="Role Name" required>
          <el-input v-model="form.groupName" placeholder="e.g. Operator" />
        </el-form-item>
        <el-form-item label="Role Code" required>
          <el-input v-model="form.groupCode" placeholder="e.g. OPERATOR" :disabled="editingGroup?.groupId === 1" />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="Role description" />
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="Active" inactive-text="Disabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" class="btn-secondary" @click="formVisible = false">Cancel</el-button>
        <el-button size="small" class="btn-primary" :loading="saving" @click="saveGroup">Save</el-button>
      </template>
    </el-dialog>

    <!-- Assign Permissions Dialog -->
    <el-dialog
      v-model="permissionVisible"
      :title="`Assign Permissions - ${currentGroup?.groupName || ''}`"
      width="540px"
      class="dark-dialog"
      :close-on-click-modal="false"
    >
      <div class="perm-dialog-body">
        <div class="tree-tools">
          <el-button size="small" link type="primary" @click="expandAll(true)">Expand All</el-button>
          <el-button size="small" link type="primary" @click="expandAll(false)">Collapse All</el-button>
          <el-button size="small" link type="primary" @click="checkAll(true)">Select All</el-button>
          <el-button size="small" link type="primary" @click="checkAll(false)">Deselect All</el-button>
        </div>
        <div class="tree-container">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            show-checkbox
            node-key="permId"
            default-expand-all
            :props="treeProps"
          >
            <template #default="{ data }">
              <span class="tree-node-item">
                <span class="node-label">{{ data.permName }}</span>
                <span :class="['type-badge', getPermTypeClass(data.permType)]">{{ getPermTypeLabel(data.permType) }}</span>
                <span class="node-code">({{ data.permCode }})</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>
      <template #footer>
        <el-button size="small" class="btn-secondary" @click="permissionVisible = false">Cancel</el-button>
        <el-button size="small" class="btn-primary" :loading="saving" @click="savePermissions">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { groupApi } from '@/api/group'
import { permissionApi } from '@/api/permission'
import type { Group, GroupForm, PermissionNode } from '@/api/types'

const loading = ref(false)
const saving = ref(false)
const groups = ref<Group[]>([])
const permissionTree = ref<PermissionNode[]>([])
const formVisible = ref(false)
const permissionVisible = ref(false)
const editingGroup = ref<Group | null>(null)
const currentGroup = ref<Group | null>(null)
const permissionTreeRef = ref<InstanceType<typeof ElTree>>()
const treeProps = { children: 'children', label: 'permName' }

const query = reactive({ groupName: '', groupCode: '' })
const form = reactive<GroupForm>({ groupName: '', groupCode: '', description: '', status: 1 })

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

const filteredGroups = computed(() => {
  return groups.value.filter((g) => {
    const matchName = !query.groupName || g.groupName.toLowerCase().includes(query.groupName.toLowerCase())
    const matchCode = !query.groupCode || g.groupCode.toLowerCase().includes(query.groupCode.toLowerCase())
    return matchName && matchCode
  })
})

async function loadGroups() {
  loading.value = true
  try {
    groups.value = await groupApi.list()
  } catch (e) {
    console.error('Failed to load groups:', e)
    ElMessage.error('Failed to load roles')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // filteredGroups updates reactively
}

function handleReset() {
  query.groupName = ''
  query.groupCode = ''
}

function openCreate() {
  editingGroup.value = null
  Object.assign(form, { groupName: '', groupCode: '', description: '', status: 1 })
  formVisible.value = true
}

function openEdit(row: Group) {
  editingGroup.value = row
  Object.assign(form, row)
  formVisible.value = true
}

async function saveGroup() {
  if (!form.groupName?.trim()) {
    ElMessage.warning('Role Name is required')
    return
  }
  if (!form.groupCode?.trim()) {
    ElMessage.warning('Role Code is required')
    return
  }
  saving.value = true
  try {
    if (editingGroup.value) {
      await groupApi.update(editingGroup.value.groupId, form)
    } else {
      await groupApi.create(form)
    }
    ElMessage.success('Saved successfully')
    formVisible.value = false
    loadGroups()
  } catch (e) {
    console.error('Failed to save group:', e)
    ElMessage.error('Failed to save role')
  } finally {
    saving.value = false
  }
}

async function openPermissions(row: Group) {
  currentGroup.value = row
  try {
    permissionTree.value = await permissionApi.tree()
    permissionVisible.value = true
    const ids = await groupApi.permissionIds(row.groupId)
    setTimeout(() => {
      permissionTreeRef.value?.setCheckedKeys(ids)
    }, 50)
  } catch (e) {
    console.error('Failed to load permissions:', e)
    ElMessage.error('Failed to load permissions')
  }
}

async function savePermissions() {
  if (!currentGroup.value) return
  saving.value = true
  try {
    const checkedKeys = (permissionTreeRef.value?.getCheckedKeys(false) || []) as number[]
    const halfCheckedKeys = (permissionTreeRef.value?.getHalfCheckedKeys() || []) as number[]
    const allIds = Array.from(new Set([...checkedKeys, ...halfCheckedKeys]))
    await groupApi.assignPermissions(currentGroup.value.groupId, allIds)
    ElMessage.success('Permissions assigned successfully')
    permissionVisible.value = false
  } catch (e) {
    console.error('Failed to assign permissions:', e)
    ElMessage.error('Failed to assign permissions')
  } finally {
    saving.value = false
  }
}

async function removeGroup(row: Group) {
  try {
    await ElMessageBox.confirm(`Are you sure you want to delete role "${row.groupName}"?`, 'Confirm Delete', {
      type: 'warning',
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
    })
    await groupApi.delete(row.groupId)
    ElMessage.success('Deleted successfully')
    loadGroups()
  } catch {}
}

function expandAll(expand: boolean) {
  const nodes = permissionTreeRef.value?.store.nodesMap
  if (nodes) {
    for (const key in nodes) {
      nodes[key].expanded = expand
    }
  }
}

function checkAll(check: boolean) {
  if (check) {
    const allKeys = getAllPermIds(permissionTree.value)
    permissionTreeRef.value?.setCheckedKeys(allKeys)
  } else {
    permissionTreeRef.value?.setCheckedKeys([])
  }
}

function getAllPermIds(nodes: PermissionNode[]): number[] {
  const ids: number[] = []
  for (const n of nodes) {
    ids.push(n.permId)
    if (n.children && n.children.length > 0) {
      ids.push(...getAllPermIds(n.children))
    }
  }
  return ids
}

function getPermTypeLabel(type: number) {
  return type === 1 ? 'Menu' : type === 2 ? 'Button' : 'API'
}

function getPermTypeClass(type: number) {
  return type === 1 ? 'menu' : type === 2 ? 'button' : 'api'
}

onMounted(() => {
  loadGroups()
})
</script>

<style scoped lang="scss">
.group-management-view {
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

  :deep(.el-input__wrapper) {
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

  :deep(.el-input__inner) {
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

/* ========================================================== */
/* ABSOLUTE CSS GRID ACTION BUTTON MATRIX                     */
/* ========================================================== */
.action-btn-grid {
  display: grid;
  grid-template-columns: 28px 68px 36px;
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
.code-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  line-height: 16px;
  font-size: 10px;
  font-family: monospace;
  font-weight: 400;
  border-radius: 8px;
  border: none !important;
  background-color: rgba(100, 116, 139, 0.14) !important;
  color: #94a3b8 !important;
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

.type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 14px;
  line-height: 14px;
  font-size: 9px;
  font-weight: 400;
  border-radius: 7px;
  border: none !important;
  margin-left: 6px;
  &.menu { background-color: rgba($accent-blue, 0.14) !important; color: #7dd3fc !important; }
  &.button { background-color: rgba($accent-yellow, 0.14) !important; color: #facc15 !important; }
  &.api { background-color: rgba(168, 85, 247, 0.14) !important; color: #c084fc !important; }
}

.perm-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tree-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid #1f2937;
}

.tree-container {
  max-height: 380px;
  overflow-y: auto;
  padding: 6px;
  background-color: #0b1119;
  border: 1px solid #1e293b;
  border-radius: 4px;
}

.tree-node-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  .node-label {
    color: $text-primary;
  }
  .node-code {
    color: $text-muted;
    font-size: 10px;
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

// Dark tree inside dialog
:deep(.el-tree) {
  background-color: transparent;
  color: $text-primary;
  --el-tree-node-hover-bg-color: #1a2332;

  .el-tree-node__content {
    height: 24px;
    &:hover {
      background-color: #1a2332;
    }
  }
}

// Dark table overrides (matching AlarmConfigView)
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
  th.el-table__cell .cell {
    justify-content: center !important;
    text-align: center !important;
  }
  td.el-table__cell .cell {
    justify-content: flex-start !important;
    text-align: left !important;
  }
  td.el-table__cell.is-center .cell {
    justify-content: center !important;
    text-align: center !important;
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
