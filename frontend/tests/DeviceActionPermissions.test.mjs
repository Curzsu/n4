import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

const templatePermissionCodes = [
  'device:template:add',
  'device:template:edit',
  'device:template:delete',
  'device:template:import',
  'device:template:export',
  'device:template:point:add',
  'device:template:point:edit',
  'device:template:point:delete',
  'device:template:faceplate:view',
]

const deploymentPermissionCodes = [
  'device:deploy:group:add',
  'device:deploy:group:edit',
  'device:deploy:group:delete',
  'device:deploy:add',
  'device:deploy:edit',
  'device:deploy:delete',
  'device:deploy:mount',
  'device:deploy:unmount',
  'device:deploy:import',
  'device:deploy:export',
  'device:deploy:alarm:edit',
]

test('binds every device template operation to a permission', async () => {
  const viewPath = fileURLToPath(new URL('../src/views/device-deploy/ProductManagerView.vue', import.meta.url))
  const source = await readFile(viewPath, 'utf8')

  for (const permissionCode of templatePermissionCodes) {
    assert.match(source, permissionPattern(permissionCode), `Missing template permission: ${permissionCode}`)
  }
})

test('binds every device deployment operation to a permission', async () => {
  const viewPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(viewPath, 'utf8')

  for (const permissionCode of deploymentPermissionCodes) {
    assert.match(source, permissionPattern(permissionCode), `Missing deployment permission: ${permissionCode}`)
  }
})

function permissionPattern(permissionCode) {
  return new RegExp(`v-permission=["'](?:'|")${permissionCode}(?:'|")["']`)
}
