<template>
  <div class="alarm-config-view">
    <!-- Tab bar -->
    <div class="tab-bar">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab-item', { active: activeTab === tab.key }]"
        @click="onTabChange(tab.key)"
      >
        {{ tab.label }}
      </div>
      <div class="tab-actions">
        <el-button type="primary" size="small" class="btn-primary" :icon="Plus" @click="openCreateDialog">
          Add Rule
        </el-button>
      </div>
    </div>

    <!-- Search Bar -->
    <div class="search-box-table">
      <el-input
        v-model="searchQuery"
        placeholder="Search alarm rules..."
        size="small"
        :prefix-icon="Search"
        clearable
        class="search-bar"
      />
    </div>

    <!-- Numeric table: v-if so only the active tab's table mounts (~1000 combined
         rows otherwise make the initial render take seconds in dev mode).
         Rows are windowed: only the first numericVisible rows mount; scrolling
         near the bottom appends the next batch (onTableScroll). -->
    <div v-if="activeTab === 'numeric'" :ref="setTableWrapperRef" class="config-table-wrapper" @scroll.capture="onTableScroll($event, 'numeric')">
      <el-table :data="displayedNumeric" row-key="id" :height="tableHeight" style="width: 100%"
        :header-cell-style="headerCellStyle" :cell-style="cellStyle" size="small" border v-loading="loading">
        <el-table-column prop="alarmName" label="Alarm Name" width="200" show-overflow-tooltip />
        <el-table-column prop="timeDelay" label="Time Delay(s)" width="80" align="center" />
        <el-table-column prop="deadband" label="Deadband" width="80" align="center" />
        <el-table-column label="High-High" align="center">
          <el-table-column prop="highHighValue" label="Value" width="70" />
          <el-table-column prop="highHighSeverity" label="Severity" width="80" />
          <el-table-column prop="highHighMessage" label="Message" width="120" show-overflow-tooltip />
        </el-table-column>
        <el-table-column label="High" align="center">
          <el-table-column prop="highValue" label="Value" width="70" />
          <el-table-column prop="highSeverity" label="Severity" width="80" />
          <el-table-column prop="highMessage" label="Message" width="120" show-overflow-tooltip />
        </el-table-column>
        <el-table-column label="Low" align="center">
          <el-table-column prop="lowValue" label="Value" width="70" />
          <el-table-column prop="lowSeverity" label="Severity" width="80" />
          <el-table-column prop="lowMessage" label="Message" width="120" show-overflow-tooltip />
        </el-table-column>
        <el-table-column label="Low-Low" align="center">
          <el-table-column prop="lowLowValue" label="Value" width="70" />
          <el-table-column prop="lowLowSeverity" label="Severity" width="80" />
          <el-table-column prop="lowLowMessage" label="Message" width="120" show-overflow-tooltip />
        </el-table-column>
        <el-table-column label="Actions" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">Edit</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">Delete</el-button>
          </template>
        </el-table-column>
        <template #append>
          <div class="table-window-hint">
            Showing {{ displayedNumeric.length }} of {{ filteredNumericData.length }} numeric rules
            <span v-if="displayedNumeric.length < filteredNumericData.length"> - scroll to bottom to load more</span>
          </div>
        </template>
      </el-table>
    </div>

    <!-- Enum table: v-if, same reason as the numeric table -->
    <div v-if="activeTab === 'enum'" :ref="setTableWrapperRef" class="config-table-wrapper" @scroll.capture="onTableScroll($event, 'enum')">
      <el-table :data="displayedEnum" row-key="id" :height="tableHeight" style="width: 100%"
        :header-cell-style="headerCellStyle" :cell-style="cellStyle" size="small" border v-loading="loading">
        <el-table-column prop="alarmName" label="Alarm Name" width="200" show-overflow-tooltip />
        <el-table-column prop="timeDelay" label="Time Delay(s)" width="80" align="center" />
        <el-table-column prop="enumValue" label="Enum Value" width="100" align="center" />
        <el-table-column prop="enumMeaning" label="Meaning" min-width="150" show-overflow-tooltip />
        <el-table-column prop="severity" label="Severity" width="90" />
        <el-table-column prop="message" label="Message" min-width="200" show-overflow-tooltip />
        <el-table-column label="Actions" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">Edit</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">Delete</el-button>
          </template>
        </el-table-column>
        <template #append>
          <div class="table-window-hint">
            Showing {{ displayedEnum.length }} of {{ filteredEnumData.length }} enum rules
            <span v-if="displayedEnum.length < filteredEnumData.length"> - scroll to bottom to load more</span>
          </div>
        </template>
      </el-table>
    </div>

    <!-- Create / Edit dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? 'Edit Alarm Rule' : 'Add Alarm Rule'"
      width="720px" :close-on-click-modal="false" @closed="resetForm">
      <el-form :model="formData"  size="small" label-position="right">
        <!-- Basic fields: Point Name + Point Type on the same row -->
        <div class="basic-row">
          <el-form-item v-if="isEdit" label="Alarm Name" class="basic-name">
            <el-input v-model="formData.alarmName" disabled />
          </el-form-item>
          <el-form-item v-else label="Point Name" required class="basic-name">
            <el-input v-model="formData.pointName" placeholder="e.g. Available_physical_memory" />
          </el-form-item>
          <el-form-item label="Point Type" required class="basic-type">
            <el-select v-model="formData.pointType" placeholder="Select type" style="width: 140px"
              popper-class="alarm-config-popper" :disabled="isEdit">
              <el-option label="NUMERIC" value="NUMERIC" />
              <el-option label="ENUM" value="ENUM" />
            </el-select>
          </el-form-item>
        </div>

        <!-- Placeholder before type selection -->
        <div v-if="!formData.pointType" class="type-hint">
          Please select a Point Type to configure alarm rules.
        </div>

        <!-- Numeric rules (NUMERIC only) -->
        <template v-if="formData.pointType === 'NUMERIC'">
          <!-- Per-point: Time_Delay + Deadband -->
          <div class="level-row">
            <el-form-item label="Time Delay(s)" class="field-half">
              <el-input v-model="formData.timeDelay" placeholder="0" />
            </el-form-item>
            <el-form-item label="Deadband" class="field-half">
              <el-input v-model="formData.deadband" placeholder="0" />
            </el-form-item>
          </div>

          <el-divider content-position="left">High-High</el-divider>
          <div class="level-row">
            <el-form-item label="Value" required class="field-half">
              <el-input v-model="formData.highHighValue" placeholder=">= threshold" />
            </el-form-item>
            <el-form-item label="Severity" required class="field-half">
              <el-select v-model="formData.highHighSeverity" placeholder="-" popper-class="alarm-config-popper">
                <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </div>
          <div class="level-row">
            <el-form-item label="Message" class="field-full">
              <el-input v-model="formData.highHighMessage" placeholder="Alarm message" />
            </el-form-item>
          </div>

          <el-divider content-position="left">High</el-divider>
          <div class="level-row">
            <el-form-item label="Value" required class="field-half">
              <el-input v-model="formData.highValue" placeholder=">= threshold" />
            </el-form-item>
            <el-form-item label="Severity" required class="field-half">
              <el-select v-model="formData.highSeverity" placeholder="-" popper-class="alarm-config-popper">
                <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </div>
          <div class="level-row">
            <el-form-item label="Message" class="field-full">
              <el-input v-model="formData.highMessage" placeholder="Alarm message" />
            </el-form-item>
          </div>

          <el-divider content-position="left">Low</el-divider>
          <div class="level-row">
            <el-form-item label="Value" required class="field-half">
              <el-input v-model="formData.lowValue" placeholder="<= threshold" />
            </el-form-item>
            <el-form-item label="Severity" required class="field-half">
              <el-select v-model="formData.lowSeverity" placeholder="-" popper-class="alarm-config-popper">
                <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </div>
          <div class="level-row">
            <el-form-item label="Message" class="field-full">
              <el-input v-model="formData.lowMessage" placeholder="Alarm message" />
            </el-form-item>
          </div>

          <el-divider content-position="left">Low-Low</el-divider>
          <div class="level-row">
            <el-form-item label="Value" required class="field-half">
              <el-input v-model="formData.lowLowValue" placeholder="<= threshold" />
            </el-form-item>
            <el-form-item label="Severity" required class="field-half">
              <el-select v-model="formData.lowLowSeverity" placeholder="-" popper-class="alarm-config-popper">
                <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </div>
          <div class="level-row">
            <el-form-item label="Message" class="field-full">
              <el-input v-model="formData.lowLowMessage" placeholder="Alarm message" />
            </el-form-item>
          </div>
        </template>

        <!-- Enum rules (ENUM only) -->
        <template v-if="formData.pointType === 'ENUM'">
          <el-divider content-position="left">Enum Values</el-divider>
          <div v-for="(item, idx) in enumFormItems" :key="idx" class="enum-item">
            <div class="level-row">
              <el-form-item label="Value" class="field-quarter">
                <el-input v-model="item.enumValue" placeholder="0" />
              </el-form-item>
              <el-form-item label="Meaning" class="field-quarter">
                <el-input v-model="item.enumMeaning" placeholder="e.g. Shutdown" />
              </el-form-item>
              <el-form-item label="Severity" class="field-quarter">
                <el-select v-model="item.severity" placeholder="-" clearable popper-class="alarm-config-popper">
                  <el-option v-for="s in enumSeverityOptions" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
              <el-form-item label="Time Delay(s)" class="field-quarter">
                <el-input v-model="item.timeDelay" placeholder="0" />
              </el-form-item>
            </div>
            <div class="level-row">
              <el-form-item label="Message" class="field-full">
                <el-input v-model="item.message" placeholder="Alarm message" />
              </el-form-item>
              <el-button type="danger" link size="small" class="enum-delete-btn"
                :icon="Delete" @click="removeEnumItem(idx)" :disabled="enumFormItems.length <= 1" />
            </div>
          </div>
          <el-button type="primary" link size="small" :icon="Plus" @click="addEnumItem">
            Add Enum Value
          </el-button>
        </template>
      </el-form>

      <template #footer>
        <el-button class="btn-secondary" @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" class="btn-primary" :loading="saving" @click="handleSave">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Search } from '@element-plus/icons-vue'
