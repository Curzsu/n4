import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('Audit Logs exposes Alarm-style filters on query-backed table headers', async () => {
  const viewPath = fileURLToPath(new URL('../src/views/LogView.vue', import.meta.url))
  const mapperPath = fileURLToPath(new URL('../../monitor-backend/src/main/resources/mapper/SysOperationLogMapper.xml', import.meta.url))
  const viewSource = await readFile(viewPath, 'utf8')
  const mapperSource = await readFile(mapperPath, 'utf8')

  for (const field of ['operatorName', 'module', 'operation', 'status', 'createTime']) {
    assert.match(viewSource, new RegExp(`v-model:visible="filterPopover\\.${field}"`))
    assert.match(viewSource, new RegExp(`hasFilter\\('${field}'\\)`))
  }

  assert.match(viewSource, /placeholder="Fuzzy match"/)
  assert.match(viewSource, /<el-select[\s\S]*v-model="draft\.status"/)
  assert.match(viewSource, /<el-date-picker[\s\S]*type="datetimerange"/)
    assert.match(viewSource, /@click="resetFilter\('operatorName'\)"/)
  assert.match(viewSource, /@click="cancelFilter\('operatorName'\)"/)
  assert.match(viewSource, /@click="applyFilter\('operatorName'\)"/)
  assert.match(viewSource, /class="audit-log-filter-popper"/)
  assert.match(viewSource, /function applyFilter\(field: LogFilterField\)/)
  assert.match(viewSource, /function syncDraft\(field: LogFilterField\)/)
  assert.match(mapperSource, /AND module LIKE CONCAT\('%', #\{module\}, '%'\)/)
})
