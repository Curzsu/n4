# System Account Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove account information from the top-right navigation and move password change and logout into the System second-level menu.

**Architecture:** Keep `TopNavbar` focused on global navigation, alarm badges, and search. Reuse the existing `ChangePasswordDialog` and move account actions into `BottomToolbar`, where System-only navigation already lives. Preserve the existing authentication API calls and route behavior.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Node.js built-in test runner, Vite.

---

## Files and responsibilities

- Modify `frontend/src/components/TopNavbar.vue`: remove the account dropdown, account display computed state, password dialog mount, and account command handler.
- Modify `frontend/src/components/BottomToolbar.vue`: add System actions, mount `ChangePasswordDialog`, and reuse the existing logout confirmation flow.
- Create `frontend/tests/TopNavbar.system-account.test.mjs`: statically verify that account operations are absent from the top navbar and present in the System toolbar.
- Create `docs/superpowers/specs/2026-08-29-system-account-navigation-design.md`: record the approved UI design.

### Task 1: Add the failing navigation regression test

**Files:**
- Create: `frontend/tests/TopNavbar.system-account.test.mjs`

- [x] **Step 1: Write the failing test**

```js
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('account actions are exposed from System instead of the top navbar', async () => {
  const topNavbarPath = fileURLToPath(new URL('../src/components/TopNavbar.vue', import.meta.url))
  const bottomToolbarPath = fileURLToPath(new URL('../src/components/BottomToolbar.vue', import.meta.url))
  const topNavbar = await readFile(topNavbarPath, 'utf8')
  const bottomToolbar = await readFile(bottomToolbarPath, 'utf8')

  assert.doesNotMatch(
    topNavbar,
    /el-dropdown|ChangePasswordDialog|displayName|handleUserCommand|UserFilled|ArrowDown|user-profile/,
  )

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
```

- [x] **Step 2: Run the test to verify it fails for the missing behavior**

Run: `npm test -- tests/TopNavbar.system-account.test.mjs`

Expected: FAIL because the current `TopNavbar.vue` still contains `el-dropdown` and `ChangePasswordDialog`, while `BottomToolbar.vue` does not yet contain the password dialog or logout action.

### Task 2: Move account actions into the System toolbar

**Files:**
- Modify: `frontend/src/components/TopNavbar.vue`
- Modify: `frontend/src/components/BottomToolbar.vue`

- [x] **Step 1: Remove the top-navbar account UI and logic**

Delete the `el-dropdown` block and `ChangePasswordDialog` instance from the template. Remove the `ref` import, `UserFilled` and `ArrowDown` icon imports, the `ElMessageBox` and `ElMessage` imports, the `useAuthStore` import and instance, the `ChangePasswordDialog` import, the `changePasswordVisible` state, the `displayName` computed value, and `handleUserCommand`. Remove the `.user-profile` style block.

- [x] **Step 2: Add System-only password and logout actions**

Import `ref`, `ElMessageBox`, `ElMessage`, `useAuthStore`, and `ChangePasswordDialog` in `BottomToolbar.vue`. Add `changePasswordVisible` state and the auth store. Add `Change Password` and `Logout` entries to `systemItems` with action identifiers instead of routes. Render the dialog from `BottomToolbar` and handle those identifiers in `onItemClick`, preserving the existing logout confirmation, success message, state cleanup, and `/login` redirect.

- [x] **Step 3: Run the focused regression test**

Run: `npm test -- tests/TopNavbar.system-account.test.mjs`

Expected: PASS with one test and zero failures.

### Task 3: Verify the frontend artifact

**Files:**
- Verify: `frontend/src/components/TopNavbar.vue`
- Verify: `frontend/src/components/BottomToolbar.vue`
- Verify: `frontend/tests/TopNavbar.system-account.test.mjs`

- [x] **Step 1: Run all frontend tests**

Run: `npm test`

Expected: PASS with zero failures.

- [x] **Step 2: Run the production build**

Run: `npm run build`

Expected: exit code 0 with a generated Vite production bundle.

- [x] **Step 3: Review the final diff**

Run: `git diff --check; git diff -- frontend/src/components/TopNavbar.vue frontend/src/components/BottomToolbar.vue frontend/tests/TopNavbar.system-account.test.mjs`

Expected: no whitespace errors; the diff contains only the requested navigation relocation and its regression test.
