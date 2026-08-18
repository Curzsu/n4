<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`设备报警规则配置 - ${currentTemplate?.deviceTmplName || '设备模板'}`"
    width="720px"
    top="6vh"
    append-to-body
    class="dark-dialog simple-alarm-dialog"
    destroy-on-close
  >
    <div class="simple-alarm-container" v-loading="loading">
      <!-- Search & Summary Bar -->
      <div class="top-bar">
        <el-input
          v-model="searchQuery"
          placeholder="快速搜索点位名称 / 规则名称..."
          clearable
          size="default"
          :prefix-icon="Search"
          style="width: 320px;"
        />
        <div class="point-count-badge">
          <span>共 <strong>{{ points.length }}</strong> 个点位</span>
          <span v-if="dirtyCount > 0" class="dirty-badge">（已修改 {{ dirtyCount }} 项）</span>
        </div>
      </div>

      <!-- Point Alarm Rules Table -->
      <el-table
        :data="filteredPoints"
        size="small"
        border
        class="custom-table simple-point-table"
        max-height="480"
      >
        <el-table-column prop="pointName" label="点位名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="point-name">{{ row.pointName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="dataType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <span :class="['type-tag', row.dataType === 'enum' ? 'tag-enum' : 'tag-numeric']">
              {{ row.dataType || 'numeric' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="绑定报警规则" min-width="240">
          <template #default="{ row }">
            <el-select
              v-model="row.alarmCode"
              placeholder="无报警规则"
              size="small"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option label="-- 无报警规则 --" :value="''" />
              <template v-if="row.dataType === 'enum'">
                <el-option
                  v-for="name in allEnumRuleNames"
                  :key="name"
                  :label="name"
                  :value="name"
                />
              </template>
              <template v-else>
                <el-option
                  v-for="r in numericRules"
                  :key="r.alarmName"
                  :label="r.alarmName"
                  :value="r.alarmName"
                />
              </template>
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Footer Actions -->
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :icon="Check" @click="handleSave">
          保存配置
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { templateApi } from '@/api/template'
import { alarmApi } from '@/api/alarm'
import type { DeviceTemplate, PointTemplateItem, NumericRuleItem, EnumRuleItem } from '@/api/types'

const emit = defineEmits<{
  (e: 'saved'): void
}>()

// State
const dialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const searchQuery = ref('')

// Context
const currentTemplate = ref<DeviceTemplate | null>(null)
const points = ref<PointTemplateItem[]>([])
const originalMap = ref<Map<number, string>>(new Map())

// Rules
const numericRules = ref<NumericRuleItem[]>([])
const enumRules = ref<EnumRuleItem[]>([])

// Computed
const allEnumRuleNames = computed(() => {
  const set = new Set<string>()
  for (const r of enumRules.value) {
    if (r.alarmName) set.add(r.alarmName)
  }
  return Array.from(set).sort()
})

const dirtyCount = computed(() => {
  let count = 0
  for (const p of points.value) {
    if (!p.pointTmplId) continue
    const orig = originalMap.value.get(p.pointTmplId) || ''
    const curr = p.alarmCode || ''
    if (orig !== curr) count++
  }
  return count
})

const filteredPoints = computed(() => {
  if (!searchQuery.value) return points.value
  const q = searchQuery.value.toLowerCase().trim()
  return points.value.filter((p) => {
    const nameMatch = (p.pointName || '').toLowerCase().includes(q)
    const codeMatch = (p.alarmCode || '').toLowerCase().includes(q)
    return nameMatch || codeMatch
  })
})

// Methods
async function loadRules() {
  try {
    const [numRes, enumRes] = await Promise.all([
      alarmApi.getNumericRules(),
      alarmApi.getEnumRules()
    ])
    numericRules.value = Array.isArray(numRes) ? numRes : []
    enumRules.value = Array.isArray(enumRes) ? enumRes : []
  } catch (err) {
    console.error('Failed to load rules:', err)
  }
}

async function open(template: DeviceTemplate, targetPoint?: PointTemplateItem) {
  currentTemplate.value = { ...template }
  dialogVisible.value = true
  loading.value = true
  searchQuery.value = targetPoint?.pointName || ''

  try {
    await loadRules()

    if (template.deviceTmplId) {
      const list = await templateApi.getPoints(template.deviceTmplId)
      points.value = (list || []).map((p) => ({ ...p }))

      originalMap.value.clear()
      for (const p of points.value) {
        if (p.pointTmplId) {
          originalMap.value.set(p.pointTmplId, p.alarmCode || '')
        }
      }
    } else {
      points.value = []
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载测点失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!currentTemplate.value?.deviceTmplId) {
    ElMessage.error('设备模板无效')
    return
  }

  saving.value = true
  try {
    const promises: Promise<any>[] = []

    for (const p of points.value) {
      if (!p.pointTmplId) continue
      const orig = originalMap.value.get(p.pointTmplId) || ''
      const curr = p.alarmCode || ''
      if (orig !== curr) {
        promises.push(
          templateApi.updatePoint(p.pointTmplId, {
            alarmCode: curr
          })
        )
      }
    }

    if (promises.length > 0) {
      await Promise.all(promises)
      ElMessage.success(`保存成功（已更新 ${promises.length} 个点位规则）`)
    } else {
      ElMessage.info('未修改任何规则')
    }

    dialogVisible.value = false
    emit('saved')
  } catch (err: any) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

defineExpose({
  open
})
</script>

<style scoped lang="scss">
$bg-card: #111b27;
$bg-surface: #172334;
$bg-dark: #0b1119;
$border-color: #1e293b;
$text-primary: #f8fafc;
$text-secondary: #94a3b8;
$accent-green: #10b981;
$accent-blue: #3b82f6;
$accent-yellow: #f59e0b;

.simple-alarm-dialog {
  :deep(.el-dialog__body) {
    padding: 16px 20px;
    background-color: $bg-dark;
    color: $text-primary;
  }
}

.simple-alarm-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;

  .point-count-badge {
    font-size: 13px;
    color: $text-secondary;

    strong {
      color: $accent-blue;
    }

    .dirty-badge {
      color: $accent-yellow;
      font-weight: 500;
    }
  }
}

.simple-point-table {
  .point-name {
    font-weight: 400;
    color: $text-primary;
  }

  .type-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 3px 11px;
    border-radius: 11px;
    font-size: 12px;
    font-weight: 400;
    line-height: 1.25;
    border: none !important;

    &.tag-numeric {
      background-color: rgba(59, 130, 246, 0.20) !important;
      color: #93c5fd !important;
    }

    &.tag-enum {
      background-color: rgba(245, 158, 11, 0.20) !important;
      color: #fde047 !important;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
