import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('deployment preview keeps flat rows and a concise confirm label', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  assert.doesNotMatch(source, /Confirm Import/)
  assert.match(source, /@click="confirmDeploymentImport"/)
  assert.match(source, /:data="deploymentImportResult\.deploymentPreviews"/)
  assert.match(source, /<el-table-column type="index" label="#"/)
  assert.doesNotMatch(source, /buildDeploymentPreviewTree/)
  assert.doesNotMatch(source, /row-key="id"/)
  assert.doesNotMatch(source, /default-expand-all/)
  assert.doesNotMatch(source, /preview-result-header/)
  assert.doesNotMatch(source, /preview-section/)
  assert.match(source, /@click="closeDeploymentImportDialog">Cancel<\/el-button>/)
  assert.match(source, /scheduleDeploymentPreview\(/)
})
