import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('deployment preview exposes new navigation group counts and names', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  assert.match(source, /navigationGroupCreated/)
  assert.match(source, /navigationGroupNames/)
  assert.match(source, /Navigation groups:/)
})
