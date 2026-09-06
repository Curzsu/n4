<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`Device Alarm Rules Configuration - ${currentTemplate?.deviceTmplName || 'Device Template'}`"
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
          placeholder="Quick search point name / rule name..."
          clearable
          size="small"
          :prefix-icon="Search"
          style="width: 280px;"
        />
        <div class="point-count-badge">
          <span>Total: <strong>{{ points.length }}</strong> points</span>
          <span v-if="dirtyCount > 0" class="dirty-badge">({{ dirtyCount }} modified)</span>
        </div>
      </div>

      <!-- Point Alarm Rules Table -->
      <el-table
        :data="filteredPoints"
        size="small"
        border
        class="custom-table simple-point-table"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        max-height="480"
      >
        <el-table-column prop="pointName" label="Point Name" width="160" align="left" header-align="center" show-overflow-tooltip :resizable="false">
          <template #default="{ row }">
            <span class="point-name">{{ row.pointName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="type" label="Type" width="90" align="left" header-align="center" :resizable="false">
          <template #default="{ row }">
            <span :class="['type-tag', row.type === 'enum' ? 'tag-enum' : 'tag-numeric']">
              {{ row.type || 'numeric' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="Bind Alarm Rule" min-width="240" align="left" header-align="center" :resizable="false">
          <template #default="{ row }">
            <el-select
              v-model="row.alarmCode"
              placeholder="No Alarm Rule"
              size="small"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option label="-- No Alarm Rule --" :value="''" />
              <template v-if="row.type === 'enum'">
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
        <el-button class="btn-secondary" size="small" @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" class="btn-primary" size="small" :loading="saving" :icon="Check" @click="handleSave">
          Save Configuration
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, type CSSProperties } from 'vue'
import { Search, Check } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { templateApi } from '@/api/template'
import { alarmApi } from '@/api/alarm'
import type { DeviceTemplate, PointTemplateItem, NumericRuleItem, EnumRuleItem } from '@/api/types'

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const headerCellStyle: CSSProperties = {
  backgroundColor: '#0d1117',
  color: '#e5e7eb',
  fontSize: '10px',
  fontWeight: '600',
  borderBottom: '1px solid #2d3748',
  textAlign: 'center',
}

const cellStyle: CSSProperties = {
  backgroundColor: '#111827',
  color: '#e5e7eb',
  fontSize: '10px',
  borderBottom: '1px solid #1f2937',
  textAlign: 'left',
}

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
    ElMessage.error(err.message || 'Failed to load points')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!currentTemplate.value?.deviceTmplId) {
    ElMessage.error('Invalid device template')
    return
  }

  const modifiedCount = points.value.filter(p => {
    if (!p.pointTmplId) return false
    const orig = originalMap.value.get(p.pointTmplId) || ''
    const curr = p.alarmCode || ''
    return orig !== curr
  }).length

  if (modifiedCount === 0) {
    ElMessage.info('No rules modified')
    dialogVisible.value = false
    return
  }

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to save alarm rule bindings for ${modifiedCount} point(s)?`,
      'Confirm Save',
      {
        confirmButtonText: 'Save',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
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
      ElMessage.success(`Saved successfully (${promises.length} rule(s) updated)`)
    }

    dialogVisible.value = false
    emit('saved')
  } catch (err: any) {
    ElMessage.error(err.message || 'Save failed')
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
    padding: 12px 16px;
    background-color: $bg-dark;
    color: $text-primary;
  }
}

.simple-alarm-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;

  .point-count-badge {
    font-size: 10px;
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
    font-size: 10px;
  }

  .type-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0 6px;
    height: 16px;
    border-radius: 8px;
    font-size: 10px;
    font-weight: 400;
    line-height: 16px;
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
  gap: 8px;
}

// Button styling lives in styles/global.scss (app-wide unified look).
</style>