import { alarmApi } from '@/api/alarm'
import type { NumericRuleItem, EnumRuleItem } from '@/api/types'

const loading = ref(false)
const saving = ref(false)
const tableWrapperRef = ref<HTMLElement | null>(null)
const tableHeight = ref(400)
const dialogVisible = ref(false)
const isEdit = ref(false)
const searchQuery = ref('')

const severityOptions = ['Critical', 'Major', 'Minor', 'Warning']
const enumSeverityOptions = ['Critical', 'Major', 'Minor', 'Warning']

const tabs = [
  { key: 'numeric', label: 'Numeric' },
  { key: 'enum', label: 'Enum' },
]
const activeTab = ref('numeric')

// Table data per type
const numericData = ref<NumericRuleItem[]>([])
const enumData = ref<EnumRuleItem[]>([])

const filteredNumericData = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return numericData.value
  return numericData.value.filter((item) =>
    (item.alarmName && item.alarmName.toLowerCase().includes(q)) ||
    (item.highHighMessage && item.highHighMessage.toLowerCase().includes(q)) ||
    (item.highMessage && item.highMessage.toLowerCase().includes(q)) ||
    (item.lowMessage && item.lowMessage.toLowerCase().includes(q)) ||
    (item.lowLowMessage && item.lowLowMessage.toLowerCase().includes(q))
  )
})

