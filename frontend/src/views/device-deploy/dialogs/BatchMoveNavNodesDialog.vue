<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="520px"
    append-to-body
    class="dark-dialog"
  >
    <el-form label-width="140px" label-position="right">
      <el-form-item :label="selectedLabel">
        <div class="selected-tags-container">
          <el-tag
            v-for="node in nodesToMove"
            :key="node.nodeId"
            size="small"
            type="info"
            effect="dark"
            class="selected-group-tag"
          >
            {{ node.nodeName }}
          </el-tag>
        </div>
      </el-form-item>

      <el-form-item :label="targetParentLabel" required>
        <el-cascader
          v-model="targetParentId"
          :options="targetCascaderOptions"
          :props="cascaderProps"
          :placeholder="targetPlaceholder"
          popper-class="dark-cascader-dropdown"
          style="width: 100%"
        />
        <div class="form-tip">
          {{ formTip }}
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button class="btn-secondary" @click="dialogVisible = false">Cancel</el-button>
      <el-button
        type="primary"
        class="btn-primary"
        :loading="submitting"
        :disabled="!targetParentId"
        @click="submitMove"
      >
        Confirm
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardNavApi } from '@/api/dashboardNav'
import { useDashboardStore } from '@/stores/dashboard'
import type { NavTreeNode } from '@/api/types'

const emit = defineEmits<{
  (e: 'moved', count: number): void
}>()

const store = useDashboardStore()

const dialogVisible = ref(false)
const submitting = ref(false)
const nodesToMove = ref<NavTreeNode[]>([])
const currentParentId = ref<number | null>(null)
const targetParentId = ref<number | null>(null)

const cascaderProps = {
  value: 'nodeId',
  label: 'nodeName',
  children: 'children',
  checkStrictly: true,
  emitPath: false,
}

const movingNodeType = computed<'cabin' | 'collector' | 'group'>(() => {
  if (!nodesToMove.value.length) return 'group'
  if (nodesToMove.value.every(n => n.nodeLevel === 2)) return 'cabin'
  if (nodesToMove.value.every(n => n.nodeLevel === 3)) return 'collector'
  return 'group'
})

const dialogTitle = computed(() => {
  if (movingNodeType.value === 'cabin') return 'Batch Move Cabins'
  if (movingNodeType.value === 'collector') return 'Batch Move Collectors'
  return 'Batch Move Groups'
})

const selectedLabel = computed(() => {
  if (movingNodeType.value === 'cabin') return 'Selected Cabins'
  if (movingNodeType.value === 'collector') return 'Selected Collectors'
  return 'Selected Groups'
})

const targetParentLabel = computed(() => {
  if (movingNodeType.value === 'cabin') return 'Target Building / System'
  if (movingNodeType.value === 'collector') return 'Target Cabin / Collector'
  return 'Target Parent'
})

const targetPlaceholder = computed(() => {
  if (movingNodeType.value === 'cabin') return 'Select target building/system (Level 1)'
  if (movingNodeType.value === 'collector') return 'Select target cabin or collector'
  return 'Select target parent group'
})

const formTip = computed(() => {
  if (movingNodeType.value === 'cabin') return 'Cabins can only be moved to a building/system root node (Level 1)'
  if (movingNodeType.value === 'collector') return 'Collectors can be moved to a cabin or merged into another collector'
  return 'Select an eligible group as the new target parent'
})

function getSubtreeDepth(node: NavTreeNode): number {
  if (!node.children || node.children.length === 0) {
    return 0
  }
  return 1 + Math.max(...node.children.map(getSubtreeDepth))
}

