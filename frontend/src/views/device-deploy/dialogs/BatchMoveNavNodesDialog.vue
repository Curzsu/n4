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
      <div class="move-summary-heading">
        <div>
          <div class="move-summary-kicker">REVIEW MOVE</div>
          <div class="move-summary-title">Move Summary</div>
        </div>
        <span class="summary-ready"><span class="summary-ready-dot"></span>Ready</span>
      </div>

      <div class="move-summary-metrics">
        <div class="summary-metric summary-metric-primary">
          <span class="summary-metric-label">Items</span>
          <span class="summary-metric-value">{{ moveResult.itemCount }}</span>
          <span class="summary-metric-caption">{{ moveResult.itemType }}</span>
        </div>
        <div class="summary-metric">
          <span class="summary-metric-label">Devices</span>
          <span class="summary-metric-value">{{ moveResult.devices }}</span>
          <span class="summary-metric-caption">in this move</span>
        </div>
        <div class="summary-metric" v-if="moveResult.subGroups > 0">
          <span class="summary-metric-label">Sub-groups</span>
          <span class="summary-metric-value">{{ moveResult.subGroups }}</span>
          <span class="summary-metric-caption">included</span>
        </div>
      </div>

      <div class="summary-route">
        <div class="summary-route-card summary-route-from">
          <span class="summary-route-label">FROM</span>
          <span class="summary-route-value mono">{{ moveResult.from }}</span>
        </div>
        <span class="summary-route-arrow" aria-hidden="true">→</span>
        <div class="summary-route-card summary-route-to">
          <span class="summary-route-label">TO</span>
          <span class="summary-route-value mono">{{ moveResult.to }}</span>
        </div>
      </div>

      <div class="move-summary-note" v-if="moveResult.devices > 0">
        <span class="move-summary-note-icon" aria-hidden="true">i</span>
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
  padding: 18px;
  background: linear-gradient(145deg, rgba(20, 34, 52, 0.96), rgba(15, 23, 42, 0.88));
  border: 1px solid #34465f;
  border-radius: 10px;
  box-shadow: inset 0 1px 0 rgba(148, 163, 184, 0.06), 0 12px 28px rgba(2, 8, 23, 0.18);
}

.move-summary-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.move-summary-kicker {
  margin-bottom: 3px;
  color: #60a5fa;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.move-summary-title {
  color: #e2e8f0;
  font-size: 16px;
  font-weight: 650;
  line-height: 1.25;
}

.summary-ready {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  border: 1px solid rgba(52, 211, 153, 0.2);
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.08);
  color: #6ee7b7;
  font-size: 11px;
  font-weight: 600;
}

.summary-ready-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.55);
}

.move-summary-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 9px;
}

.summary-metric {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 4px 8px;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid rgba(71, 85, 105, 0.65);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.48);
}

.summary-metric-primary {
  border-color: rgba(59, 130, 246, 0.36);
  background: linear-gradient(145deg, rgba(30, 64, 115, 0.36), rgba(15, 23, 42, 0.58));
}

.summary-metric-label {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 500;
}

.summary-metric-value {
  color: #e2e8f0;
  font-size: 20px;
  font-weight: 650;
  line-height: 1.15;
}

.summary-metric-caption {
  grid-column: 1 / -1;
  overflow: hidden;
  color: #64748b;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-route {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 28px minmax(0, 1fr);
  align-items: center;
  gap: 7px;
  margin-top: 12px;
}

.summary-route-card {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid rgba(71, 85, 105, 0.68);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.42);
}

.summary-route-to {
  border-color: rgba(45, 212, 191, 0.3);
  background: linear-gradient(145deg, rgba(13, 78, 79, 0.24), rgba(15, 23, 42, 0.55));
}

.summary-route-label {
  color: #64748b;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.summary-route-value {
  overflow: hidden;
  color: #cbd5e1;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-route-to .summary-route-value {
  color: #99f6e4;
}

.summary-route-value.mono {
  font-family: monospace;
}

.summary-route-arrow {
  color: #60a5fa;
  font-size: 19px;
  text-align: center;
}

.move-summary-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 11px;
  border: 1px solid rgba(71, 85, 105, 0.38);
  border-radius: 7px;
  background: rgba(30, 41, 59, 0.42);
  color: #94a3b8;
  font-size: 11px;
  line-height: 1.5;
}

.move-summary-note-icon {
  display: inline-flex;
  flex: 0 0 15px;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  margin-top: 1px;
  border-radius: 50%;
  background: rgba(96, 165, 250, 0.15);
  color: #60a5fa;
  font-size: 10px;
  font-weight: 700;
}

@media (max-width: 480px) {
  .move-summary-panel {
    padding: 14px;
  }

  .move-summary-metrics {
    gap: 6px;
  }

  .summary-metric {
    padding: 9px 8px;
  }

  .summary-route {
    grid-template-columns: minmax(0, 1fr) 20px minmax(0, 1fr);
    gap: 4px;
  }

  .summary-route-card {
    padding: 9px 8px;
  }
}
</style>