const filteredEnumData = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return enumData.value
  return enumData.value.filter((item) =>
    (item.alarmName && item.alarmName.toLowerCase().includes(q)) ||
    (item.enumMeaning && item.enumMeaning.toLowerCase().includes(q)) ||
    (item.message && item.message.toLowerCase().includes(q))
  )
})

// Windowed rendering: the two tables hold ~1000 combined rows and mounting
// them at once takes seconds. Only the first WINDOW_BATCH rows render; the
// capture-phase scroll listener on each table wrapper (scroll does not bubble,
// but capture reaches the wrapper from the inner scrollbar) appends the next
// batch when the scrollbar nears the bottom. Full arrays stay in memory -
// edit dialogs, delete and any future filtering keep operating on whole sets.
const WINDOW_BATCH = 50
const WINDOW_BOTTOM_THRESHOLD_PX = 40

const numericVisible = ref(WINDOW_BATCH)
const enumVisible = ref(WINDOW_BATCH)
const displayedNumeric = computed(() => filteredNumericData.value.slice(0, numericVisible.value))
const displayedEnum = computed(() => filteredEnumData.value.slice(0, enumVisible.value))

function onTableScroll(event: Event, type: 'numeric' | 'enum') {
  const el = event.target as HTMLElement
  if (el.scrollTop + el.clientHeight < el.scrollHeight - WINDOW_BOTTOM_THRESHOLD_PX) {
    return
  }
  if (type === 'numeric' && numericVisible.value < filteredNumericData.value.length) {
    numericVisible.value = Math.min(numericVisible.value + WINDOW_BATCH, filteredNumericData.value.length)
  } else if (type === 'enum' && enumVisible.value < filteredEnumData.value.length) {
    enumVisible.value = Math.min(enumVisible.value + WINDOW_BATCH, filteredEnumData.value.length)
  }
}

