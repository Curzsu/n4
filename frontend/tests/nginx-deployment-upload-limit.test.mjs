import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('Nginx API proxy accepts deployment import batches up to the backend request limit', async () => {
  const configPath = fileURLToPath(new URL('../nginx.conf', import.meta.url))
  const source = await readFile(configPath, 'utf8')
  const apiLocation = source.match(/location \/api\/ \{[\s\S]*?\n    \}/)?.[0]

  assert.ok(apiLocation, 'the API proxy location must exist')
  assert.match(apiLocation, /client_max_body_size\s+300m;/)
})
