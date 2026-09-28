<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="520px"
    append-to-body
    class="dark-dialog"
  >
    <!-- Result view: shown after Confirm succeeds -->
    <div v-if="moveResult" class="move-summary-panel">
      <div class="move-summary-title">Move Summary</div>
      <div class="move-summary-metrics" :class="{ 'has-subgroups': moveResult.subGroups > 0 }">
        <div class="summary-metric">
          <span class="summary-metric-label">Items</span>
          <span class="summary-metric-value">{{ moveResult.itemCount }} {{ moveResult.itemType }}</span>
        </div>
        <div class="summary-metric">
          <span class="summary-metric-label">Devices</span>
          <span class="summary-metric-value">{{ moveResult.devices }}</span>
        </div>
        <div class="summary-metric" v-if="moveResult.subGroups > 0">
          <span class="summary-metric-label">Sub-groups</span>
          <span class="summary-metric-value">{{ moveResult.subGroups }}</span>
        </div>
      </div>

      <div class="move-path-title">Move Path</div>
      <div class="summary-route">
        <div class="summary-route-card summary-route-from">
          <span class="summary-route-label">From</span>
          <span class="summary-route-value mono">{{ moveResult.from }}</span>
        </div>
        <span class="summary-route-arrow" aria-hidden="true">→</span>
        <div class="summary-route-card summary-route-to">
          <span class="summary-route-label">To</span>
          <span class="summary-route-value mono">{{ moveResult.to }}</span>
        </div>
      </div>

      <div class="move-summary-note" v-if="moveResult.devices > 0">
        Devices keep their data and points; their system paths (N4 topics) will follow the new hierarchy after the move.
      </div>
    </div>

    <!-- Selection form -->
    <el-form v-else label-width="140px" label-position="right">
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
      <template v-if="moveResult">
        <el-button class="btn-secondary" @click="moveResult = null">Cancel</el-button>
        <el-button
          type="primary"
          class="btn-primary"
          :loading="submitting"
          @click="confirmMove"
        >
          Confirm
        </el-button>
      </template>
      <template v-else>
        <el-button class="btn-secondary" @click="dialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          class="btn-primary"
          :disabled="!targetParentId"
          @click="showSummary"
        >
          Confirm
        </el-button>
      </template>
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

interface MoveResultSnapshot {
  itemCount: number
  itemType: string
  devices: number
  subGroups: number
  from: string
  to: string
}
const moveResult = ref<MoveResultSnapshot | null>(null)

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
  if (movingNodeType.value === 'collector') return 'Target Cabin'
  return 'Target Parent'
})

const targetPlaceholder = computed(() => {
  if (movingNodeType.value === 'cabin') return 'Select target building/system (Level 1)'
  if (movingNodeType.value === 'collector') return 'Select target cabin (Level 2)'
  return 'Select target parent group'
})

const formTip = computed(() => {
  if (movingNodeType.value === 'cabin') return 'Cabins can only be moved to a building/system root node (Level 1)'
  if (movingNodeType.value === 'collector') return 'Collectors can only be moved to a cabin (Level 2)'
  return 'Select an eligible group as the new target parent'
})

const itemTypeLabel = computed(() => {
  if (movingNodeType.value === 'cabin') return 'cabin(s)'
  if (movingNodeType.value === 'collector') return 'collector(s)'
  return 'group(s)'
})

function countSubtreeDevices(node: NavTreeNode): number {
  if (!node.children || node.children.length === 0) {
    return node.deviceCount ?? 0
  }
  return node.children.reduce((sum, child) => sum + countSubtreeDevices(child), 0)
}

function countSubNodes(node: NavTreeNode): number {
  if (!node.children || node.children.length === 0) return 0
  return node.children.reduce((sum, child) => sum + 1 + countSubNodes(child), 0)
}

const totalDevices = computed(() => nodesToMove.value.reduce((sum, n) => sum + countSubtreeDevices(n), 0))
const totalSubNodes = computed(() => nodesToMove.value.reduce((sum, n) => sum + countSubNodes(n), 0))

function findNodeName(nodes: { nodeId: number; nodeName: string; children?: any[] }[], nodeId: number | null): string {
  if (nodeId == null) return ''
  for (const n of nodes) {
    if (n.nodeId === nodeId) return n.nodeName
    if (n.children && n.children.length) {
      const hit = findNodeName(n.children, nodeId)
      if (hit) return hit
    }
  }
  return ''
}