// Form data (union of all three types; only relevant fields used per type)
const formData = ref<any>({})
const enumFormItems = ref<EnumRuleItem[]>([])

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

function emptyForm() {
  return {
    alarmName: '',
    pointName: '',
    pointType: '',
    // numeric per-point
    timeDelay: null as number | null,
    deadband: '',
    // numeric levels
    highHighValue: '', highHighSeverity: '', highHighMessage: '',
    highValue: '', highSeverity: '', highMessage: '',
    lowValue: '', lowSeverity: '', lowMessage: '',
    lowLowValue: '', lowLowSeverity: '', lowLowMessage: '',
  }
}

function emptyEnumItem(): EnumRuleItem {
  return { alarmName: '', enumValue: '', enumMeaning: '', severity: '', message: '', timeDelay: null }
}

function addEnumItem() {
  enumFormItems.value.push({ ...emptyEnumItem() })
}

function removeEnumItem(idx: number) {
  enumFormItems.value.splice(idx, 1)
}

function onTabChange(tab: string) {
  activeTab.value = tab
}

async function fetchRules() {
  loading.value = true
  try {
    const [nums, enums] = await Promise.all([
      alarmApi.getNumericRules(),
      alarmApi.getEnumRules(),
    ])
    numericData.value = nums
    enumData.value = enums
    // Fresh data landed: restart the window so the view starts from the top.
    numericVisible.value = WINDOW_BATCH
    enumVisible.value = WINDOW_BATCH
  } catch (e) {
    console.error('Failed to fetch alarm rules:', e)
    ElMessage.error('Failed to load alarm rules')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  isEdit.value = false
  formData.value = emptyForm()
  enumFormItems.value = [emptyEnumItem()]
  dialogVisible.value = true
}

function openEditDialog(row: any) {
  isEdit.value = true
  const type = activeTab.value
  if (type === 'numeric') {
    formData.value = { ...emptyForm(), ...JSON.parse(JSON.stringify(row)) }
    formData.value.pointType = 'NUMERIC'
  } else if (type === 'enum') {
    // For enum, load all rows with the same alarm_name
    const alarmName = row.alarmName
    const items = enumData.value
      .filter(e => e.alarmName === alarmName)
      .map(e => JSON.parse(JSON.stringify(e)))
    enumFormItems.value = items.length > 0 ? items : [emptyEnumItem()]
    formData.value = { ...emptyForm(), alarmName, pointType: 'ENUM' }
  }
  dialogVisible.value = true
}

function resetForm() {
  formData.value = emptyForm()
  enumFormItems.value = [emptyEnumItem()]
}

async function handleSave() {
  const f = formData.value
  const type = f.pointType

  if (!isEdit.value && !f.pointName?.trim()) {
    ElMessage.warning('Point Name is required')
    return
  }
  if (!type) {
    ElMessage.warning('Point Type is required')
    return
  }

  saving.value = true
  try {
    if (type === 'NUMERIC') {
      const levels = [
        ['highHighValue', 'highHighSeverity'],
        ['highValue', 'highSeverity'],
        ['lowValue', 'lowSeverity'],
        ['lowLowValue', 'lowLowSeverity'],
      ] as const
      for (const [vKey, sKey] of levels) {
        if (f[vKey]?.trim() && !f[sKey]) {
          ElMessage.warning(`Severity is required when ${vKey} is filled`); saving.value = false; return
        }
      }
      const hasAny = levels.some(([v]) => f[v]?.toString().trim() !== '')
      if (!hasAny) { ElMessage.warning('At least one threshold Value is required'); saving.value = false; return }

      const payload: NumericRuleItem = {
        ...(isEdit.value ? { id: f.id } : { pointName: f.pointName }),
        alarmName: f.alarmName,
        pointType: 'NUMERIC',
        timeDelay: f.timeDelay !== '' && f.timeDelay !== null && f.timeDelay !== undefined ? Number(f.timeDelay) : null,
        deadband: f.deadband,
        highHighValue: f.highHighValue, highHighSeverity: f.highHighSeverity,
        highHighMessage: f.highHighMessage,
        highValue: f.highValue, highSeverity: f.highSeverity,
        highMessage: f.highMessage,
        lowValue: f.lowValue, lowSeverity: f.lowSeverity,
        lowMessage: f.lowMessage,
        lowLowValue: f.lowLowValue, lowLowSeverity: f.lowLowSeverity,
        lowLowMessage: f.lowLowMessage,
      }
      if (isEdit.value && payload.id) {
        await alarmApi.updateNumericRule(payload.id, payload)
      } else {
        await alarmApi.createNumericRule(payload)
      }
    } else if (type === 'ENUM') {
      const items = enumFormItems.value.filter(e => e.enumValue !== null && e.enumValue !== undefined && String(e.enumValue).trim() !== '')
      if (items.length === 0) { ElMessage.warning('At least one enum value is required'); saving.value = false; return }
      for (const e of items) {
        e.enumValue = String(e.enumValue).trim()
      }
      // Check for duplicate enum values within the same point
      const seen = new Set<string>()
      for (const e of items) {
        if (seen.has(e.enumValue)) {
          ElMessage.warning(`Duplicate enum value: ${e.enumValue}. Each value must be unique.`)
          saving.value = false
          return
        }
        seen.add(e.enumValue)
      }
      const payload: EnumRuleItem[] = items.map(e => ({
        ...e,
        alarmName: f.alarmName,
        pointType: 'ENUM',
        pointName: isEdit.value ? undefined : f.pointName,
      }))
      if (isEdit.value) {
        await alarmApi.updateEnumRules(f.alarmName, payload)
      } else {
        await alarmApi.createEnumRules(payload)
      }
    }

    ElMessage.success(isEdit.value ? 'Rule updated' : 'Rule created')
    dialogVisible.value = false
    await fetchRules()
  } catch (e) {
    console.error('Failed to save alarm rule:', e)
    ElMessage.error(isEdit.value ? 'Failed to update rule' : 'Failed to create rule')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  const type = activeTab.value
  const name = row.alarmName || row.id
  try {
    await ElMessageBox.confirm(
      `Delete rule "${name}"? This action cannot be undone.`,
      'Confirm Delete',
      { type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel' }
    )
  } catch {
    return
  }

  try {
    if (type === 'numeric') {
      await alarmApi.deleteNumericRule(row.id)
    } else if (type === 'enum') {
      await alarmApi.deleteEnumRules(row.alarmName)
    }
    ElMessage.success('Rule deleted')
    await fetchRules()
  } catch (e) {
    console.error('Failed to delete alarm rule:', e)
    ElMessage.error('Failed to delete rule')
  }
}

// Size the table from the measured wrapper height instead of a magic
// window.innerHeight offset: the wrapper is the flex child that already
// absorbs the real page chrome (top navbar, bottom toolbar, paddings), so
// the table fills the viewport exactly and leaves no blank strip above the
// bottom menu bar. The ResizeObserver keeps it in sync on window resizes.
let wrapperResizeObserver: ResizeObserver | null = null

function updateTableHeight() {
  if (tableWrapperRef.value) {
    tableHeight.value = Math.max(300, tableWrapperRef.value.clientHeight)
  }
}

// Function template ref: the numeric/enum wrappers are v-if alternated, so a
// tab switch swaps the mounted element; rebind the observer on every swap.
function setTableWrapperRef(el: Element | ComponentPublicInstance | null) {
  wrapperResizeObserver?.disconnect()
  const node = el instanceof HTMLElement ? el : null
  tableWrapperRef.value = node
  if (node) {
    if (!wrapperResizeObserver) {
      wrapperResizeObserver = new ResizeObserver(updateTableHeight)
    }
    wrapperResizeObserver.observe(node)
    updateTableHeight()
  }
}

onMounted(() => {
  fetchRules()
})

onBeforeUnmount(() => {
  wrapperResizeObserver?.disconnect()
  wrapperResizeObserver = null
})
</script>

<style scoped lang="scss">
.alarm-config-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid $border-color;
  flex-shrink: 0;
}

.tab-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  padding: 0 12px;
}

.tab-item {
  padding: 8px 20px;
  font-size: 13px;
  color: $text-secondary;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
  &:hover { color: $text-primary; background-color: $bg-card-hover; }
  &.active { color: $accent-green; border-bottom-color: $accent-green; }
}

.search-box-table {
  width: 240px;
  flex-shrink: 0;

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

.config-table-wrapper {
  flex: 1;
  min-height: 0;
}

// Footer inside the el-table #append slot: tells the user how much of the
// windowed list is currently mounted and that scrolling loads more.
.table-window-hint {
  padding: 6px 10px;
  font-size: 10px;
  color: #6b7280;
  background-color: #111827;
  border-bottom: 1px solid #1f2937;
}

.basic-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  .basic-name { flex: 1 1 auto; .el-input { width: 100%; } }
  .basic-type { flex: 0 0 auto; }
  // !important beats global.scss `.el-dialog .el-form-item { margin-bottom: 22px !important }`
  // (the test branch applies 22px to every dialog; this dialog is compact by design).
  .el-form-item { margin-bottom: 4px !important; }
}

.level-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  .el-form-item {
    // Rows inside one level group sit tight; group separation comes from the
    // divider margins. is-error needs extra room for the absolute-positioned
    // validation message below the field. !important beats the global
    // `.el-dialog .el-form-item { margin-bottom: 22px !important }` rule.
    margin-bottom: 4px !important;
    &.is-error {
      margin-bottom: 18px !important;
    }
  }
  /* Element Plus sets width:100% on .el-form-item by default, which forces
     each item onto its own line. Override so flex-basis from the field-* classes
     actually controls the width. */
  // :deep(.el-form-item) { width: auto; }
  .field-half {
    flex: 1 1 0; min-width: 0;
    .el-input, .el-select { width: 100%; }
  }
  .field-quarter {
    flex: 1 1 0; min-width: 0;
    .el-input, .el-select { width: 100%; }
  }
  .field-full {
    flex: 1 1 0;
    .el-input { width: 100%; }
  }
  .enum-delete-btn {
    flex: 0 0 auto;
    height: 24px; 
  }
}

