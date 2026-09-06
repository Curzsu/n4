# Deployment Folder Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a direct folder-selection entry to the deployment import dialog while preserving ZIP and multi-CSV uploads.

**Architecture:** Keep the existing Element Plus uploader for ZIP and individual CSV files. Add a separate hidden native file input with `multiple` and `webkitdirectory`; when the browser returns the recursively enumerated files, filter case-insensitively to CSV files, replace the existing deployment file list, and reuse the current preview/confirm API flow. The backend and database behavior remain unchanged.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, TypeScript, Element Plus, native File API, Node.js built-in test runner, Vite.

---

### Task 1: Add the folder-upload contract test

**Files:**
- Create: `frontend/tests/SystemDeploymentView.folder-upload.test.mjs`
- Test: `frontend/src/views/SystemDeploymentView.vue`

- [ ] **Step 1: Write the failing test**

Create a Node test that reads the real Vue component source and asserts the new user-visible contract:

```js
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('deployment import exposes recursive folder selection for CSV files', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  assert.match(source, /Select Folder/)
  assert.match(source, /webkitdirectory/)
  assert.match(source, /multiple/)
  assert.match(source, /handleDeploymentFolderChange/)
  assert.match(source, /toLowerCase\(\)\.endsWith\(['"]\.csv['"]\)/)
  assert.match(source, /No CSV files found in the selected folder/)
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `E:\Curzsu\N4-monitor\frontend`:

```text
npm test -- tests/SystemDeploymentView.folder-upload.test.mjs
```

Expected result: FAIL because the current component has no `Select Folder` control or folder-change handler.

### Task 2: Implement the folder selection flow

**Files:**
- Modify: `frontend/src/views/SystemDeploymentView.vue` in the deployment import dialog template and deployment import state/handlers.

- [ ] **Step 1: Add the separate folder picker UI**

Place this beside the existing `<el-upload>` inside the deployment import dialog:

```vue
<div class="deployment-folder-picker">
  <input
    ref="deploymentFolderInputRef"
    class="deployment-folder-input"
    type="file"
    accept=".csv"
    multiple
    webkitdirectory
    @change="handleDeploymentFolderChange"
  />
  <el-button size="small" class="btn-secondary" :icon="FolderOpened" @click="openDeploymentFolderPicker">
    Select Folder
  </el-button>
  <span v-if="deploymentFolderFileCount" class="deployment-folder-summary">
    {{ deploymentFolderName }}: {{ deploymentFolderFileCount }} CSV file(s) selected
  </span>
</div>
```

Add scoped styles that hide only the native input while leaving the button accessible and keep the summary readable in the dark dialog:

```css
.deployment-folder-input {
  display: none;
}

.deployment-folder-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.deployment-folder-summary {
  color: #94a3b8;
  font-size: 12px;
}
```

- [ ] **Step 2: Add folder state and handlers**

Add the following state next to the existing deployment import refs:

```ts
const deploymentFolderInputRef = ref<HTMLInputElement>()
const deploymentFolderName = ref('')
const deploymentFolderFileCount = ref(0)
```

Add a reset helper and folder-picker handlers near the existing deployment file handlers:

```ts
function resetDeploymentFolderSelection() {
  deploymentFolderName.value = ''
  deploymentFolderFileCount.value = 0
  if (deploymentFolderInputRef.value) {
    deploymentFolderInputRef.value.value = ''
  }
}

function openDeploymentFolderPicker() {
  deploymentFolderInputRef.value?.click()
}

function handleDeploymentFolderChange(event: Event) {
  const input = event.target as HTMLInputElement
  const selectedFiles = Array.from(input.files ?? [])
  const csvFiles = selectedFiles.filter(file => file.name.toLowerCase().endsWith('.csv'))

  deploymentUploadRef.value?.clearFiles()
  deploymentImportFiles.value = csvFiles
  deploymentImportResult.value = null
  deploymentFolderFileCount.value = csvFiles.length

  const firstRelativePath = csvFiles[0]?.webkitRelativePath ?? ''
  deploymentFolderName.value = firstRelativePath.split('/')[0] || 'Selected folder'

  if (!csvFiles.length) {
    ElMessage.warning('No CSV files found in the selected folder')
  }

  input.value = ''
}
```

Update the existing file-change handler so choosing ZIP/CSV clears the folder summary:

```ts
function handleDeploymentFileChange(_uploadFile: any, uploadFiles: any[]) {
  resetDeploymentFolderSelection()
  deploymentImportFiles.value = uploadFiles
    .map(file => file.raw as File | undefined)
    .filter((file): file is File => Boolean(file))
  deploymentImportResult.value = null
}
```

Call `resetDeploymentFolderSelection()` from both `openDeploymentImportDialog()` and `closeDeploymentImportDialog()` so reopening the dialog cannot retain a previous folder selection.

- [ ] **Step 3: Preserve the existing import behavior**

Do not change `deploymentApi.importFiles`, the upload endpoint, preview/confirm guards, or any backend code. The folder handler must only populate the same `deploymentImportFiles` array used by ZIP and multi-CSV uploads.

### Task 3: Verify the implementation

**Files:**
- Test: `frontend/tests/SystemDeploymentView.folder-upload.test.mjs`
- Verify: `frontend/src/views/SystemDeploymentView.vue`

- [ ] **Step 1: Run the focused folder-upload test**

Run:

```text
npm test -- tests/SystemDeploymentView.folder-upload.test.mjs
```

Expected result: PASS.

- [ ] **Step 2: Run all frontend tests**

Run:

```text
npm test
```

Expected result: all tests pass, including the existing System setup test.

- [ ] **Step 3: Run the production build**

Run:

```text
npm run build
```

Expected result: `vue-tsc -b` and Vite complete successfully.

- [ ] **Step 4: Manually verify the intended flow**

In `System → Device Deployment → Import Data`:

1. Click `Select Folder` and choose `KVTP-A0807`.
2. Confirm the dialog reports 13 CSV files selected.
3. Click `Preview`; confirm the existing preview summary appears.
4. Choose a ZIP afterward and confirm the folder summary disappears, proving the two input modes replace each other.
5. Reopen the dialog and confirm no previous files or preview remain.

### Scope and safety

- Only `frontend/src/views/SystemDeploymentView.vue` and the new frontend test are implementation changes.
- No backend, database, API contract, or existing ZIP/multi-CSV behavior changes.
- Do not stage or commit unrelated dirty-worktree files.
