import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('deployment import exposes recursive folder selection for CSV files', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  assert.match(source, /Select Folder/)
  assert.match(source, /webkitdirectory/)
  assert.match(source, /multiple/)
  assert.match(source, /@drop\.capture="handleDeploymentDrop"/)
  assert.match(source, /function handleDeploymentDrop/)
  assert.match(source, /webkitGetAsEntry\(\)/)
  assert.match(source, /reader\.readEntries/)
  assert.match(source, /function deduplicateDeploymentFiles/)
  assert.match(source, /deduplicateDeploymentFiles\(csvFiles\)/)
  assert.match(source, /handleDeploymentFolderChange/)
  assert.match(source, /toLowerCase\(\)\.endsWith\(['"]\.csv['"]\)/)
  assert.match(source, /No CSV files found in the selected folder/)
  assert.match(source, /New\/reused counts are based on CSV business keys/)
  assert.match(source, /not folder names/)
  assert.match(source, /Importing deployment data does not create navigation nodes/)
  assert.match(source, /Use Add Child Group for new sidebar folders/)
})
