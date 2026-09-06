import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('preserves visual indentation for nested navigation nodes', async () => {
  const componentPath = fileURLToPath(new URL('../src/components/LeftSidebar.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  assert.match(source, /:indent="16"/, 'the navigation tree must define a visible indent per level')

  const treeContentRule = source.match(
    /:deep\(\.el-tree-node__content\)\s*\{([\s\S]*?)\n\s*\}\s*\n\s*:deep\(\.el-tree-node\.is-current/,
  )
  assert.ok(treeContentRule, 'the tree node content style must be present')
  assert.match(treeContentRule[1], /padding-right:\s*4px\s*!important/)
  assert.doesNotMatch(treeContentRule[1], /(^|\n)\s*padding\s*:/m)
  assert.doesNotMatch(treeContentRule[1], /(^|\n)\s*padding-left\s*:/m)
})
