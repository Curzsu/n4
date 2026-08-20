<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogMode === 'create' ? 'Add Navigation Node' : 'Edit Navigation Node'"
    width="480px"
    append-to-body
    class="dark-dialog"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="Node Name" prop="nodeName">
        <el-input v-model="form.nodeName" placeholder="e.g. Electrical / MV System" maxlength="50" show-word-limit />
      </el-form-item>

      <el-form-item label="Parent Node" v-if="showParentSelect">
        <el-cascader
          v-model="form.parentId"
          :options="parentCascaderOptions"
          :props="parentCascaderProps"
          placeholder="Select parent node"
          :clearable="false"
          popper-class="dark-cascader-dropdown"
          style="width: 100%"
        />
        <div class="form-tip">Select an eligible group as the target parent</div>
      </el-form-item>

      <el-form-item label="Icon" v-if="form.parentId == null">
        <el-select v-model="form.icon" placeholder="Select icon (Root node only)" clearable style="width: 100%">
          <el-option
            v-for="opt in iconOptions"
            :key="opt.name"
            :label="opt.name"
            :value="opt.name"
          >
            <span style="display: flex; align-items: center; gap: 8px;">
              <el-icon><component :is="opt.comp" /></el-icon>
              <span>{{ opt.name }}</span>
            </span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="Sort Order" prop="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" style="width: 100%" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button class="btn-secondary" @click="dialogVisible = false">Cancel</el-button>
      <el-button type="primary" class="btn-primary" @click="submitForm" :loading="submitting">Confirm</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Location, Picture, OfficeBuilding, Grid, Lightning, Warning,
  EditPen, Cpu, Monitor, Box,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { dashboardNavApi } from '@/api/dashboardNav'
import { useDashboardStore } from '@/stores/dashboard'
import type { NavTreeNode } from '@/api/types'

const emit = defineEmits<{
  (e: 'saved', node: { mode: 'create' | 'edit'; id?: number }): void
  (e: 'deleted', nodeId: number): void
}>()

const store = useDashboardStore()

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingNodeId = ref<number | null>(null)
const targetParentLevel = ref<number>(0)

const form = ref({
  nodeName: '',
  parentId: null as number | null,
  icon: null as string | null,
  sortOrder: 0,
})

const rules = {
  nodeName: [{ required: true, message: 'Please enter node name', trigger: 'blur' }],
}

const showParentSelect = computed(() => targetParentLevel.value > 0)

const iconOptions = [
  { name: 'Location', comp: Location },
  { name: 'Picture', comp: Picture },
  { name: 'OfficeBuilding', comp: OfficeBuilding },
  { name: 'Grid', comp: Grid },
  { name: 'Lightning', comp: Lightning },
  { name: 'Warning', comp: Warning },
  { name: 'EditPen', comp: EditPen },
  { name: 'Cpu', comp: Cpu },
  { name: 'Monitor', comp: Monitor },
  { name: 'Box', comp: Box },
]

const parentCascaderProps = {
  value: 'nodeId',
  label: 'nodeName',
  children: 'children',
  checkStrictly: false,
  emitPath: false,
}

const parentCascaderOptions = computed(() => {
  const targetLevel = targetParentLevel.value
  if (targetLevel === 0) return []

  const buildTree = (nodes: NavTreeNode[]): any[] => {
    const res: any[] = []
    for (const n of nodes) {
      if (n.nodeLevel > targetLevel) continue

      if (n.nodeLevel === targetLevel) {
        const maxL = n.maxLevel ?? 4
        if (n.nodeLevel >= maxL) continue

        res.push({
          nodeId: n.nodeId,
          nodeName: n.nodeName,
          nodeLevel: n.nodeLevel,
        })
      } else {
        let validChildren: any[] = []
        if (n.children && n.children.length > 0) {
          validChildren = buildTree(n.children)
        }
        if (validChildren.length > 0) {
          res.push({
            nodeId: n.nodeId,
            nodeName: n.nodeName,
            nodeLevel: n.nodeLevel,
            children: validChildren,
          })
        }
      }
    }
    return res
  }

  return buildTree(store.navTree)
})

function findParentId(nodes: NavTreeNode[], childId: number): number | null {
  for (const n of nodes) {
    if (n.children) {
      if (n.children.some(c => c.nodeId === childId)) return n.nodeId
      const found = findParentId(n.children, childId)
      if (found) return found
    }
  }
  return null
}

function openCreate(parent: NavTreeNode | null = null) {
  dialogMode.value = 'create'
  editingNodeId.value = null
  targetParentLevel.value = parent ? parent.nodeLevel : 0

  form.value = {
    nodeName: '',
    parentId: parent ? parent.nodeId : null,
    icon: parent ? null : 'OfficeBuilding',
    sortOrder: 0,
  }
  dialogVisible.value = true
}

function openEdit(node: NavTreeNode) {
  dialogMode.value = 'edit'
  editingNodeId.value = node.nodeId
  targetParentLevel.value = node.nodeLevel - 1

  form.value = {
    nodeName: node.nodeName,
    parentId: node.nodeLevel === 1 ? null : (findParentId(store.navTree, node.nodeId) ?? null),
    icon: node.icon ?? null,
    sortOrder: node.sortOrder,
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (dialogMode.value === 'create') {
        const res = await dashboardNavApi.createNode({
          nodeName: form.value.nodeName,
          parentId: form.value.parentId,
          icon: form.value.parentId == null ? form.value.icon : null,
          sortOrder: form.value.sortOrder,
        })
        ElMessage.success('Node created successfully')
        emit('saved', { mode: 'create', id: res.nodeId })
      } else {
        await dashboardNavApi.updateNode(editingNodeId.value!, {
          nodeName: form.value.nodeName,
          parentId: form.value.parentId,
          icon: form.value.parentId == null ? form.value.icon : null,
          sortOrder: form.value.sortOrder,
        })
        ElMessage.success('Node updated successfully')
        emit('saved', { mode: 'edit', id: editingNodeId.value! })
      }
      dialogVisible.value = false
      await store.loadNavTree()
    } catch (e: any) {
      ElMessage.error(e.message || 'Operation failed')
    } finally {
      submitting.value = false
    }
  })
}

async function confirmDelete(node: NavTreeNode) {
  const isLeaf = !node.children || node.children.length === 0
  try {
    const impact = await dashboardNavApi.getDeleteImpact(node.nodeId)
    const msg = !isLeaf
      ? `This will delete this node along with its ${impact.childCount} sub-nodes, and unbind ${impact.deviceCount} devices.`
      : `This will delete this node, and unbind ${impact.deviceCount} devices.`
    await ElMessageBox.confirm(
      `${msg}\nNode deletion cannot be undone. Do you want to proceed?`,
      'Delete Confirmation',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }

  try {
    await dashboardNavApi.deleteNode(node.nodeId)
    ElMessage.success('Deleted successfully')
    if (store.currentNavNode?.nodeId === node.nodeId) {
      store.currentNavNode = null
      store.currentDevices = []
    }
    await store.loadNavTree()
    emit('deleted', node.nodeId)
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to delete node')
  }
}

defineExpose({
  openCreate,
  openEdit,
  confirmDelete,
})
</script>

<style scoped lang="scss">
.form-tip {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
}
</style>
