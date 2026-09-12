import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('deployment preview presents dedicated navigation tree preview and clean element summary', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  assert.match(source, /Navigation Tree Preview/)
  assert.match(source, /deploymentNavChangeRows/)
  assert.match(source, /nav-tree-badge/)
  assert.match(source, /New Elements/)
  assert.match(source, /label="Existing"/)
  assert.doesNotMatch(source, /category:\s*'Navigation'/)
})
