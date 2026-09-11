# Device Deployment Import Preview 优化实施计划

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 修正新设备点位的导入统计、简化确认按钮文案，并将 Device Deployment Preview 改为按系统路径展开的层级表格。

**Architecture:** 后端保留现有设备业务键、点位 mqttPath 和持久化复用行为，只让新设备的有效点位优先计入 created。前端新增纯函数把平铺的设备预览转换成系统父行与设备子行，使用 Element Plus tree table 展开显示，并将导入结果拆成概览、统计、设备明细和问题四个视觉分区。

**Tech Stack:** Spring Boot / JUnit 5 / Mockito / Vue 3 / TypeScript / Element Plus / Node.js built-in test runner.

---

## 文件结构

- Modify: monitor-backend/src/main/java/com/n4monitor/transfer/deployment/DeploymentTransferService.java — 以设备动作决定点位统计口径。
- Modify: monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java — 覆盖新设备复用地址仍计为新点位。
- Create: frontend/src/utils/deploymentImportPreview.ts — 将设备预览按 systemName 构造成树形数据。
- Modify: frontend/src/views/SystemDeploymentView.vue — 接入树形预览、调整 Preview 布局和确认按钮文案。
- Create: frontend/tests/SystemDeploymentView.import-preview.test.mjs — 覆盖文案、树形表格契约和分组函数行为。

### Task 1: 为新设备点位统计写失败的后端回归测试

**Files:**
- Modify: monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java

- [ ] Step 1: Write the failing test

