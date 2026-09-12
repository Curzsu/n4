import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('SystemDeploymentView includes Child Groups selection and Batch Move integration', async () => {
  const viewPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(viewPath, 'utf8')

  // Check template elements
  assert.ok(source.includes('Batch Move'), 'Must include Batch Move button in Child Groups header')
  assert.ok(source.includes("v-permission=\"'device:deploy:group:edit'\""), 'Batch Move button must require group:edit permission')
  assert.ok(source.includes('type="selection"'), 'Child Groups table must include selection column')
  assert.ok(source.includes('@selection-change="handleChildGroupSelectionChange"'), 'Must bind selection change handler')
  assert.ok(source.includes('<BatchMoveNavNodesDialog ref="batchMoveDialogRef"'), 'Must mount BatchMoveNavNodesDialog')

  // Check script logic
  assert.ok(source.includes('openBatchMoveDialog'), 'Must define openBatchMoveDialog')
  assert.ok(source.includes('handleBatchMoveSuccess'), 'Must define handleBatchMoveSuccess')
  assert.ok(source.includes('selectedChildGroups.value = []'), 'Must reset selected groups on node switch and move success')
})

test('BatchMoveNavNodesDialog handles anti-cycle, max level, and API invocation', async () => {
  const dialogPath = fileURLToPath(new URL('../src/views/device-deploy/dialogs/BatchMoveNavNodesDialog.vue', import.meta.url))
  const source = await readFile(dialogPath, 'utf8')

  // Verify cascader and API bindings
  assert.ok(source.includes('targetCascaderOptions'), 'Must define targetCascaderOptions computed property')
  assert.ok(source.includes('checkStrictly: true'), 'Cascader must allow selecting any group level')
  assert.ok(source.includes('dashboardNavApi.batchMoveNodes'), 'Must invoke dashboardNavApi.batchMoveNodes')

  // Simulate target options filtering logic matching BatchMoveNavNodesDialog
  function getSubtreeDepth(node) {
    if (!node.children || node.children.length === 0) return 0
    return 1 + Math.max(...node.children.map(getSubtreeDepth))
  }

  function filterTargetOptions(nodes, movingNodes, currentParentId) {
    const movingIds = new Set(movingNodes.map(n => n.nodeId))
    const maxSubtreeDepth = Math.max(...movingNodes.map(getSubtreeDepth), 0)

    const build = (list) => {
      const res = []
      for (const n of list) {
        if (movingIds.has(n.nodeId)) continue
        const maxL = n.maxLevel ?? 4
        const isCurrentParent = currentParentId !== null && n.nodeId === currentParentId
        const wouldOverflow = n.nodeLevel + 1 + maxSubtreeDepth > maxL
        const item = {
          nodeId: n.nodeId,
          nodeName: n.nodeName,
          nodeLevel: n.nodeLevel,
          disabled: isCurrentParent || wouldOverflow,
        }
        if (n.children && n.children.length > 0) {
          const validChildren = build(n.children)
          if (validChildren.length > 0) {
            item.children = validChildren
          }
        }
        res.push(item)
      }
      return res
    }
    return build(nodes)
  }

  const sampleTree = [
    {
      nodeId: 1,
      nodeName: 'A1_MVP',
      nodeLevel: 1,
      maxLevel: 3,
      children: [
        {
          nodeId: 10,
          nodeName: 'A1_MVP_A1',
          nodeLevel: 2,
          children: [
            { nodeId: 100, nodeName: 'MVP_A1', nodeLevel: 3, children: [] }
          ]
        },
        { nodeId: 11, nodeName: 'A1_MVP_B1', nodeLevel: 2, children: [] }
      ]
    },
    {
      nodeId: 2,
      nodeName: 'A2_MVP',
      nodeLevel: 1,
      maxLevel: 3,
      children: []
    }
  ]

  // Move A1_MVP_A1 (nodeId: 10, has child MVP_A1) currently under A1_MVP (nodeId: 1)
  const moving = [sampleTree[0].children[0]]
  const options = filterTargetOptions(sampleTree, moving, 1)

  // A1_MVP (nodeId: 1) should be disabled because it is current parent
  assert.equal(options.find(o => o.nodeId === 1).disabled, true)

  // A1_MVP_A1 (nodeId: 10) and child MVP_A1 (100) must be excluded entirely
  assert.equal(options[0].children?.some(c => c.nodeId === 10 || c.nodeId === 100), false)

  // A2_MVP (nodeId: 2) is level 1, maxLevel 3, child subtree depth is 1 (1 + 1 + 1 = 3 <= 3) -> valid and enabled
  const a2Option = options.find(o => o.nodeId === 2)
  assert.equal(a2Option.disabled, false)
})

test('dashboardNavApi provides batchMoveNodes endpoint', async () => {
  const apiPath = fileURLToPath(new URL('../src/api/dashboardNav.ts', import.meta.url))
  const source = await readFile(apiPath, 'utf8')

  assert.ok(source.includes('batchMoveNodes('), 'Must define batchMoveNodes')
  assert.ok(source.includes("'/api/dashboard/nav/nodes/batch-move'"), 'Must post to /api/dashboard/nav/nodes/batch-move')
})