const targetCascaderOptions = computed(() => {
  if (!nodesToMove.value.length) return []

  const movingIds = new Set(nodesToMove.value.map(n => n.nodeId))
  const maxSubtreeDepth = Math.max(...nodesToMove.value.map(getSubtreeDepth), 0)
  const isMovingCabins = movingNodeType.value === 'cabin'

  // If moving cabins (Level 2), target MUST be a Level 1 root node (Building / System)
  if (isMovingCabins) {
    const res: any[] = []
    for (const n of store.navTree || []) {
      if (movingIds.has(n.nodeId)) continue
      if (n.nodeLevel === 1) {
        const isCurrentParent = currentParentId.value !== null && n.nodeId === currentParentId.value
        res.push({
          nodeId: n.nodeId,
          nodeName: n.nodeName,
          nodeLevel: n.nodeLevel,
          disabled: isCurrentParent,
        })
      }
    }
    return res
  }

  // Otherwise (moving collectors or generic groups):
  const buildOptions = (nodes: NavTreeNode[], parentMaxLevel?: number): any[] => {
    const res: any[] = []
    for (const n of nodes) {
      // Exclude selected nodes and their entire subtrees (anti-cycle)
      if (movingIds.has(n.nodeId)) {
        continue
      }

      const maxL = n.maxLevel ?? parentMaxLevel ?? 3
      const isCurrentParent = currentParentId.value !== null && n.nodeId === currentParentId.value
      const isLeaf = !n.children || n.children.length === 0

      // Leaf nodes (like MVP_B1 at level 3, or nodes with devices) can accept device merges
      if (isLeaf) {
        if (n.nodeLevel > maxL) {
          continue
        }
        const label = n.deviceCount != null ? `${n.nodeName} (${n.deviceCount})` : n.nodeName
        res.push({
          nodeId: n.nodeId,
          nodeName: label,
          nodeLevel: n.nodeLevel,
          disabled: isCurrentParent,
        })
        continue
      }

      // Non-leaf directory folder node:
      // Check if this folder itself can accept child folders without exceeding maxL
      const canFolderAcceptSubtree = n.nodeLevel + 1 + maxSubtreeDepth <= maxL
      const validChildren = buildOptions(n.children, maxL)

      // If this folder cannot accept child folders AND has no selectable descendants, prune it
      if (!canFolderAcceptSubtree && validChildren.length === 0) {
        continue
      }

      const item: any = {
        nodeId: n.nodeId,
        nodeName: n.nodeName,
        nodeLevel: n.nodeLevel,
        disabled: isCurrentParent || !canFolderAcceptSubtree,
      }
      if (validChildren.length > 0) {
        item.children = validChildren
      }
      res.push(item)
    }
    return res
  }

  return buildOptions(store.navTree || [])
})

function open(nodes: NavTreeNode[], parentId: number | null = null) {
  nodesToMove.value = nodes
  currentParentId.value = parentId
  targetParentId.value = null
  dialogVisible.value = true
}

async function submitMove() {
  if (!targetParentId.value) {
    ElMessage.warning('Please select a target parent')
    return
  }
  if (!nodesToMove.value.length) {
    dialogVisible.value = false
    return
  }

  submitting.value = true
  try {
    const nodeIds = nodesToMove.value.map(n => n.nodeId)
    const count = await dashboardNavApi.batchMoveNodes({
      nodeIds,
      targetParentId: targetParentId.value,
    })
    const typeName = movingNodeType.value === 'cabin'
      ? 'cabin'
      : movingNodeType.value === 'collector'
        ? 'collector'
        : 'group'
    ElMessage.success(`Successfully moved ${count} ${typeName}(s)`)
    dialogVisible.value = false
    emit('moved', count)
  } catch (e: any) {
    console.error('Failed to batch move nodes', e)
    ElMessage.error(e?.message || 'Failed to move')
  } finally {
    submitting.value = false
  }
}

defineExpose({
  open,
})
</script>

<style scoped>
.selected-tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 120px;
  overflow-y: auto;
  padding: 4px;
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid #334155;
  border-radius: 4px;
  width: 100%;
}

.selected-group-tag {
  font-family: monospace;
}

.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