在现有测试类中新增 countsPointsAsCreatedWhenTheirDeviceIsNew。测试构造已存在但挂载在其他导航节点上的设备，以及相同 mqttPath 的已有点位；Preview 时设备动作应为 CREATE，点位应按 created 统计。测试体使用现有 mock、实体构造和静态导入：

    @Test
    void countsPointsAsCreatedWhenTheirDeviceIsNew() {
        DeviceTemplateMapper deviceTemplateMapper = mock(DeviceTemplateMapper.class);
        PointTemplateMapper pointTemplateMapper = mock(PointTemplateMapper.class);
        DeviceDeploymentMapper deviceDeploymentMapper = mock(DeviceDeploymentMapper.class);
        PointDeploymentMapper pointDeploymentMapper = mock(PointDeploymentMapper.class);
        NavNodeMapper navNodeMapper = mock(NavNodeMapper.class);
        AlarmRuleEnumMapper alarmRuleEnumMapper = mock(AlarmRuleEnumMapper.class);
        AlarmRuleNumericMapper alarmRuleNumericMapper = mock(AlarmRuleNumericMapper.class);
        AlarmRuleSyncService alarmRuleSyncService = mock(AlarmRuleSyncService.class);

        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplId(1L);
        template.setN4Name("Collector");
        template.setIsDeleted(false);

        PointTemplate pointTemplate = new PointTemplate();
        pointTemplate.setPointTmplId(10L);
        pointTemplate.setDeviceTmplId(1L);
        pointTemplate.setPointName("Temperature");
        pointTemplate.setType("numeric");
        pointTemplate.setIsDeleted(false);

        DeviceDeployment existingDevice = new DeviceDeployment();
        existingDevice.setDeviceDeployId(20L);
        existingDevice.setDeviceTmplId(1L);
        existingDevice.setNavNodeId(999L);
        existingDevice.setSystemName("A1_MVP_A1/MVP_A1");
        existingDevice.setDeviceDeployName("MVP_A1_00_Collector_00");
        existingDevice.setIsDeleted(false);

        PointDeployment existingPoint = new PointDeployment();
        existingPoint.setPointDeployId(30L);
        existingPoint.setPointTmplId(10L);
        existingPoint.setDeviceDeployId(20L);
        existingPoint.setPointName("Temperature");
        existingPoint.setN4Address("A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Temperature");
        existingPoint.setIsDeleted(false);

        NavNode root = new NavNode();
        root.setNodeId(100L);
        root.setNodeName("A1_MVP_A1");
        root.setNodeLevel(1);
        root.setMaxLevel(2);
        root.setIsDeleted(false);

        NavNode leaf = new NavNode();
        leaf.setNodeId(101L);
        leaf.setParentId(100L);
        leaf.setNodeName("MVP_A1");
        leaf.setNodeLevel(2);
        leaf.setIsDeleted(false);

        when(deviceTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(template));
        when(pointTemplateMapper.findAllIncludingDeleted()).thenReturn(List.of(pointTemplate));
        when(deviceDeploymentMapper.findAllIncludingDeleted()).thenReturn(List.of(existingDevice));
        when(pointDeploymentMapper.findAllByN4Addresses(List.of(existingPoint.getN4Address())))
                .thenReturn(List.of(existingPoint));
        when(navNodeMapper.findAll()).thenReturn(List.of(root, leaf));
        when(alarmRuleEnumMapper.findAll()).thenReturn(List.of());
        when(alarmRuleNumericMapper.findAll()).thenReturn(List.of());

        DeploymentTransferService service = new DeploymentTransferService(
                new DeploymentCsvParser(),
                deviceTemplateMapper,
                pointTemplateMapper,
                deviceDeploymentMapper,
                pointDeploymentMapper,
                navNodeMapper,
                alarmRuleEnumMapper,
                alarmRuleNumericMapper,
                alarmRuleSyncService);

        String csv = String.join("\n",
                "*path,*name,*type,allPath,mqttPath",
                "MVP_A1_00_Collector_00,Temperature,numeric,all/path,"
                        + existingPoint.getN4Address());
        MockMultipartFile file = new MockMultipartFile(
                "files", "deployment.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = service.importFiles(List.of(file), true);

        assertThat(summary.valid()).isTrue();
        assertThat(summary.deviceDeploymentCreated()).isEqualTo(1);
        assertThat(summary.deviceDeploymentReused()).isZero();
        assertThat(summary.pointDeploymentCreated()).isEqualTo(1);
        assertThat(summary.pointDeploymentReused()).isZero();
        assertThat(summary.deploymentPreviews()).singleElement()
                .extracting(DeploymentPreviewItem::action)
                .isEqualTo("CREATE");
    }

The existing imports already provide all referenced types and assertThat; do not add production code yet.

- [ ] Step 2: Run the focused test and verify the expected failure

Run from monitor-backend:

    mvn -s maven-settings-aliyun.xml "-Dtest=DeploymentTransferServiceTest#countsPointsAsCreatedWhenTheirDeviceIsNew" test

Expected: the test fails on pointDeploymentCreated() because the current implementation increments pointDeploymentReused whenever the imported mqttPath already exists.

### Task 2: Implement the new-device point classification

**Files:**
- Modify: monitor-backend/src/main/java/com/n4monitor/transfer/deployment/DeploymentTransferService.java

- [ ] Step 1: Change the point statistic branch

Immediately after existingPoint is obtained in the row-processing loop, use the owning device action as the first condition:

    PointDeployment existingPoint = pointsByN4Address.get(n4Address);
    boolean isNewDevice = !isActiveDeployment(devicePlan.existingDeployment(), devicePlan.navNodeId());
    if (isNewDevice || existingPoint == null) {
        stats.pointDeploymentCreated();
    } else {
        stats.pointDeploymentReused();
    }

Keep existingPoint in PointPlan unchanged so formal persistence still reuses or updates the existing row by mqttPath.

- [ ] Step 2: Run the focused test and verify it passes

Run the Task 1 Maven command again. Expected: PASS with one created device, zero reused devices, one created point, zero reused points, and deployment preview action CREATE.

- [ ] Step 3: Run the full deployment transfer test class

    mvn -s maven-settings-aliyun.xml "-Dtest=DeploymentTransferServiceTest" test

Expected: the class passes, including active-device point reuse and navigation-path behavior.

### Task 3: Add the failing frontend Preview contract and grouping test

**Files:**
- Create: frontend/tests/SystemDeploymentView.import-preview.test.mjs

- [ ] Step 1: Write the failing test

Create a source-contract test and exercise the real grouping helper after TypeScript transpilation:

    import assert from 'node:assert/strict'
    import { readFile } from 'node:fs/promises'
    import { fileURLToPath } from 'node:url'
    import { test } from 'node:test'
    import ts from 'typescript'

    test('deployment preview uses a concise confirm label and hierarchical rows', async () => {
      const componentPath = fileURLToPath(new URL('../src/views/SystemDeploymentView.vue', import.meta.url))
      const helperPath = fileURLToPath(new URL('../src/utils/deploymentImportPreview.ts', import.meta.url))
      const source = await readFile(componentPath, 'utf8')
      const helperSource = await readFile(helperPath, 'utf8')

      assert.doesNotMatch(source, /Confirm Import/)
      assert.match(source, /@click="confirmDeploymentImport"/)
      assert.match(source, /buildDeploymentPreviewTree/)
      assert.match(source, /row-key="id"/)
      assert.match(source, /default-expand-all/)
      assert.match(source, /children:\s*'children'/)
      assert.match(source, /Import Preview/)
      assert.match(source, /preview-section/)

      const compiledHelper = ts.transpileModule(helperSource, {
        compilerOptions: {
          module: ts.ModuleKind.ESNext,
          target: ts.ScriptTarget.ES2022,
        },
      }).outputText
      const moduleUrl = 'data:text/javascript;charset=utf-8,' + encodeURIComponent(compiledHelper)
      const { buildDeploymentPreviewTree } = await import(moduleUrl)

      const result = buildDeploymentPreviewTree([
        {
          sourceFile: 'a.csv',
          deviceName: 'Collector A',
          devicePath: 'Collector_A',
          systemName: 'A1_MVP_A1/MVP_A1',
          templateName: 'Collector',
          pointCount: 2,
          action: 'CREATE',
        },
        {
          sourceFile: 'b.csv',
          deviceName: 'Collector B',
          devicePath: 'Collector_B',
          systemName: 'A1_MVP_A1/MVP_A1',
          templateName: 'Collector',
          pointCount: 3,
          action: 'REUSE',
        },
      ])

      assert.equal(result.length, 1)
      assert.equal(result[0].rowType, 'system')
      assert.equal(result[0].systemName, 'A1_MVP_A1/MVP_A1')
      assert.equal(result[0].deviceCount, 2)
      assert.equal(result[0].pointCount, 5)
      assert.equal(result[0].children.length, 2)
      assert.equal(result[0].children[0].rowType, 'device')
      assert.equal(result[0].children[1].deviceName, 'Collector B')
    })

- [ ] Step 2: Run the focused test and verify the expected failure

Run from frontend:

    npm test -- tests/SystemDeploymentView.import-preview.test.mjs

Expected: FAIL because the helper file does not exist and the component still contains Confirm Import and a flat device table.

### Task 4: Implement the frontend tree helper and layered Preview UI

**Files:**
- Create: frontend/src/utils/deploymentImportPreview.ts
- Modify: frontend/src/views/SystemDeploymentView.vue

- [ ] Step 1: Add the grouping helper

Create the helper with one parent row per systemName, preserving input order and aggregating device and point counts:

    import type { DeploymentPreviewItem } from '@/api/types'

    export interface DeploymentPreviewTreeRow extends DeploymentPreviewItem {
      id: string
      rowType: 'system' | 'device'
      deviceCount?: number
      children?: DeploymentPreviewTreeRow[]
    }

    export function buildDeploymentPreviewTree(
      items: DeploymentPreviewItem[] = [],
    ): DeploymentPreviewTreeRow[] {
      const groups = new Map<string, DeploymentPreviewTreeRow>()

      items.forEach((item, index) => {
        const groupId = 'system-' + (item.systemName || 'unknown')
        let group = groups.get(groupId)
        if (!group) {
          group = {
            id: groupId,
            rowType: 'system',
            sourceFile: '',
            deviceName: '',
            devicePath: '',
            systemName: item.systemName,
            templateName: '',
            pointCount: 0,
            action: 'GROUP',
            deviceCount: 0,
            children: [],
          }
          groups.set(groupId, group)
        }

        group.children?.push({
          ...item,
          id: groupId + '-device-' + index,
          rowType: 'device',
        })
        group.deviceCount = (group.deviceCount ?? 0) + 1
        group.pointCount += item.pointCount
      })

      return Array.from(groups.values())
    }

- [ ] Step 2: Connect the helper to the Vue script

Add the import:

    import { buildDeploymentPreviewTree } from '@/utils/deploymentImportPreview'

After deploymentSummaryTableData, add:

    const deploymentPreviewTreeData = computed(() =>
      buildDeploymentPreviewTree(deploymentImportResult.value?.deploymentPreviews ?? []),
    )

- [ ] Step 3: Replace the result layout and flat device table

Use a transfer-result wrapper with an Import Preview header, source file and row metadata, and a ready/action-required tag. Put Summary Statistics in a preview-section, then render the device data using this tree-table contract:

    <section v-if="deploymentPreviewTreeData.length" class="preview-section">
      <div class="preview-section-heading">
        <span>Parsed Device Deployments</span>
        <span class="preview-section-caption">
          {{ deploymentImportResult.deploymentPreviews?.length ?? 0 }} device(s) grouped by system
        </span>
      </div>
      <el-table
        :data="deploymentPreviewTreeData"
        class="custom-table preview-device-tree"
        row-key="id"
        border
        size="small"
        default-expand-all
        :tree-props="{ children: 'children' }"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
      >
        <el-table-column label="System / Device" min-width="330" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.rowType === 'system'" class="preview-system-cell">
              <span class="preview-system-name">{{ row.systemName }}</span>
              <span class="preview-system-meta">
                {{ row.deviceCount }} device(s) · {{ row.pointCount }} point(s)
              </span>
            </div>
            <div v-else class="preview-device-cell">
              <span class="preview-device-name">{{ row.deviceName }}</span>
              <span class="preview-device-path">{{ row.devicePath }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="sourceFile" label="File" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.rowType === 'device'">{{ row.sourceFile }}</span>
            <span v-else class="preview-empty-value">Grouped</span>
          </template>
        </el-table-column>
        <el-table-column prop="templateName" label="Matched Template" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.rowType === 'device'">{{ row.templateName || '-' }}</span>
            <span v-else class="preview-empty-value">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="pointCount" label="Points" width="90" align="center" />
        <el-table-column prop="action" label="Import Action" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.rowType === 'system'" size="small" type="info" effect="plain">Group</el-tag>
            <el-tag v-else-if="row.action === 'CREATE'" size="small" type="success" effect="dark">New</el-tag>
            <el-tag v-else-if="row.action === 'REUSE'" size="small" type="info" effect="dark">Reused</el-tag>
            <el-tag v-else size="small" type="warning" effect="dark">Skipped</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

The summary table keeps the existing fields but uses New and Reused column labels. The issues section keeps the existing five columns and fields exactly: severity/Level, code/Code, fileName/File, rowNumber/Row, and message/Message. Change only its surrounding section heading to the layered preview-section-heading--danger style.

Change only the footer button text to Confirm while preserving its click handler, loading state and disabled condition.

- [ ] Step 4: Add the layered-preview styles

Add classes for the result header, section panels, captions, system/device cells, and system-level tree rows. The styles must include the following behavior:

    .transfer-result {
      display: flex;
      flex-direction: column;
      gap: 14px;
      margin-top: 20px;
    }

    .preview-result-header,
    .preview-section-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    .preview-result-header {
      padding: 12px 14px;
      border: 1px solid rgba(59, 130, 246, 0.32);
      border-radius: 8px;
      background: linear-gradient(135deg, rgba(30, 64, 175, 0.22), rgba(15, 23, 42, 0.86));
    }

    .preview-section {
      padding: 10px;
      border: 1px solid rgba(51, 65, 85, 0.8);
      border-radius: 8px;
      background: rgba(15, 23, 42, 0.46);
    }

    .preview-system-cell,
    .preview-device-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }

    .preview-system-name {
      color: #93c5fd;
      font-weight: 700;
    }

    .preview-system-meta,
    .preview-device-path,
    .preview-result-meta,
    .preview-section-caption,
    .preview-empty-value {
      color: $text-muted;
      font-size: 10px;
    }

    .preview-device-name {
      color: $text-primary;
      font-weight: 600;
    }

    .preview-device-tree :deep(.el-table__row--level-0) {
      background: rgba(30, 64, 175, 0.14);
    }

    .preview-device-tree :deep(.el-table__row--level-0:hover > td) {
      background: rgba(37, 99, 235, 0.22) !important;
    }

    .preview-issues-section {
      border-color: rgba(248, 113, 113, 0.34);
      background: rgba(127, 29, 29, 0.12);
    }

Keep the existing dark table style and all validation, upload, API, and refresh behavior unchanged. Keep all user-facing source strings in English.

- [ ] Step 5: Run the focused frontend test and verify it passes

    npm test -- tests/SystemDeploymentView.import-preview.test.mjs

Expected: PASS, including the grouping assertion and the absence of Confirm Import.

### Task 5: Verify all affected behavior

- [ ] Step 1: Run all frontend tests from frontend

    npm test

Expected: all Node tests pass with zero failures.

- [ ] Step 2: Run the frontend type-check and production build from frontend

    npm run build

Expected: vue-tsc and Vite finish with exit code 0.

- [ ] Step 3: Run the backend deployment transfer test class from monitor-backend

    mvn -s maven-settings-aliyun.xml "-Dtest=DeploymentTransferServiceTest" test

Expected: the class passes with zero failures.

- [ ] Step 4: Review the diff and repository status from the repository root

    git diff --check
    git status --short
    git diff --stat

Expected: only task-related implementation files remain modified and no Chinese characters are added to source files.

- [ ] Step 5: Commit the implementation locally

Stage only the five implementation/test files listed in the File Structure section and commit with:

    git commit -m "fix(deploy): 修正新设备点位统计并优化导入预览"

Do not push to any remote.

