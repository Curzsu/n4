import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const logViewSource = readFileSync(new URL('../src/views/LogView.vue', import.meta.url), 'utf8')

test('keeps the audit log Module column wide enough for module names', () => {
  assert.match(
    logViewSource,
    /<el-table-column\s+prop="module"\s+label="Module"\s+width="140"/,
  )
})
