import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import ts from 'typescript'

test('deployment preview uses a concise confirm label and hierarchical rows', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const helperPath = fileURLToPath(new URL('../src/utils/deploymentImportPreview.ts', import.meta.url))
  const source = await readFile(componentPath, 'utf8')
  const helperSource = await readFile(helperPath, 'utf8')

  assert.doesNotMatch(source, /Confirm Import/)
  assert.match(source, /@click="confirmDeploymentImport"/)
  assert.match(source, /buildDeploymentPreviewTree/)
  assert.match(source, /row-key="id"/)
  assert.match(source, /default-expand-all/)
  assert.match(source, /children:\s*'children'/)
  assert.match(source, /Import Preview/)
  assert.match(source, /preview-section/)

  const compiledHelper = ts.transpileModule(helperSource, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText
  const moduleUrl = 'data:text/javascript;charset=utf-8,' + encodeURIComponent(compiledHelper)
  const { buildDeploymentPreviewTree } = await import(moduleUrl)

  const result = buildDeploymentPreviewTree([
    {
      sourceFile: 'a.csv',
      deviceName: 'Collector A',
      devicePath: 'Collector_A',
      systemName: 'A1_MVP_A1/MVP_A1',
      templateName: 'Collector',
      pointCount: 2,
      action: 'CREATE',
    },
    {
      sourceFile: 'b.csv',
      deviceName: 'Collector B',
      devicePath: 'Collector_B',
      systemName: 'A1_MVP_A1/MVP_A1',
      templateName: 'Collector',
      pointCount: 3,
      action: 'REUSE',
    },
  ])

  assert.equal(result.length, 1)
  assert.equal(result[0].rowType, 'system')
  assert.equal(result[0].systemName, 'A1_MVP_A1/MVP_A1')
  assert.equal(result[0].deviceCount, 2)
  assert.equal(result[0].pointCount, 5)
  assert.equal(result[0].children.length, 2)
  assert.equal(result[0].children[0].rowType, 'device')
  assert.equal(result[0].children[1].deviceName, 'Collector B')
})
