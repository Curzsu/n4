# Admin Name Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在主界面右上角显示当前登录管理员的姓名，并保持该区域完全不可交互。

**Architecture:** 复用现有 `auth` store 的 `currentUser` 状态，在 `TopNavbar.vue` 中计算 `realName || username` 并渲染普通文本。不会新增接口，不会修改 `BottomToolbar` 中已有的账户操作，也不会把姓名包装成下拉菜单或点击入口。

**Tech Stack:** Vue 3、TypeScript、Pinia、Element Plus、Vite、Node.js built-in test runner。

---

## 文件职责

- 修改 `frontend/tests/TopNavbar.system-account.test.mjs`：增加顶部姓名展示和取值规则的回归断言，同时保留顶部无账户交互的断言。
- 修改 `frontend/src/components/TopNavbar.vue`：订阅 `auth` store，计算并显示管理员姓名，增加非交互文本样式。
- 不修改 `frontend/src/components/BottomToolbar.vue`：保留现有 System 菜单和账户操作，避免扩大本次范围。

### Task 1: 增加姓名展示回归测试

**Files:**
- Modify: `frontend/tests/TopNavbar.system-account.test.mjs`

- [ ] **Step 1: 写入失败测试**

将现有测试调整为以下完整内容，保留 System 菜单的账户操作断言：

```js
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
```

删除旧断言中会把合法的 `displayName` 判定为错误的 `/displayName/` 匹配项。新增的 `admin-name` 断言还要确保姓名节点没有绑定事件属性。

- [ ] **Step 2: 运行测试确认失败**

运行：`npm test -- tests/TopNavbar.system-account.test.mjs`

预期：测试失败，原因是当前 `TopNavbar.vue` 尚未包含 `admin-name`、`authStore` 和 `displayName` 实现；失败不能来自测试语法错误或测试文件路径错误。

### Task 2: 实现只读姓名展示

**Files:**
- Modify: `frontend/src/components/TopNavbar.vue`

- [ ] **Step 1: 添加姓名文本节点**

在现有搜索图标后追加普通 `span`，不添加 `@click`、`cursor` 或下拉组件：

```vue
<el-icon class="search-icon"><Search /></el-icon>
<span class="admin-name">{{ displayName }}</span>
```

- [ ] **Step 2: 复用认证状态计算显示值**

在现有 import 中增加 `useAuthStore`，初始化 store，并在 `alarmSummary` 之前增加：

```ts
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const displayName = computed(() => authStore.currentUser?.realName || authStore.currentUser?.username || '')
```

该逻辑只读取已有状态；不发起新请求、不修改认证状态。

- [ ] **Step 3: 添加非交互样式**

在 `TopNavbar.vue` 的 scoped style 中增加：

```scss
.admin-name {
  color: $text-secondary;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}
```

不要增加 `cursor`、hover、active 或点击相关样式。

- [ ] **Step 4: 运行聚焦测试确认通过**

运行：`npm test -- tests/TopNavbar.system-account.test.mjs`

预期：测试通过，输出 1 个通过用例且无失败。

### Task 3: 完成前端验证

**Files:**
- Verify: `frontend/src/components/TopNavbar.vue`
- Verify: `frontend/tests/TopNavbar.system-account.test.mjs`

- [ ] **Step 1: 运行全部前端测试**

运行：`npm test`

预期：所有测试通过，退出码为 0。

- [ ] **Step 2: 运行生产构建**

运行：`npm run build`

预期：`vue-tsc` 和 Vite 构建均成功，退出码为 0。

- [ ] **Step 3: 检查目标内容和空白错误**

运行：`git diff --check -- frontend/src/components/TopNavbar.vue`，并再次运行 `npm test -- tests/TopNavbar.system-account.test.mjs`。

预期：没有空白错误；聚焦测试仍通过；本次新增内容只包含姓名展示、显示值计算、非交互样式和对应测试断言。

- [ ] **Step 4: 保留工作区改动边界**

检查 `git status --short`，确认不对工作区已有的其它改动执行暂存、提交、撤销或覆盖。由于两个目标文件已有先前未提交内容，本次不自动提交实现文件；如需提交，由用户在审阅完整 diff 后另行决定。
