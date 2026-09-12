import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('SystemDeploymentView includes Child Groups selection and Batch Move integration', async () => {
  const viewPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(viewPath, 'utf8')

  // Check template elements
  assert.ok(source.includes('Batch Move'), 'Must include Batch Move button in Child Groups header')
  assert.ok(source.includes('Exit Move'), 'Must include Exit Move button')
  assert.ok(source.indexOf('Move<template') < source.indexOf('Exit Move'), 'Move button must appear on the left of Exit Move')
  assert.ok(source.includes("v-permission=\"'device:deploy:group:edit'\""), 'Batch Move button must require group:edit permission')
  assert.ok(source.includes('v-if="isGroupBatchMoving" type="selection"'), 'Child Groups table must show selection column only during batch move mode')
  assert.ok(source.includes('@selection-change="handleChildGroupSelectionChange"'), 'Must bind selection change handler')
  assert.ok(source.includes('<BatchMoveNavNodesDialog ref="batchMoveDialogRef"'), 'Must mount BatchMoveNavNodesDialog')

  // Check script logic
  assert.ok(source.includes('isGroupBatchMoving = ref(false)'), 'Initial state must hide checkboxes')
  assert.ok(source.includes('enterGroupBatchMove'), 'Must define enterGroupBatchMove')
  assert.ok(source.includes('exitGroupBatchMove'), 'Must define exitGroupBatchMove')
  assert.ok(source.includes('handleBatchMoveButtonClick'), 'Must define handleBatchMoveButtonClick')
  assert.ok(source.includes('openBatchMoveDialog'), 'Must define openBatchMoveDialog')
  assert.ok(source.includes('handleBatchMoveSuccess'), 'Must define handleBatchMoveSuccess')
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

    const build = (list, parentMaxLevel) => {
      const res = []
      for (const n of list) {
        if (movingIds.has(n.nodeId)) continue
        const maxL = n.maxLevel ?? parentMaxLevel ?? 3
        const isCurrentParent = currentParentId !== null && n.nodeId === currentParentId
        const isLeaf = !n.children || n.children.length === 0

        if (isLeaf) {
          if (n.nodeLevel > maxL) continue
          const label = n.deviceCount != null ? `${n.nodeName} (${n.deviceCount})` : n.nodeName
          res.push({
            nodeId: n.nodeId,
            nodeName: label,
            nodeLevel: n.nodeLevel,
            disabled: isCurrentParent,
          })
          continue
        }

        const canFolderAcceptSubtree = n.nodeLevel + 1 + maxSubtreeDepth <= maxL
        const validChildren = build(n.children, maxL)
        if (!canFolderAcceptSubtree && validChildren.length === 0) continue

        const item = {
          nodeId: n.nodeId,
          nodeName: n.nodeName,
          nodeLevel: n.nodeLevel,
          disabled: isCurrentParent || !canFolderAcceptSubtree,
        }
        if (validChildren.length > 0) {
          item.children = validChildren
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
      children: [
        { nodeId: 20, nodeName: 'A2_MVP_A1', nodeLevel: 2, children: [] }
      ]
    }
  ]

  // Move A1_MVP_A1 (nodeId: 10, has child MVP_A1) currently under A1_MVP (nodeId: 1)
  const moving = [sampleTree[0].children[0]]
  const options = filterTargetOptions(sampleTree, moving, 1)

  // A1_MVP (nodeId: 1) should be disabled because it is current parent
  assert.equal(options.find(o => o.nodeId === 1).disabled, true)

  // A1_MVP_A1 (nodeId: 10) and child MVP_A1 (100) must be excluded entirely
  assert.equal(Boolean(options[0].children?.some(c => c.nodeId === 10 || c.nodeId === 100)), false)

  // A2_MVP (nodeId: 2) is level 1, maxLevel 3, child subtree depth is 1 (1 + 1 + 1 = 3 <= 3) -> valid and enabled
  const a2Option = options.find(o => o.nodeId === 2)
  assert.equal(a2Option.disabled, false)

  // Level 3 leaf nodes (like MVP_A1) are selectable targets for merging devices
  const movingSingle = [{ nodeId: 999, nodeName: 'SingleCabin', nodeLevel: 2, children: [] }]
  const singleMoveOptions = filterTargetOptions(sampleTree, movingSingle, null)
  const mvpNode = singleMoveOptions.find(o => o.nodeId === 1)
  const mvpA1Option = mvpNode.children.find(c => c.nodeId === 10)
  // MVP_A1 (level 3) is included as a leaf target for device merging
  assert.ok(mvpA1Option.children.some(c => c.nodeId === 100 && c.nodeName === 'MVP_A1'))
})

test('dashboardNavApi provides batchMoveNodes endpoint', async () => {
  const apiPath = fileURLToPath(new URL('../src/api/dashboardNav.ts', import.meta.url))
  const source = await readFile(apiPath, 'utf8')

  assert.ok(source.includes('batchMoveNodes('), 'Must define batchMoveNodes')
  assert.ok(source.includes("'/api/dashboard/nav/nodes/batch-move'"), 'Must post to /api/dashboard/nav/nodes/batch-move')
})
