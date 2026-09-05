<template>
  <el-dialog
    v-model="visible"
    title="Export Events"
    width="440px"
    append-to-body
    class="dark-dialog"
  >
    <el-form label-width="100px" label-position="left">
      <el-form-item label="Record Time" required>
        <el-date-picker
          v-model="range"
          type="datetimerange"
          :shortcuts="shortcuts"
          value-format="x"
          start-placeholder="From"
          end-placeholder="To"
          style="width: 100%"
        />
        <div class="form-tip">Range is filtered by record (write) time</div>
      </el-form-item>

      <el-form-item label="Severity">
        <el-select
          v-model="severities"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          placeholder="All severities"
          style="width: 100%"
        >
          <el-option v-for="s in SEVERITIES" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>

      <el-form-item label="Alarm Only">
        <el-switch v-model="alarmOnly" />
        <div class="form-tip">On = alarm frames only, off = every stored frame in range</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button class="btn-secondary" @click="visible = false">Cancel</el-button>
      <el-button
        type="primary"
        class="btn-primary"
        :loading="exporting"
        :disabled="!hasRange"
        @click="onExport"
      >Export</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportAlarmEvents } from '@/api/alarm'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void }>()

const visible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const SEVERITIES = ['Critical', 'Major', 'Minor', 'Warning']

// value-format="x" makes the picker model epoch-millis strings; Number() is
// applied before the request. The dialog starts empty - Export stays disabled
// until a range is picked, because the backend rejects unbounded exports.
const range = ref<[string, string] | null>(null)
const severities = ref<string[]>([])
const alarmOnly = ref(true)
const exporting = ref(false)

const hasRange = computed(() => !!range.value && range.value[0] !== '' && range.value[1] !== '')

const shortcuts = [
  { text: 'Last 1h', value: () => { const end = new Date(); return [new Date(end.getTime() - 3_600_000), end] } },
  { text: 'Last 24h', value: () => { const end = new Date(); return [new Date(end.getTime() - 86_400_000), end] } },
  { text: 'Last 7d', value: () => { const end = new Date(); return [new Date(end.getTime() - 7 * 86_400_000), end] } },
]

async function onExport() {
  if (!hasRange.value || exporting.value) return
  exporting.value = true
  try {
    const result = await exportAlarmEvents({
      start: Number(range.value![0]),
      end: Number(range.value![1]),
      severity: severities.value.length > 0 ? severities.value : undefined,
      alarmOnly: alarmOnly.value,
    })
    downloadBlob(result.blob, result.fileName)
    ElMessage.success(`Exported ${result.rows ?? 'unknown'} row(s) to ${result.fileName}`)
    visible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : 'Export failed')
  } finally {
    exporting.value = false
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<style scoped lang="scss">
.form-tip {
  font-size: 10px;
  color: #64748b;
  line-height: 14px;
  margin-top: 4px;
}

// Button styling copied from SystemDeploymentView.vue (Device Template /
// Device Deployment buttons) so the export controls match that look. Scoped
// on purpose - promoting it to a global style would restyle every el-button
// in the app.
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
}

.btn-primary {
  background-color: rgba($accent-green, 0.16) !important;
  color: #34d399 !important;

  &:hover {
    background-color: rgba($accent-green, 0.28) !important;
    color: #6ee7b7 !important;
  }
}

.btn-secondary {
  background-color: rgba($accent-blue, 0.16) !important;
  color: #93c5fd !important;

  &:hover {
    background-color: rgba($accent-blue, 0.28) !important;
    color: #bfdbfe !important;
  }
}
</style>
