# Deployment Navigation Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show missing imported navigation groups as `new` in Preview, restore or create their hierarchy on Confirm Import, and remove only the identified legacy demo data.

**Architecture:** Extend the deployment import summary with a navigation-group count and names. The transfer service calculates unique missing top-level business groups during validation without writing; Confirm Import reuses the existing path ensure/restore transaction and mounts imported devices. The Vue dialog renders the new navigation count while preserving device and point business-key counts.

**Tech Stack:** Spring Boot, MyBatis, Java 21, JUnit 5, Mockito, Vue 3, TypeScript, Element Plus, Node test runner, MySQL.

---

### Task 1: Add the backend navigation Preview contract

**Files:**
- Modify: `monitor-backend/src/main/java/com/n4monitor/transfer/ImportSummary.java`
- Modify: `monitor-backend/src/main/java/com/n4monitor/transfer/ImportStats.java`
- Test: `monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add a test that imports a CSV whose `A1_MVP_D1/MVP_D1` path can be created below an active `A1_MVP` root, runs Preview, and asserts:

```java
assertThat(summary.valid()).isTrue();
assertThat(summary.dryRun()).isTrue();
assertThat(summary.navigationGroupCreated()).isEqualTo(1);
assertThat(summary.navigationGroupNames()).containsExactly("A1_MVP_D1");
verify(navNodeMapper, never()).insert(any(NavNode.class));
verify(navNodeMapper, never()).restore(any(NavNode.class));
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run from `E:\Curzsu\N4-monitor\monitor-backend`:

```text
mvn -s maven-settings-aliyun.xml -Dtest=DeploymentTransferServiceTest#previewReportsMissingNavigationGroupAsNew test
```

Expected: test compilation or assertion failure because `ImportSummary` has no navigation-group fields and the service does not calculate the count.

- [ ] **Step 3: Add summary fields and counters**

Add `int navigationGroupCreated` and `List<String> navigationGroupNames` to `ImportSummary`, add matching state and methods to `ImportStats`, and pass immutable names from `toSummary()`.

- [ ] **Step 4: Run the focused test to verify it passes**

Run the same Maven command. Expected: PASS with one navigation group and no mapper write calls.

- [ ] **Step 5: Commit the contract change**

```text
git add monitor-backend/src/main/java/com/n4monitor/transfer/ImportSummary.java monitor-backend/src/main/java/com/n4monitor/transfer/ImportStats.java monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java
git commit -m "feat: report new navigation groups in deployment preview"
```

### Task 2: Calculate navigation groups without changing import writes

**Files:**
- Modify: `monitor-backend/src/main/java/com/n4monitor/transfer/deployment/DeploymentTransferService.java`
- Test: `monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java`

- [ ] **Step 1: Add the missing-path and active-path assertions**

The missing-path test must assert `navigationGroupCreated() == 1`, `navigationGroupNames() == ["A1_MVP_D1"]`, and no mapper insert/restore calls for Preview. Add an active-path assertion to the existing nested navigation test that the navigation count remains zero.

- [ ] **Step 2: Run the test class to verify the new assertion fails**

```text
mvn -s maven-settings-aliyun.xml -Dtest=DeploymentTransferServiceTest test
```

Expected: the new navigation count is zero because the service currently only reports device, point, and rule counts.

- [ ] **Step 3: Implement unique navigation-group counting**

During row validation, when a system path cannot resolve to an active leaf but `canEnsureNavigationPath()` is true, add the first segment to a set and increment the summary once per unique first segment. Keep the existing `NAVIGATION_NODE_NOT_FOUND` warning for paths that cannot be attached to an active root. Leave the existing early return before `ensureNavigationPaths()` so Preview remains read-only.

- [ ] **Step 4: Run the focused import test class**

```text
mvn -s maven-settings-aliyun.xml -Dtest=DeploymentTransferServiceTest test
```

Expected: all deployment-transfer tests pass, including missing path, active path, deleted path restore, and Preview read-only behavior.