const sourceName = computed(() => findNodeName(store.navTree || [], currentParentId.value) || '-')
const targetName = computed(() => (targetParentId.value ? findNodeName(targetCascaderOptions.value, targetParentId.value) : ''))

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
  const isMovingCollectors = movingNodeType.value === 'collector'

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

  // If moving collectors (Level 3), target MUST be a Level 2 Cabin (Level 3 nodes are completely omitted)
  if (isMovingCollectors) {
    const res: any[] = []
    for (const rootNode of store.navTree || []) {
      if (movingIds.has(rootNode.nodeId)) continue
      const cabinChildren: any[] = []
      for (const cabin of rootNode.children || []) {
        if (movingIds.has(cabin.nodeId)) continue
        if (cabin.nodeLevel === 2) {
          const isCurrentParent = currentParentId.value !== null && cabin.nodeId === currentParentId.value
          cabinChildren.push({
            nodeId: cabin.nodeId,
            nodeName: cabin.nodeName,
            nodeLevel: cabin.nodeLevel,
            disabled: isCurrentParent,
          })
        }
      }
      if (cabinChildren.length > 0) {
        res.push({
          nodeId: rootNode.nodeId,
          nodeName: rootNode.nodeName,
          nodeLevel: rootNode.nodeLevel,
          disabled: true,
          children: cabinChildren,
        })
      }
    }
    return res
  }

  // Otherwise (moving generic groups):
  const buildOptions = (nodes: NavTreeNode[], parentMaxLevel?: number): any[] => {
    const res: any[] = []
    for (const n of nodes) {
      // Exclude selected nodes and their entire subtrees (anti-cycle)
      if (movingIds.has(n.nodeId)) {
        continue
      }

      const maxL = n.maxLevel ?? parentMaxLevel ?? 3
      const isCurrentParent = currentParentId.value !== null && n.nodeId === currentParentId.value

      // Non-leaf directory folder node:
      // Check if this folder itself can accept child folders without exceeding maxL
      const canFolderAcceptSubtree = n.nodeLevel + 1 + maxSubtreeDepth <= maxL
      const validChildren = n.children && n.children.length > 0 ? buildOptions(n.children, maxL) : []

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
  moveResult.value = null
  dialogVisible.value = true
}

/** First Confirm: freeze the pre-move summary and switch to the review view */
function showSummary() {
  if (!targetParentId.value) {
    ElMessage.warning('Please select a target parent')
    return
  }
  if (!nodesToMove.value.length) {
    dialogVisible.value = false
    return
  }
  moveResult.value = {
    itemCount: nodesToMove.value.length,
    itemType: itemTypeLabel.value,
    devices: totalDevices.value,
    subGroups: totalSubNodes.value,
    from: sourceName.value,
    to: targetName.value,
  }
}

/** Second Confirm: actually execute the move */
async function confirmMove() {
  if (!moveResult.value || !targetParentId.value) {
    return
  }
  submitting.value = true
  try {
    const nodeIds = nodesToMove.value.map(n => n.nodeId)
    const count = await dashboardNavApi.batchMoveNodes({
      nodeIds,
      targetParentId: targetParentId.value,
    })
    ElMessage.success(`Successfully moved ${count} ${moveResult.value.itemType}`)
    dialogVisible.value = false
    moveResult.value = null
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

.move-summary-panel {
  margin-top: 4px;
}

.move-summary-title {
  margin-bottom: 8px;
  color: #cbd5e1;
  font-size: 13px;
  font-weight: 600;
}

.move-summary-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #1f2937;
  background-color: #111827;
}

.move-summary-metrics.has-subgroups {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-metric {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 9px 10px;
  border-right: 1px solid #1f2937;
}

.summary-metric:last-child {
  border-right: 0;
}

.summary-metric-label {
  margin: -9px -10px 1px;
  padding: 4px 10px;
  border-bottom: 1px solid #1f2937;
  background-color: #1b2533;
  color: #9ca3af;
  font-size: 10px;
  font-weight: 600;
  text-align: center;
}

.summary-metric-value {
  overflow: hidden;
  color: #e5e7eb;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.move-path-title {
  margin: 14px 0 8px;
  color: #cbd5e1;
  font-size: 13px;
  font-weight: 600;
}

.summary-route {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 24px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
}

.summary-route-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  min-height: 58px;
  padding: 9px 10px;
  border: 1px solid #1f2937;
  border-radius: 6px;
  background-color: #111827;
}

.summary-route-label {
  color: #64748b;
  font-size: 10px;
}

.summary-route-value {
  overflow: hidden;
  color: #e5e7eb;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-route-value.mono {
  font-family: monospace;
}

.summary-route-arrow {
  color: #64748b;
  font-size: 16px;
  text-align: center;
}

.move-summary-note {
  margin-top: 10px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.5;
}

@media (max-width: 480px) {
  .move-summary-metrics.has-subgroups {
    grid-template-columns: 1fr;
  }

  .summary-metric {
    border-right: 0;
    border-bottom: 1px solid #1f2937;
  }

  .summary-metric:last-child {
    border-bottom: 0;
  }

  .summary-route {
    grid-template-columns: minmax(0, 1fr) 18px minmax(0, 1fr);
    gap: 4px;
  }

  .summary-route-card {
    padding: 8px;
  }
}
</style>
