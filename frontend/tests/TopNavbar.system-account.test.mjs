import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('displays the current administrator name without account actions', async () => {
  const topNavbarPath = fileURLToPath(new URL('../src/components/TopNavbar.vue', import.meta.url))
  const bottomToolbarPath = fileURLToPath(new URL('../src/components/BottomToolbar.vue', import.meta.url))
  const topNavbar = await readFile(topNavbarPath, 'utf8')
  const bottomToolbar = await readFile(bottomToolbarPath, 'utf8')

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
  assert.match(bottomToolbar, /ChangePasswordDialog/)
  assert.match(systemMenu, /label: 'Change Password'/)
  assert.match(systemMenu, /label: 'Logout'/)
  assert.match(systemMenu, /label: 'Audit Logs'/)
})
