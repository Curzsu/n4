import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('SystemDeploymentView declares selectedNode before cabin setup uses it', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')
  const script = source.slice(source.indexOf('<script setup'), source.indexOf('</script>'))

  const selectedNodeDeclaration = script.indexOf('const selectedNode = computed')
  const cabinComputed = script.indexOf('const cabinImageUrl = computed')
  const cabinWatcher = script.indexOf('watch(() => selectedNode.value?.nodeId')

  assert.ok(selectedNodeDeclaration >= 0, 'selectedNode declaration must exist')
  assert.ok(cabinComputed >= 0, 'cabin image computed state must exist')
  assert.ok(cabinWatcher >= 0, 'cabin zoom watcher must exist')
  assert.ok(selectedNodeDeclaration < cabinComputed, 'selectedNode must be declared before cabin computed state')
  assert.ok(selectedNodeDeclaration < cabinWatcher, 'selectedNode must be declared before cabin watcher')
})