### Task 3: Expose the navigation count in the Vue dialog

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/views/SystemDeploymentView.vue`
- Test: `frontend/tests/SystemDeploymentView.navigation-preview.test.mjs`

- [ ] **Step 1: Write the failing frontend contract test**

Create a Node test that reads the real component and asserts the navigation fields and display text are present:

```js
assert.match(source, /navigationGroupCreated/)
assert.match(source, /navigationGroupNames/)
assert.match(source, /Navigation groups:/)
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `E:\Curzsu\N4-monitor\frontend`:

```text
npm test -- tests/SystemDeploymentView.navigation-preview.test.mjs
```

Expected: FAIL because the frontend type and dialog do not contain navigation fields.

- [ ] **Step 3: Add the type and display line**

Add the two fields to `ImportSummary` and render a line before device counts:

```vue
<div v-if="deploymentImportResult.navigationGroupCreated" class="transfer-result-navigation">
  Navigation groups: {{ deploymentImportResult.navigationGroupCreated }} new
  ({{ deploymentImportResult.navigationGroupNames.join(', ') }})
</div>
```

- [ ] **Step 4: Run frontend tests and build**

```text
npm test
npm run build
```

Expected: all frontend tests pass and the production build exits with code 0.

### Task 4: Prepare a recoverable database and file cleanup

**Files/targets:**
- Database rows: device IDs `452`, `453`, `454`; their point rows; navigation node IDs `27`, `28`.
- File target: `C:\Users\Lukesu\Desktop\KVTP-A0807 - 副本\A1_MVP_C1`.
- Preserve: D1 devices `455`–`457`, D1 points, formal A1 data, and shared alarm rules.

- [ ] **Step 1: Re-query exact cleanup targets**

Verify the device names, system paths, point counts, navigation names, and that no target device is D1 or formal A1 data. Abort if any ID or name differs from the approved list.

- [ ] **Step 2: Export a rollback backup**

Export the exact navigation, device, and point rows to a timestamped backup under the project `logs` directory, without exposing credentials or inserting the backup into the import folder.

- [ ] **Step 3: Remove legacy database rows in dependency order**

Delete the 12 point rows, then the three legacy device rows, then the two C1 navigation rows in one transaction. Do not delete the shared alarm-rule rows referenced by other devices.

- [ ] **Step 4: Move the legacy C1 folder out of the import folder**

Move only `A1_MVP_C1` to a timestamped desktop backup location or the Windows Recycle Bin after confirming the destination is not the import folder. Leave `A1_MVP_A1` and `A1_MVP_D1` untouched.

- [ ] **Step 5: Re-query cleanup results**

Verify the targets are absent from active and deleted deployment records, D1 remains present, and the import folder contains only the formal A1 and D1 folders.

### Task 5: End-to-end acceptance

**Files/targets:**
- Runtime: `http://localhost:5173/device-deployment`
- API: local backend on port `8088`
- Input: `C:\Users\Lukesu\Desktop\KVTP-A0807 - 副本`

- [ ] **Step 1: Rebuild and restart both services**

Start the compiled backend with the project Maven settings and the Vite frontend, then verify port 8088 and 5173 are listening.

- [ ] **Step 2: Establish the first Preview state**

Because D1 navigation is currently logically deleted, upload the clean folder and click Preview. Expected UI result includes:

```text
Navigation groups: 1 new (A1_MVP_D1)
```

and the D1 device/point counts may remain reused.

- [ ] **Step 3: Confirm the import**

Click Confirm Import and verify the API response is committed, the navigation tree contains `A1_MVP_D1 → MVP_D1`, and three D1 devices are mounted below the leaf.

- [ ] **Step 4: Verify deletion and repeat Preview**

Delete `A1_MVP_D1` from the UI, wait for the navigation tree refresh, upload the same clean folder again, and click Preview. Expected: `Navigation groups: 1 new (A1_MVP_D1)` again, not zero.

- [ ] **Step 5: Run final verification**

Run:

```text
mvn -s maven-settings-aliyun.xml -Dtest=DeploymentTransferServiceTest test
npm test
npm run build
```

Record the result and provide the local acceptance URL.

## Safety notes

- Do not alter unrelated dirty-worktree files.
- Do not delete formal A1 data, D1 rows, or shared alarm rules.
- Preview must remain read-only.
- All source code, comments, and runtime messages added by this plan must be English-only.
