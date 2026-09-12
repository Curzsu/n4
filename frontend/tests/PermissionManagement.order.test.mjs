import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import ts from 'typescript'

test('renders permissions with stable hierarchical order labels', async () => {
  const viewPath = fileURLToPath(new URL('../src/views/system/PermissionManagementView.vue', import.meta.url))
  const helperPath = fileURLToPath(new URL('../src/utils/permissionTree.ts', import.meta.url))
  const servicePath = fileURLToPath(new URL('../../monitor-backend/src/main/java/com/n4monitor/service/SysPermissionService.java', import.meta.url))
  const viewSource = await readFile(viewPath, 'utf8')
  const serviceSource = await readFile(servicePath, 'utf8')

  assert.match(viewSource, /buildPermissionTreeView\(await permissionApi\.tree\(\)\)/)
  assert.doesNotMatch(viewSource, /class="permission-order"/)
  assert.doesNotMatch(viewSource, /row\.displayOrder/)
  assert.match(viewSource, /default-expand-all/)
  assert.match(serviceSource, /Comparator\.comparing\(PermissionTreeDto::getSortOrder/)
  assert.match(serviceSource, /thenComparing\(PermissionTreeDto::getPermId/)
  assert.match(serviceSource, /sortPermissionTree\(/)

  const helperSource = await readFile(helperPath, 'utf8')
  const compiledHelper = ts.transpileModule(helperSource, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText
  const { buildPermissionTreeView } = await import(`data:text/javascript;charset=utf-8,${encodeURIComponent(compiledHelper)}`)

  const result = buildPermissionTreeView([
    {
      permId: 20,
      permName: 'Device Management',
      permCode: 'device',
      permType: 1,
      parentId: 0,
      sortOrder: 2,
      status: 1,
      children: [],
    },
    {
      permId: 10,
      permName: 'User Management',
      permCode: 'user',
      permType: 1,
      parentId: 0,
      sortOrder: 1,
      status: 1,
      children: [
        {
          permId: 102,
          permName: 'Edit User',
          permCode: 'user:edit',
          permType: 2,
          parentId: 10,
          sortOrder: 2,
          status: 1,
          children: [],
        },
        {
          permId: 101,
          permName: 'Add User',
          permCode: 'user:add',
          permType: 2,
          parentId: 10,
          sortOrder: 1,
          status: 1,
          children: [
            {
              permId: 1011,
              permName: 'Confirm Add User',
              permCode: 'user:add:confirm',
              permType: 3,
              parentId: 101,
              sortOrder: 1,
              status: 1,
              children: [],
            },
          ],
        },
      ],
    },
  ])

  assert.deepEqual(result.map((node) => [node.permName, node.displayOrder]), [
    ['User Management', '1'],
    ['Device Management', '2'],
  ])
  assert.deepEqual(result[0].children.map((node) => [node.permName, node.displayOrder]), [
    ['Add User', '1.1'],
    ['Edit User', '1.2'],
  ])
  assert.equal(result[0].children[0].children[0].displayOrder, '1.1.1')
})

test('resolves every catalog menu icon without a misleading lock fallback', async () => {
  const viewPath = fileURLToPath(new URL('../src/views/system/PermissionManagementView.vue', import.meta.url))
  const catalogPath = fileURLToPath(new URL('../../monitor-backend/src/main/java/com/n4monitor/config/RbacPermissionCatalog.java', import.meta.url))
  const viewSource = await readFile(viewPath, 'utf8')
  const catalogSource = await readFile(catalogPath, 'utf8')
  const iconNames = [...catalogSource.matchAll(/menu\([^\n]+"([A-Z][A-Za-z0-9]+)", \d+\)/g)]
    .map((match) => match[1])

  assert.ok(iconNames.length > 0)
  for (const iconName of new Set(iconNames)) {
    assert.match(viewSource, new RegExp(`\\b${iconName}\\b`), `Missing icon mapping: ${iconName}`)
  }
  assert.doesNotMatch(viewSource, /<Lock v-else/)
})
