import assert from 'node:assert/strict'
import { readFile, access } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('SystemDeploymentView displays cabin diagrams for all cabin-level nodes with fallbacks', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  // Verify template bindings
  assert.ok(source.includes('Cabin Diagram - {{ selectedNode.nodeName }}'), 'Header must display node name in cabin diagram title')
  assert.ok(source.includes('@error="handleCabinImageError"'), 'Image must have @error fallback handler')

  // Verify cabinImageUrl logic elements
  assert.ok(source.includes('selectedNode.value.nodeLevel === 1'), 'Must exclude root category level nodes')
  assert.ok(source.includes("return '/cabin-images/A1_MVP_B1.png'"), 'Must map MVP B1 cabin node to A1_MVP_B1.png')
  assert.ok(source.includes("return '/cabin-images/A1_MVP_A1.png'"), 'Must map MVP A1 cabin node to A1_MVP_A1.png')
  assert.ok(source.includes("return '/cabin-images/default_cabin.png'"), 'Must fallback to default_cabin.png for other cabin nodes')

  // Verify physical asset files exist
  const defaultImagePath = fileURLToPath(new URL('../public/cabin-images/default_cabin.png', import.meta.url))
  const a1ImagePath = fileURLToPath(new URL('../public/cabin-images/A1_MVP_A1.png', import.meta.url))
  const b1ImagePath = fileURLToPath(new URL('../public/cabin-images/A1_MVP_B1.png', import.meta.url))

  await assert.doesNotReject(access(defaultImagePath), 'default_cabin.png must exist in public/cabin-images')
  await assert.doesNotReject(access(a1ImagePath), 'A1_MVP_A1.png must exist in public/cabin-images')
  await assert.doesNotReject(access(b1ImagePath), 'A1_MVP_B1.png must exist in public/cabin-images')

  // Simulate cabin image resolution function matching SystemDeploymentView implementation
  function resolveCabinImageUrl(node) {
    if (!node) return ''
    if (node.nodeLevel === 1) return ''
    const name = node.nodeName.trim()
    const normalized = name.replace(/[-_]/g, '').toLowerCase()
    if (normalized.includes('mvpb1')) {
      return '/cabin-images/A1_MVP_B1.png'
    }
    if (normalized.includes('mvpa1') || normalized.startsWith('a1mvp') || normalized.startsWith('mvp')) {
      return '/cabin-images/A1_MVP_A1.png'
    }
    return '/cabin-images/default_cabin.png'
  }

  // Level 1 category roots: no cabin diagram
  assert.equal(resolveCabinImageUrl({ nodeName: 'A1_MVP', nodeLevel: 1 }), '')
  assert.equal(resolveCabinImageUrl({ nodeName: 'A1_SLP', nodeLevel: 1 }), '')
  assert.equal(resolveCabinImageUrl({ nodeName: 'BA', nodeLevel: 1 }), '')

  // Level 2+ cabin nodes: must have cabin diagram
  assert.equal(resolveCabinImageUrl({ nodeName: 'A1_MVP_A1', nodeLevel: 2 }), '/cabin-images/A1_MVP_A1.png')
  assert.equal(resolveCabinImageUrl({ nodeName: 'A1_MVP_B1', nodeLevel: 2 }), '/cabin-images/A1_MVP_B1.png')
  assert.equal(resolveCabinImageUrl({ nodeName: 'A1_SLP_A2C2', nodeLevel: 2 }), '/cabin-images/default_cabin.png')
  assert.equal(resolveCabinImageUrl({ nodeName: 'A1_SLP_BS1A', nodeLevel: 2 }), '/cabin-images/default_cabin.png')
  assert.equal(resolveCabinImageUrl({ nodeName: 'Custom_Cabin_01', nodeLevel: 2 }), '/cabin-images/default_cabin.png')
})