:deep(.el-form-item__label){
  padding-right: 4px;
}

.type-hint {
  padding: 10px 12px;
  color: $text-muted;
  font-size: 10px;
  text-align: center;
}

// Enum items in the form: visual separation between multi-value rows
.enum-item {
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed $border-color;
  &:last-child { border-bottom: none; }
}

// Dark theme for el-table (consistent with Active Alarms table: 10px font, 20px row height, no vertical padding)
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

  // Compact row height: zero out cell padding, force .cell to 20px line/height.
  // Element Plus sets line-height: 23px on .cell which dominates row height;
  // override with higher specificity + !important (same approach as AlarmTable.vue).
  // NOTE: EP table header <th> only carries the `el-table__cell` class (NOT
  // `el-table__header-cell`), so we target `th.el-table__cell` for headers.
  .el-table__cell { padding: 0 0 !important; }
  .el-table__cell .cell { padding: 0 4px !important; }
  .el-table__header-cell { padding: 0 4px !important; }
  td.el-table__cell .cell,
  th.el-table__cell .cell {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    display: flex;
    align-items: center;
    justify-content: flex-start;
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

:deep(.el-dialog) {
  background-color: $bg-card;
  border: 1px solid $border-card;
  .el-dialog__title { color: $text-primary; }
  .el-dialog__body { color: $text-primary; }
}
:deep(.el-divider__text) { background-color: $bg-card; color: $text-secondary; }
:deep(.el-divider--horizontal) { margin: 12px 0; }
:deep(.el-form-item__label) { color: $text-secondary; }
:deep(.el-input__wrapper) {
  background-color: $bg-dark;
  box-shadow: 0 0 0 1px $border-card inset;
  &:hover { box-shadow: 0 0 0 1px $text-muted inset; }
}
:deep(.el-input.is-disabled .el-input__wrapper) { background-color: $bg-card-hover; }
:deep(.el-input__inner) {
  color: $text-primary;
  &::placeholder { color: $text-muted; -webkit-text-fill-color: $text-muted; }
}
:deep(.el-input.is-disabled .el-input__inner) {
  color: $text-primary; -webkit-text-fill-color: $text-primary;
}
:deep(.el-select__wrapper) {
  background-color: $bg-dark;
  box-shadow: 0 0 0 1px $border-card inset;
  &:hover { box-shadow: 0 0 0 1px $text-muted inset; }
}
:deep(.el-select__placeholder) { color: $text-muted; }
:deep(.el-select__selected-item) { color: $text-primary; }
:deep(.el-select__caret) { color: $text-secondary; }
</style>

<style lang="scss">
// Select dropdown options (teleported to body) - dark theme + compact (10px font, 20px row height, no padding).
// Consistent with the AlarmTable filter-select popper styling.
.el-select__popper.alarm-config-popper {
  .el-select-dropdown { background-color: #111827; border: 1px solid #2d3748; }
  .el-select-dropdown__item {
    color: #9ca3af;
    font-size: 10px;
    height: 20px;
    line-height: 20px;
    padding: 0 8px;
    &:hover, &.is-hovering { background-color: #1a2332; color: #e5e7eb; }
    &.is-selected { color: #10b981; font-weight: 600; }
  }
  .el-popper__arrow::before { background-color: #111827; border-color: #2d3748; }
}
</style>
