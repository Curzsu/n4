<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? 'Edit Alarm Rule' : 'Add Alarm Rule'"
    width="720px"
    top="6vh"
    append-to-body
    class="dark-dialog alarm-rule-modal"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="resetForm"
  >
    <div v-loading="loading">
      <el-form :model="formData" size="small" label-position="right">
        <!-- Basic fields: Alarm Name + Point Type on the same row -->
        <div class="basic-row">
          <el-form-item label="Alarm Name" class="basic-name">
            <el-input v-model="formData.alarmName" :placeholder="formData.pointName || 'e.g. Temperature_High_00'" />
          </el-form-item>
          <el-form-item label="Point Type" required class="basic-type">
            <el-select
              v-model="formData.pointType"
              placeholder="Select type"
              style="width: 140px"
              popper-class="alarm-config-popper"
            >
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
            <el-form-item label="Value" class="field-half">
              <el-input v-model="formData.highHighValue" placeholder=">= threshold" />
            </el-form-item>
            <el-form-item label="Severity" class="field-half">
              <el-select v-model="formData.highHighSeverity" placeholder="-" clearable popper-class="alarm-config-popper">
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
            <el-form-item label="Value" class="field-half">
              <el-input v-model="formData.highValue" placeholder=">= threshold" />
            </el-form-item>
            <el-form-item label="Severity" class="field-half">
              <el-select v-model="formData.highSeverity" placeholder="-" clearable popper-class="alarm-config-popper">
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
            <el-form-item label="Value" class="field-half">
              <el-input v-model="formData.lowValue" placeholder="<= threshold" />
            </el-form-item>
            <el-form-item label="Severity" class="field-half">
              <el-select v-model="formData.lowSeverity" placeholder="-" clearable popper-class="alarm-config-popper">
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
            <el-form-item label="Value" class="field-half">
              <el-input v-model="formData.lowLowValue" placeholder="<= threshold" />
            </el-form-item>
            <el-form-item label="Severity" class="field-half">
              <el-select v-model="formData.lowLowSeverity" placeholder="-" clearable popper-class="alarm-config-popper">
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
                <el-input v-model="item.enumMeaning" placeholder="e.g. Fault / Trip" />
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
              <el-button
                type="danger"
                link
                size="small"
                class="enum-delete-btn"
                :icon="Delete"
                @click="removeEnumItem(idx)"
                :disabled="enumFormItems.length <= 1"
              />
            </div>
          </div>
          <el-button type="primary" link size="small" :icon="Plus" @click="addEnumItem">
            Add Enum Value
          </el-button>
        </template>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-secondary" @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" class="btn-primary" :loading="saving" @click="handleSave">Save</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { alarmApi } from '@/api/alarm'
import { templateApi } from '@/api/template'
import type { NumericRuleItem, EnumRuleItem, PointTemplateItem } from '@/api/types'

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const currentPoint = ref<PointTemplateItem | null>(null)

const severityOptions = ['Critical', 'Major', 'Minor', 'Warning']
const enumSeverityOptions = ['Critical', 'Major', 'Minor', 'Warning']

const formData = ref<any>(emptyForm())
const enumFormItems = ref<EnumRuleItem[]>([])

