<template>
  <el-dialog
    v-model="dialogVisible"
    title="Batch Move Groups"
    width="520px"
    append-to-body
    class="dark-dialog"
  >
    <el-form label-width="110px" label-position="right">
      <el-form-item label="Selected Groups">
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

      <el-form-item label="Target Parent" required>
        <el-cascader
          v-model="targetParentId"
          :options="targetCascaderOptions"
          :props="cascaderProps"
          placeholder="Select target parent group"
          popper-class="dark-cascader-dropdown"
          style="width: 100%"
        />
        <div class="form-tip">
          Select an eligible group as the new target parent
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

  const buildOptions = (nodes: NavTreeNode[], parentMaxLevel?: number): any[] => {
    const res: any[] = []
    for (const n of nodes) {
      // Exclude selected nodes and their entire subtrees (anti-cycle)
      if (movingIds.has(n.nodeId)) {
        continue
      }

      const maxL = n.maxLevel ?? parentMaxLevel ?? 3

      // If this node cannot be a parent because its level is already at or above maxL,
      // or moving the subtree under it would exceed maxL, prune it completely.
      const wouldOverflow = n.nodeLevel >= maxL || (n.nodeLevel + 1 + maxSubtreeDepth > maxL)
      if (wouldOverflow) {
        continue
      }

      const isCurrentParent = currentParentId.value !== null && n.nodeId === currentParentId.value

      const item: any = {
        nodeId: n.nodeId,
        nodeName: n.nodeName,
        nodeLevel: n.nodeLevel,
        disabled: isCurrentParent,
      }

      if (n.children && n.children.length > 0) {
        const validChildren = buildOptions(n.children, maxL)
        if (validChildren.length > 0) {
          item.children = validChildren
        }
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
    ElMessage.warning('Please select a target parent group')
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
    ElMessage.success(`Successfully moved ${count} group(s)`)
    dialogVisible.value = false
    emit('moved', count)
  } catch (e: any) {
    console.error('Failed to batch move nodes', e)
    ElMessage.error(e?.message || 'Failed to move groups')
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
