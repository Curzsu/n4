<template>
  <div class="log-view">
    <!-- Header -->
    <div class="config-header">
      <span class="config-title">Audit Logs</span>
      <div class="header-actions">
        <el-button size="small" class="btn-secondary" :icon="Refresh" @click="loadLogs">
          Refresh
        </el-button>
      </div>
    </div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">Operator:</span>
        <el-input
          v-model="query.operatorName"
          size="small"
          placeholder="Operator"
          clearable
          style="width: 120px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">Module:</span>
        <el-input
          v-model="query.module"
          size="small"
          placeholder="e.g. User"
          clearable
          style="width: 120px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">Operation:</span>
        <el-input
          v-model="query.operation"
          size="small"
          placeholder="e.g. Add"
          clearable
          style="width: 120px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">Status:</span>
        <el-select
          v-model="query.status"
          size="small"
          placeholder="All"
          clearable
          style="width: 90px"
          popper-class="alarm-config-popper"
          @change="handleSearch"
        >
          <el-option label="Success" :value="1" />
          <el-option label="Failed" :value="0" />
        </el-select>
      </div>
      <div class="filter-item">
        <span class="filter-label">Time:</span>
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          size="small"
          range-separator="-"
          start-placeholder="Start"
          end-placeholder="End"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 270px"
          popper-class="alarm-datepicker-popper"
          @change="handleSearch"
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
        :data="logs"
        height="100%"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        size="small"
        border
        class="custom-table"
        v-loading="loading"
      >
        <el-table-column prop="logId" label="ID" width="55" align="center" header-align="center" />
        <el-table-column prop="operatorName" label="Operator" width="110" show-overflow-tooltip />
        <el-table-column prop="module" label="Module" width="120" show-overflow-tooltip />
        <el-table-column prop="operation" label="Operation" min-width="140" show-overflow-tooltip />
        <el-table-column label="Type" width="75" align="center" header-align="center">
          <template #default="{ row }">
            <span :class="['type-badge', businessTypeClass(row.businessType)]">{{ businessTypeLabel(row.businessType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="75" align="center" header-align="center">
          <template #default="{ row }">
            <span :class="['status-badge', row.status === 1 ? 'active' : 'disabled']">
              {{ row.status === 1 ? 'Success' : 'Failed' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="Cost(ms)" width="70" align="center" header-align="center">
          <template #default="{ row }">
            <span class="cost-text">{{ row.costTime }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorIp" label="IP Address" width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" label="Timestamp" width="145" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="Actions" width="75" align="center" header-align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row as OperationLog)">
              Detail
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        size="small"
        @change="loadLogs"
      />
    </div>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailVisible" title="Audit Log Details" width="650px" class="dark-dialog" :close-on-click-modal="false">
      <div v-if="detail" class="log-detail-modal">
        <div class="detail-grid">
          <div class="detail-row"><span class="detail-k">Log ID:</span><span class="detail-v">{{ detail.logId }}</span></div>
          <div class="detail-row"><span class="detail-k">Operator:</span><span class="detail-v">{{ detail.operatorName }}</span></div>
          <div class="detail-row"><span class="detail-k">Module:</span><span class="detail-v">{{ detail.module }}</span></div>
          <div class="detail-row"><span class="detail-k">Operation:</span><span class="detail-v">{{ detail.operation }}</span></div>
          <div class="detail-row">
            <span class="detail-k">Business Type:</span>
            <span class="detail-v">
              <span :class="['type-badge', businessTypeClass(detail.businessType)]">{{ businessTypeLabel(detail.businessType) }}</span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-k">Status:</span>
            <span class="detail-v">
              <span :class="['status-badge', detail.status === 1 ? 'active' : 'disabled']">{{ detail.status === 1 ? 'Success' : 'Failed' }}</span>
            </span>
          </div>
          <div class="detail-row"><span class="detail-k">HTTP Method:</span><span class="detail-v">{{ detail.requestMethod }}</span></div>
          <div class="detail-row"><span class="detail-k">Cost Time:</span><span class="detail-v">{{ detail.costTime }} ms</span></div>
          <div class="detail-row"><span class="detail-k">Source IP:</span><span class="detail-v">{{ detail.operatorIp }}</span></div>
          <div class="detail-row"><span class="detail-k">Timestamp:</span><span class="detail-v">{{ detail.createTime }}</span></div>
          <div class="detail-row full-width"><span class="detail-k">Request URI:</span><span class="detail-v code-text">{{ detail.requestUri }}</span></div>
          <div class="detail-row full-width"><span class="detail-k">Java Method:</span><span class="detail-v code-text">{{ detail.method }}</span></div>
          <div class="detail-row full-width params-row">
            <span class="detail-k">Parameters:</span>
            <pre class="params-pre">{{ formatParams(detail.requestParams) }}</pre>
          </div>
          <div v-if="detail.status === 0" class="detail-row full-width error-row">
            <span class="detail-k">Error Message:</span>
            <span class="error-msg">{{ detail.errorMsg }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button size="small" class="btn-secondary" @click="detailVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { logApi } from '@/api/log'
import type { LogQuery, OperationLog } from '@/api/types'

const loading = ref(false)
const logs = ref<OperationLog[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)
const query = reactive<LogQuery>({
  operatorName: '',
  module: '',
  operation: '',
  status: undefined,
  page: 1,
  pageSize: 20,
})

const detailVisible = ref(false)
const detail = ref<OperationLog | null>(null)

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

const businessTypeMap: Record<number, { label: string; cls: string }> = {
  1: { label: 'Create', cls: 'create' },
  2: { label: 'Update', cls: 'update' },
  3: { label: 'Delete', cls: 'delete' },
  4: { label: 'Grant', cls: 'grant' },
  0: { label: 'Other', cls: 'other' },
}

function businessTypeLabel(type?: number): string {
  return type != null && businessTypeMap[type] ? businessTypeMap[type].label : '-'
}

function businessTypeClass(type?: number): string {
  return type != null && businessTypeMap[type] ? businessTypeMap[type].cls : 'other'
}

function formatParams(params?: string): string {
  if (!params) return '-'
  try {
    return JSON.stringify(JSON.parse(params), null, 2)
  } catch {
    return params
  }
}

async function loadLogs() {
  loading.value = true
  try {
    query.startTime = dateRange.value?.[0]
    query.endTime = dateRange.value?.[1]
    const data = await logApi.page(query)
    logs.value = data.list || data.records || []
    total.value = data.total
  } catch (e) {
    console.error('Failed to load audit logs:', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadLogs()
}

function handleReset() {
  query.operatorName = ''
  query.module = ''
  query.operation = ''
  query.status = undefined
  dateRange.value = null
  query.page = 1
  loadLogs()
}

async function openDetail(row: OperationLog) {
  detail.value = await logApi.detail(row.logId)
  detailVisible.value = true
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped lang="scss">
.log-view {
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
  gap: 10px;
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

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-date-editor.el-input__wrapper),
  :deep(.el-range-editor.el-input__wrapper) {
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

  :deep(.el-input__inner),
  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder),
  :deep(.el-range-input) {
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    color: #e5e7eb !important;

    &::placeholder {
      font-size: 10px !important;
      color: #64748b !important;
    }
  }

  :deep(.el-range-separator) {
    font-size: 10px !important;
    line-height: 20px !important;
    color: #64748b !important;
    padding: 0 4px !important;
  }

  :deep(.el-input__prefix),
  :deep(.el-input__suffix),
  :deep(.el-select__suffix),
  :deep(.el-range__icon),
  :deep(.el-range__close-icon) {
    font-size: 10px !important;
    line-height: 20px !important;
  }
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 4px 0 0;
  flex-shrink: 0;
}

/* ========================================================== */
/* BORDERLESS ROUNDED CAPSULE TAGS & BADGES                  */
/* ========================================================== */
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

  &.create { background-color: rgba($accent-green, 0.14) !important; color: #4ade80 !important; }
  &.update { background-color: rgba($accent-yellow, 0.14) !important; color: #facc15 !important; }
  &.delete { background-color: rgba($accent-red, 0.16) !important; color: #fca5a5 !important; }
  &.grant { background-color: rgba($accent-blue, 0.14) !important; color: #7dd3fc !important; }
  &.other { background-color: rgba(100, 116, 139, 0.14) !important; color: #94a3b8 !important; }
}

.cost-text {
  color: $text-secondary;
  font-size: 10px;
  font-family: monospace;
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

// Dark log detail modal
.log-detail-modal {
  padding: 4px 0;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 11px;

  &.full-width {
    grid-column: 1 / -1;
  }

  .detail-k {
    color: $text-secondary;
    min-width: 90px;
    font-weight: 500;
    flex-shrink: 0;
  }

  .detail-v {
    color: $text-primary;
    word-break: break-all;
  }
}

.code-text {
  font-family: monospace;
  color: #93c5fd !important;
  font-size: 11px;
}

.params-pre {
  margin: 0;
  padding: 8px;
  background-color: #0b1119;
  border: 1px solid #1e293b;
  border-radius: 4px;
  font-family: monospace;
  font-size: 10px;
  color: #a5f3fc;
  max-height: 180px;
  overflow-y: auto;
  flex: 1;
}

.error-msg {
  color: #fca5a5;
  font-size: 11px;
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
</style>
