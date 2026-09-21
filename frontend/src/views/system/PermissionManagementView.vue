<template>
  <div class="permission-management-view">
    <!-- Header -->
    <div class="config-header">
      <span class="config-title">Permissions</span>
      <div class="header-actions">
        <el-button v-permission="'sys:permission:add'" size="small" class="btn-primary" :icon="Plus" @click="openCreate()">
          Add Permission
        </el-button>
        <el-button size="small" class="btn-secondary" :icon="Refresh" @click="loadTree">
          Refresh
        </el-button>
      </div>
    </div>

    <!-- Tree Table -->
    <div class="table-wrapper">
      <el-table
        :data="permissionTree"
        row-key="permId"
        :indent="48"
        :row-class-name="permissionRowClass"
        :expand-row-keys="defaultExpandedKeys"
        height="100%"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        size="small"
        border
        class="custom-table"
        v-loading="loading"
      >
        <el-table-column prop="permName" label="Permission Name" min-width="200" align="left" header-align="center" show-overflow-tooltip :resizable="false">
          <template #default="{ row }">
            <span class="tree-perm-name">
              <el-icon v-if="isValidIcon(row.icon)" class="perm-icon"><component :is="row.icon" /></el-icon>
              <span>{{ row.permName }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="permCode" label="Permission Code" min-width="170" align="left" header-align="center" show-overflow-tooltip :resizable="false">
          <template #default="{ row }">
            <span class="code-badge">{{ row.permCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Type" width="85" align="left" header-align="center" :resizable="false">
          <template #default="{ row }">
            <span :class="['type-badge', getPermTypeClass(row.permType)]">{{ getPermTypeLabel(row.permType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="Route Path" min-width="140" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
        <el-table-column prop="component" label="Component" min-width="170" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
        <el-table-column prop="sortOrder" label="Sort" width="60" align="left" header-align="center" :resizable="false" />
        <el-table-column label="Status" width="85" align="left" header-align="center" :resizable="false">
          <template #default="{ row }">
            <span :class="['status-badge', row.status === 1 ? 'active' : 'disabled']">
              {{ row.status === 1 ? 'Active' : 'Disabled' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="160" align="center" header-align="center" :resizable="false">
          <template #default="{ row }">
            <div class="action-btn-grid">
              <div class="action-col">
                <el-button
                  v-if="row.permType === 1"
                  v-permission="'sys:permission:add'"
                  type="primary"
                  link
                  size="small"
                  @click="openCreate(row as PermissionNode)"
                >
                  Add Child
                </el-button>
              </div>
              <div class="action-col">
                <el-button
                  v-permission="'sys:permission:edit'"
                  type="primary"
                  link
                  size="small"
                  @click="openEdit(row as PermissionNode)"
                >
                  Edit
                </el-button>
              </div>
              <div class="action-col">
                <el-button
                  v-permission="'sys:permission:delete'"
                  type="danger"
                  link
                  size="small"
                  @click="removePermission(row as PermissionNode)"
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
      :title="editingPermission ? 'Edit Permission' : 'Add Permission'"
      width="560px"
      class="dark-dialog"
      :close-on-click-modal="false"
    >
      <el-form :model="form" size="small" label-width="120px" label-position="right">
        <el-form-item label="Parent Node">
          <el-input :model-value="parentDisplayName" disabled placeholder="Root (0)" />
        </el-form-item>
        <el-form-item label="Permission Name" required>
          <el-input v-model="form.permName" placeholder="e.g. User Management" />
        </el-form-item>
        <el-form-item label="Permission Code" required>
          <el-input v-model="form.permCode" placeholder="e.g. sys:user:list" />
        </el-form-item>
        <el-form-item label="Resource Type" required>
          <el-select v-model="form.permType" placeholder="Select type" popper-class="alarm-config-popper">
            <el-option label="Menu" :value="1" />
            <el-option label="Button / Action" :value="2" />
            <el-option label="API Endpoint" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.permType === 1" label="Route Path">
          <el-input v-model="form.path" placeholder="e.g. /system/users" />
        </el-form-item>
        <el-form-item v-if="form.permType === 1" label="Component">
          <el-input v-model="form.component" placeholder="e.g. system/UserManagementView" />
        </el-form-item>
        <el-form-item v-if="form.permType === 1" label="Icon">
          <el-input v-model="form.icon" placeholder="e.g. User, Setting, Lock" />
        </el-form-item>
        <el-form-item label="Sort Order">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="Active" inactive-text="Disabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" class="btn-secondary" @click="formVisible = false">Cancel</el-button>
        <el-button size="small" class="btn-primary" :loading="saving" @click="savePermission">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bell,
  Clock,
  Coin,
  Cpu,
  Document,
  EditPen,
  Key,
  List,
  Lock,
  Monitor,
  Operation,
  Plus,
  Refresh,
  Setting,
  SetUp,
  SwitchButton,
  Tickets,
  Tools,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import { permissionApi } from '@/api/permission'
import type { PermissionForm, PermissionNode } from '@/api/types'
import { buildPermissionTreeView, type PermissionViewNode } from '@/utils/permissionTree'

const knownIcons = {
  Bell,
  Clock,
  Coin,
  Cpu,
  Document,
  EditPen,
  Key,
  List,
  Lock,
  Monitor,
  Operation,
  Setting,
  SetUp,
  SwitchButton,
  Tickets,
  Tools,
  TrendCharts,
  User,
}

function isValidIcon(name?: string) {
  return Boolean(name && name in knownIcons)
}

const loading = ref(false)
const saving = ref(false)
const permissionTree = ref<PermissionViewNode[]>([])
const formVisible = ref(false)
const editingPermission = ref<PermissionNode | null>(null)
const currentParent = ref<PermissionNode | null>(null)

const form = reactive<PermissionForm>({
  permName: '',
  permCode: '',
  permType: 1,
  parentId: 0,
  path: '',
  component: '',
  icon: '',
  sortOrder: 0,
  status: 1,
})

const parentDisplayName = computed(() => {
  if (currentParent.value) {
    return `${currentParent.value.permName} (ID: ${currentParent.value.permId})`
  }
  if (form.parentId && form.parentId > 0) {
    return `Parent ID: ${form.parentId}`
  }
  return 'Root (Level 1)'
})

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

/** Row level class derived from the dotted display order (e.g. "3.1.2" -> depth 2) */
function permissionRowClass({ row }: { row: PermissionViewNode }): string {
  const depth = (row.displayOrder.match(/\./g) || []).length
  return `perm-level-${depth}`
}

/** Expand root nodes only on first load: level 1 visible, level 2+ collapsed */
const defaultExpandedKeys = computed(() => permissionTree.value.map(node => node.permId))

async function loadTree() {
  loading.value = true
  try {
    permissionTree.value = buildPermissionTreeView(await permissionApi.tree())
  } catch (e) {
    console.error('Failed to load permissions:', e)
    ElMessage.error('Failed to load permissions')
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: PermissionNode) {
  editingPermission.value = null
  currentParent.value = parent || null
  Object.assign(form, {
    permName: '',
    permCode: '',
    permType: parent ? 2 : 1,
    parentId: parent?.permId || 0,
    path: '',
    component: '',
    icon: '',
    sortOrder: 0,
    status: 1,
  })
  formVisible.value = true
}

function openEdit(row: PermissionNode) {
  editingPermission.value = row
  currentParent.value = null
  Object.assign(form, {
    permName: row.permName,
    permCode: row.permCode,
    permType: row.permType,
    parentId: row.parentId,
    path: row.path,
    component: row.component,
    icon: row.icon,
    sortOrder: row.sortOrder,
    status: row.status,
  })
  formVisible.value = true
}

async function savePermission() {
  if (!form.permName?.trim()) {
    ElMessage.warning('Permission Name is required')
    return
  }
  if (!form.permCode?.trim()) {
    ElMessage.warning('Permission Code is required')
    return
  }
  saving.value = true
  try {
    if (editingPermission.value) {
      await permissionApi.update(editingPermission.value.permId, form)
    } else {
      await permissionApi.create(form)
    }
    ElMessage.success('Saved successfully')
    formVisible.value = false
    loadTree()
  } catch (e) {
    console.error('Failed to save permission:', e)
    ElMessage.error('Failed to save permission')
  } finally {
    saving.value = false
  }
}

async function removePermission(row: PermissionNode) {
  try {
    await ElMessageBox.confirm(`Are you sure you want to delete permission "${row.permName}"?`, 'Confirm Delete', {
      type: 'warning',
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
    })
    await permissionApi.delete(row.permId)
    ElMessage.success('Deleted successfully')
    loadTree()
  } catch {}
}

function getPermTypeLabel(type: number) {
  return type === 1 ? 'Menu' : type === 2 ? 'Button' : 'API'
}

function getPermTypeClass(type: number) {
  return type === 1 ? 'menu' : type === 2 ? 'button' : 'api'
}

onMounted(() => {
  loadTree()
})
</script>

<style scoped lang="scss">
.permission-management-view {
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

.table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tree-perm-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  .perm-icon {
    font-size: 12px;
    color: $text-secondary;
  }
}

/* ========================================================== */
/* ABSOLUTE CSS GRID ACTION BUTTON MATRIX                     */
/* ========================================================== */
.action-btn-grid {
  display: grid;
  grid-template-columns: 56px 28px 36px;
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
  height: 16px;
  line-height: 16px;
  font-size: 10px;
  font-weight: 400;
  border-radius: 8px;
  border: none !important;
  transition: all 0.2s ease-in-out;

  &.menu {
    background-color: rgba($accent-blue, 0.14) !important;
    color: #7dd3fc !important;
  }
  &.button {
    background-color: rgba($accent-yellow, 0.14) !important;
    color: #facc15 !important;
  }
  &.api {
    background-color: rgba(168, 85, 247, 0.14) !important;
    color: #c084fc !important;
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

// Dark table overrides (matching AlarmTable / ProductManagerView)
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

  /* Leaf rows render a placeholder where parent rows render the expand icon;
     both must occupy the same width (20px icon + 2px margin) so rows at the
     same tree level align. */
  .el-table__placeholder {
    display: inline-block;
    width: 22px;
  }
  .el-table__expand-icon {
    color: $text-secondary;
    font-size: 11px;
    width: 20px;
    height: 20px;
    line-height: 20px;
    margin-right: 2px;
  }

  /* Level typography: directory bold/bright, menu regular, buttons muted */
  tr.perm-level-0 .tree-perm-name {
    font-weight: 600;
    color: #f1f5f9;
  }
  tr.perm-level-1 .tree-perm-name {
    color: #e5e7eb;
  }
  tr.perm-level-2 .tree-perm-name,
  tr.perm-level-3 .tree-perm-name {
    color: #94a3b8;
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
