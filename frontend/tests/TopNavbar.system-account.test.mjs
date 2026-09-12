import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('displays the current administrator name without account actions', async () => {
  const topNavbarPath = fileURLToPath(new URL('../src/components/TopNavbar.vue', import.meta.url))
  const bottomToolbarPath = fileURLToPath(new URL('../src/components/BottomToolbar.vue', import.meta.url))
  const leftSidebarPath = fileURLToPath(new URL('../src/components/LeftSidebar.vue', import.meta.url))
  const topNavbar = await readFile(topNavbarPath, 'utf8')
  const bottomToolbar = await readFile(bottomToolbarPath, 'utf8')
  const leftSidebar = await readFile(leftSidebarPath, 'utf8')

  assert.match(topNavbar, /<span class="admin-name">\{\{ displayName \}\}<\/span>/)
  assert.match(topNavbar, /const authStore = useAuthStore\(\)/)
  assert.match(
    topNavbar,
    /const displayName = computed\(\(\) => authStore\.currentUser\?\.realName \|\| authStore\.currentUser\?\.username \|\| ''\)/,
  )
  assert.doesNotMatch(
    topNavbar,
    /el-dropdown|ChangePasswordDialog|handleUserCommand|UserFilled|ArrowDown|user-profile|ElMessageBox|ElMessage/,
  )
  assert.doesNotMatch(topNavbar, /<span class="admin-name"[^>]*(?:@click|@command|v-on:)/)

  const systemItemsStart = bottomToolbar.indexOf('const systemItems = computed')
  const defaultItemsStart = bottomToolbar.indexOf('// Items shown on all other routes')
  assert.ok(systemItemsStart >= 0, 'System menu definition must exist')
  assert.ok(defaultItemsStart > systemItemsStart, 'Default menu must follow the System menu')

  const systemMenu = bottomToolbar.slice(systemItemsStart, defaultItemsStart)
  assert.match(systemMenu, /label: 'Device Template'/)
  assert.match(systemMenu, /label: 'Device Deployment'/)
  assert.match(systemMenu, /label: 'Alarm Configuration'/)
  assert.match(systemMenu, /label: 'Authority Management'/)
  assert.doesNotMatch(systemMenu, /label: 'Change Password'|label: 'Logout'|label: 'Audit Logs'/)

  assert.match(leftSidebar, /label: 'Audit Logs'/)
  assert.match(leftSidebar, /label: 'Change Password'/)
  assert.match(leftSidebar, /label: 'Logout'/)
})
