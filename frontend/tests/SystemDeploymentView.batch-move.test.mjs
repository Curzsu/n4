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

  // Check 4-tier domain model semantics (Level 1 -> Cabins, Level 2 -> Collectors)
  assert.ok(source.includes('childSectionTitle'), 'Must define childSectionTitle')
  assert.ok(source.includes('addChildButtonText'), 'Must define addChildButtonText')
  assert.ok(source.includes('childNameColumnLabel'), 'Must define childNameColumnLabel')
  assert.ok(source.includes('emptyChildText'), 'Must define emptyChildText')
})

test('BatchMoveNavNodesDialog handles anti-cycle, max level, and API invocation', async () => {
  const dialogPath = fileURLToPath(new URL('../src/views/device-deploy/dialogs/BatchMoveNavNodesDialog.vue', import.meta.url))
  const source = await readFile(dialogPath, 'utf8')

  // Verify cascader and API bindings
  assert.ok(source.includes('targetCascaderOptions'), 'Must define targetCascaderOptions computed property')
  assert.ok(source.includes('checkStrictly: true'), 'Cascader must allow selecting any group level')
  assert.ok(source.includes('dashboardNavApi.batchMoveNodes'), 'Must invoke dashboardNavApi.batchMoveNodes')
  assert.ok(source.includes("movingNodeType = computed<'cabin' | 'collector' | 'group'>"), 'Must determine node type based on level')
  assert.ok(source.includes('isMovingCabins'), 'Must handle cabin moving constraints')

  // Simulate target options filtering logic matching BatchMoveNavNodesDialog
  function getSubtreeDepth(node) {
    if (!node.children || node.children.length === 0) return 0
    return 1 + Math.max(...node.children.map(getSubtreeDepth))
  }

  function filterTargetOptions(nodes, movingNodes, currentParentId) {
    const isMovingCabins = movingNodes.length > 0 && movingNodes.every(n => n.nodeLevel === 2)
    const isMovingCollectors = movingNodes.length > 0 && movingNodes.every(n => n.nodeLevel === 3)
    const movingIds = new Set(movingNodes.map(n => n.nodeId))

    if (isMovingCabins) {
      const res = []
      for (const n of nodes || []) {
        if (movingIds.has(n.nodeId)) continue
        if (n.nodeLevel === 1) {
          const isCurrentParent = currentParentId !== null && n.nodeId === currentParentId
          res.push({
            nodeId: n.nodeId,
            nodeName: n.nodeName,
            nodeLevel: n.nodeLevel,
            disabled: isCurrentParent,
          })
        }
      }
      return res
    }

    if (isMovingCollectors) {
      const res = []
      for (const rootNode of nodes || []) {
        if (movingIds.has(rootNode.nodeId)) continue
        const cabinChildren = []
        for (const cabin of rootNode.children || []) {
          if (movingIds.has(cabin.nodeId)) continue
          if (cabin.nodeLevel === 2) {
            const isCurrentParent = currentParentId !== null && cabin.nodeId === currentParentId
            cabinChildren.push({
              nodeId: cabin.nodeId,
              nodeName: cabin.nodeName,
              nodeLevel: cabin.nodeLevel,
              disabled: isCurrentParent,
            })
          }
        }
        if (cabinChildren.length > 0) {
          res.push({
            nodeId: rootNode.nodeId,
            nodeName: rootNode.nodeName,
            nodeLevel: rootNode.nodeLevel,
            disabled: true,
            children: cabinChildren,
          })
        }
      }
      return res
    }

    const maxSubtreeDepth = Math.max(...movingNodes.map(getSubtreeDepth), 0)

    const build = (list, parentMaxLevel) => {
      const res = []
      for (const n of list) {
        if (movingIds.has(n.nodeId)) continue
        const maxL = n.maxLevel ?? parentMaxLevel ?? 3
        const isCurrentParent = currentParentId !== null && n.nodeId === currentParentId

        const canFolderAcceptSubtree = n.nodeLevel + 1 + maxSubtreeDepth <= maxL
        const validChildren = n.children && n.children.length > 0 ? build(n.children, maxL) : []
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

  // Scenario 1: Move Cabin (Level 2) - should strictly only allow Level 1 root nodes
  const movingCabin = [sampleTree[0].children[0]] // A1_MVP_A1 (level 2)
  const cabinOptions = filterTargetOptions(sampleTree, movingCabin, 1)

  // Only level 1 nodes returned
  assert.equal(cabinOptions.length, 2)
  assert.equal(cabinOptions.every(o => o.nodeLevel === 1), true)
  assert.equal(cabinOptions.find(o => o.nodeId === 1).disabled, true) // current parent
  assert.equal(cabinOptions.find(o => o.nodeId === 2).disabled, false) // target root
  assert.equal(cabinOptions[0].children, undefined) // No level 2 or 3 nested targets for cabins

  // Scenario 2: Move Collector (Level 3) - target must strictly be Level 2 Cabins, Level 3 is omitted
  const movingCollector = [sampleTree[0].children[0].children[0]] // MVP_A1 (level 3)
  const collectorOptions = filterTargetOptions(sampleTree, movingCollector, 10)
  const mvp1Node = collectorOptions.find(o => o.nodeId === 1)
  assert.ok(mvp1Node.children.length > 0)
  assert.equal(mvp1Node.disabled, true) // Level 1 is disabled as parent for collectors
  // Cabin A1_MVP_B1 (nodeId: 11, level 2) is a valid selectable target
  const cabinB1 = mvp1Node.children.find(c => c.nodeId === 11)
  assert.equal(cabinB1.disabled, false)
  // None of the cabins in collectorOptions should have children (Level 3 nodes stripped)
  assert.equal(mvp1Node.children.some(c => Boolean(c.children && c.children.length > 0)), false)
})

test('dashboardNavApi provides batchMoveNodes endpoint', async () => {
  const apiPath = fileURLToPath(new URL('../src/api/dashboardNav.ts', import.meta.url))
  const source = await readFile(apiPath, 'utf8')

  assert.ok(source.includes('batchMoveNodes('), 'Must define batchMoveNodes')
  assert.ok(source.includes("'/api/dashboard/nav/nodes/batch-move'"), 'Must post to /api/dashboard/nav/nodes/batch-move')
})