function emptyForm() {
  return {
    id: undefined as number | undefined,
    alarmName: '',
    pointName: '',
    pointType: '',
    timeDelay: null as number | null,
    deadband: '',
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

function resetForm() {
  formData.value = emptyForm()
  enumFormItems.value = [emptyEnumItem()]
  currentPoint.value = null
  isEdit.value = false
}

async function open(point: PointTemplateItem) {
  resetForm()
  currentPoint.value = point
  dialogVisible.value = true
  loading.value = true

  try {
    const rawType = (point.type || '').toLowerCase()
    const isEnum = rawType === 'enum' || rawType === 'boolean'
    const targetType = isEnum ? 'ENUM' : 'NUMERIC'
    const targetAlarmCode = point.alarmCode?.trim()

    if (targetAlarmCode) {
      if (isEnum) {
        const enumRules = await alarmApi.getEnumRules()
        const matched = enumRules.filter(r => r.alarmName === targetAlarmCode)
        if (matched.length > 0) {
          isEdit.value = true
          formData.value = {
            ...emptyForm(),
            alarmName: targetAlarmCode,
            pointName: point.pointName,
            pointType: 'ENUM',
          }
          enumFormItems.value = matched.map(m => ({ ...m }))
        } else {
          initNewForm(point, targetType, targetAlarmCode)
        }
      } else {
        const numRules = await alarmApi.getNumericRules()
        const matched = numRules.find(r => r.alarmName === targetAlarmCode)
        if (matched) {
          isEdit.value = true
          formData.value = {
            ...emptyForm(),
            ...JSON.parse(JSON.stringify(matched)),
            pointName: point.pointName,
            pointType: 'NUMERIC',
          }
        } else {
          initNewForm(point, targetType, targetAlarmCode)
        }
      }
    } else {
      initNewForm(point, targetType, `${point.pointName}_00`)
    }
  } catch (e) {
    console.error('Failed to load alarm rule for point:', e)
    ElMessage.error('Failed to load alarm rule')
  } finally {
    loading.value = false
  }
}

function initNewForm(point: PointTemplateItem, pointType: 'NUMERIC' | 'ENUM', defaultAlarmName: string) {
  isEdit.value = false
  formData.value = {
    ...emptyForm(),
    alarmName: defaultAlarmName || point.pointName,
    pointName: point.pointName,
    pointType,
  }
  if (pointType === 'ENUM') {
    enumFormItems.value = [emptyEnumItem()]
  }
}

async function handleSave() {
  const f = formData.value
  const type = f.pointType
  const alarmName = f.alarmName?.trim() || f.pointName?.trim()

  if (!alarmName) {
    ElMessage.warning('Alarm Name is required')
    return
  }
  if (alarmName.length > 64) {
    ElMessage.warning('Alarm Name cannot exceed 64 characters')
    return
  }
  if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(alarmName) || /[^\x00-\x7F]/.test(alarmName)) {
    ElMessage.warning('Alarm Name cannot contain Chinese characters')
    return
  }
  if (!type) {
    ElMessage.warning('Point Type is required')
    return
  }

  if (type === 'NUMERIC') {
    if (f.timeDelay !== '' && f.timeDelay !== null && f.timeDelay !== undefined) {
      const td = Number(f.timeDelay)
      if (isNaN(td) || !Number.isInteger(td) || td < 0 || td > 86400) {
        ElMessage.warning('Time Delay must be an integer between 0 and 86400 seconds')
        return
      }
    }

    if (f.deadband !== '' && f.deadband !== null && f.deadband !== undefined) {
      const db = Number(f.deadband)
      if (isNaN(db) || db < 0) {
        ElMessage.warning('Deadband must be a non-negative number')
        return
      }
    }

    const levels = [
      ['highHighValue', 'highHighSeverity', 'highHighMessage'],
      ['highValue', 'highSeverity', 'highMessage'],
      ['lowValue', 'lowSeverity', 'lowMessage'],
      ['lowLowValue', 'lowLowSeverity', 'lowLowMessage'],
    ] as const

    for (const [vKey, sKey, mKey] of levels) {
      const val = f[vKey]?.toString().trim()
      if (val) {
        const numVal = Number(val)
        if (isNaN(numVal)) {
          ElMessage.warning(`Threshold ${vKey} must be a valid number`)
          return
        }
        if (!f[sKey]) {
          ElMessage.warning(`Severity is required when ${vKey} is configured`)
          return
        }
      }
      const msg = f[mKey]?.toString().trim()
      if (msg) {
        if (msg.length > 150) {
          ElMessage.warning(`Message for ${vKey} cannot exceed 150 characters`)
          return
        }
        if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(msg) || /[^\x00-\x7F]/.test(msg)) {
          ElMessage.warning(`Message for ${vKey} cannot contain Chinese characters`)
          return
        }
      }
    }

    const hasAny = levels.some(([v]) => f[v]?.toString().trim() !== '')
    if (!hasAny) {
      ElMessage.warning('At least one threshold Value is required')
      return
    }

    // Cross-threshold logical ordering check
    const hh = f.highHighValue?.toString().trim() !== '' ? Number(f.highHighValue) : null
    const h = f.highValue?.toString().trim() !== '' ? Number(f.highValue) : null
    const l = f.lowValue?.toString().trim() !== '' ? Number(f.lowValue) : null
    const ll = f.lowLowValue?.toString().trim() !== '' ? Number(f.lowLowValue) : null

    if (hh !== null && h !== null && hh <= h) {
      ElMessage.warning(`High-High threshold (${hh}) must be greater than High threshold (${h})`)
      return
    }
    if (hh !== null && l !== null && hh <= l) {
      ElMessage.warning(`High-High threshold (${hh}) must be greater than Low threshold (${l})`)
      return
    }
    if (hh !== null && ll !== null && hh <= ll) {
      ElMessage.warning(`High-High threshold (${hh}) must be greater than Low-Low threshold (${ll})`)
      return
    }
    if (h !== null && l !== null && h <= l) {
      ElMessage.warning(`High threshold (${h}) must be greater than Low threshold (${l})`)
      return
    }
    if (h !== null && ll !== null && h <= ll) {
      ElMessage.warning(`High threshold (${h}) must be greater than Low-Low threshold (${ll})`)
      return
    }
    if (l !== null && ll !== null && l <= ll) {
      ElMessage.warning(`Low threshold (${l}) must be greater than Low-Low threshold (${ll})`)
      return
    }
  } else if (type === 'ENUM') {
    const items = enumFormItems.value.filter(e => e.enumValue !== null && e.enumValue !== undefined && String(e.enumValue).trim() !== '')
    if (items.length === 0) {
      ElMessage.warning('At least one enum value is required')
      return
    }
    for (const e of items) {
      e.enumValue = String(e.enumValue).trim()
      if (e.enumValue.length > 50) {
        ElMessage.warning(`Enum value "${e.enumValue}" exceeds 50 characters`)
        return
      }
      if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(e.enumValue) || /[^\x00-\x7F]/.test(e.enumValue)) {
        ElMessage.warning(`Enum value "${e.enumValue}" cannot contain Chinese characters`)
        return
      }
      if (e.enumMeaning) {
        e.enumMeaning = String(e.enumMeaning).trim()
        if (e.enumMeaning.length > 50) {
          ElMessage.warning(`Enum meaning cannot exceed 50 characters`)
          return
        }
        if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(e.enumMeaning) || /[^\x00-\x7F]/.test(e.enumMeaning)) {
          ElMessage.warning(`Enum meaning cannot contain Chinese characters`)
          return
        }
      }
      if (e.message) {
        e.message = String(e.message).trim()
        if (e.message.length > 150) {
          ElMessage.warning(`Enum message cannot exceed 150 characters`)
          return
        }
        if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(e.message) || /[^\x00-\x7F]/.test(e.message)) {
          ElMessage.warning(`Enum message cannot contain Chinese characters`)
          return
        }
      }
      if (e.timeDelay !== null && e.timeDelay !== undefined) {
        const td = Number(e.timeDelay)
        if (isNaN(td) || !Number.isInteger(td) || td < 0 || td > 86400) {
          ElMessage.warning(`Time Delay for enum "${e.enumValue}" must be between 0 and 86400 seconds`)
          return
        }
      }
    }
    const seen = new Set<string>()
    for (const e of items) {
      if (seen.has(e.enumValue)) {
        ElMessage.warning(`Duplicate enum value: ${e.enumValue}. Each value must be unique.`)
        return
      }
      seen.add(e.enumValue)
    }
  }

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to save alarm rule "${alarmName}"?`,
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
    if (type === 'NUMERIC') {
      const payload: NumericRuleItem = {
        ...(f.id ? { id: f.id } : {}),
        alarmName,
        pointName: f.pointName || currentPoint.value?.pointName,
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

      if (f.id) {
        await alarmApi.updateNumericRule(f.id, payload)
      } else {
        await alarmApi.createNumericRule(payload)
      }
    } else if (type === 'ENUM') {
      const items = enumFormItems.value.filter(e => e.enumValue !== null && e.enumValue !== undefined && String(e.enumValue).trim() !== '')
      const payload: EnumRuleItem[] = items.map(e => ({
        ...e,
        alarmName,
        pointType: 'ENUM',
        pointName: f.pointName || currentPoint.value?.pointName,
      }))

      if (isEdit.value) {
        await alarmApi.updateEnumRules(alarmName, payload)
      } else {
        await alarmApi.createEnumRules(payload)
      }
    }

    // Automatically bind alarm rule to current point template if not bound or updated
    if (currentPoint.value && currentPoint.value.pointTmplId) {
      if (currentPoint.value.alarmCode !== alarmName) {
        await templateApi.updatePoint(currentPoint.value.pointTmplId, {
          ...currentPoint.value,
          alarmCode: alarmName,
        })
      }
    }

    ElMessage.success('Alarm rule saved successfully')
    dialogVisible.value = false
    emit('saved')
  } catch (e) {
    console.error('Failed to save alarm rule:', e)
    ElMessage.error('Failed to save alarm rule')
  } finally {
    saving.value = false
  }
}

defineExpose({
  open,
})
</script>

<style scoped lang="scss">
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

:deep(.el-form-item__label) {
  padding-right: 4px;
}

:deep(.el-divider--horizontal) { margin: 12px 0; }

.type-hint {
  padding: 10px 12px;
  color: $text-muted;
  font-size: 10px;
  text-align: center;
}

.enum-item {
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed $border-color;
  &:last-child { border-bottom: none; }
}

:deep(.el-divider__text) { background-color: $bg-card; color: $text-secondary; }
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
