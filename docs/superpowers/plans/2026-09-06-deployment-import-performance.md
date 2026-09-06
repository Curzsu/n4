# Device Deployment Import Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce `Device Deployment` import database round trips by writing alarm codes with point batches and batching updates for existing points.

**Architecture:** Keep the existing transactional import flow and CSV/API contract. Resolve alarm names as today, attach them to new `PointDeployment` rows before `insertBatch()`, and send only changed existing alarm links through a set-based MySQL update in bounded chunks of 500 rows.

**Tech Stack:** Java 21, Spring Boot, MyBatis XML mappers, JUnit 5, Mockito, Maven, Vue/Vite verification.

---

## File Map

- Modify `monitor-backend/src/main/java/com/n4monitor/transfer/deployment/DeploymentTransferService.java` to pass resolved alarm names into point persistence, include them in new point batches, and batch changed existing-point links.
- Modify `monitor-backend/src/main/java/com/n4monitor/deployment/mapper/PointDeploymentMapper.java` to expose the bounded batch update operation.
- Modify `monitor-backend/src/main/resources/mapper/PointDeploymentMapper.xml` to implement the set-based `CASE n4_address` update.
- Modify `monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java` to cover new-point alarm-code inserts, changed existing-point batch updates, unchanged links, and the existing no-clear behavior.

## Task 1: Add the failing test for new-point alarm codes

**Files:**
- Modify: `monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java`

- [ ] **Step 1: Extend the existing new-point alarm-link test.**

In `keepsAlarmLinksSeparateForTheSameDevicePathInDifferentSystems()`, capture the argument passed to `pointDeploymentMapper.insertBatch()` and assert that the two inserted points already contain the resolved alarm names. Also assert that the old per-address update is not used for new points:

```java
var pointCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
verify(pointDeploymentMapper).insertBatch(pointCaptor.capture());
@SuppressWarnings("unchecked")
List<PointDeployment> importedPoints = (List<PointDeployment>) pointCaptor.getValue();
assertThat(importedPoints)
        .extracting(PointDeployment::getAlarmCode)
        .containsExactly("Communication_status_1_00", "Communication_status_1_01");
verify(pointDeploymentMapper, never()).updateAlarmCodeByN4Address(any(), any());
```

- [ ] **Step 2: Run only the transfer service test and confirm the expected failure.**

Run from `E:\Curzsu\N4-monitor`:

```text
mvn.cmd -pl monitor-backend -am -Dtest=DeploymentTransferServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: the test fails because the inserted `PointDeployment` rows currently have a null `alarmCode`, and the current service still calls the per-address update for new points.

## Task 2: Put alarm codes into new point batches

**Files:**
- Modify: `monitor-backend/src/main/java/com/n4monitor/transfer/deployment/DeploymentTransferService.java`
- Test: `monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java`

- [ ] **Step 1: Pass the resolved row-alarm map into point persistence.**

Change the call to `persistPoints()` to pass `alarmState.rowAlarmNames()`:

```java
persistPoints(pointPlans.values(), deviceIds, alarmState.rowAlarmNames());
```

- [ ] **Step 2: Set the alarm code before buffering a point for insertion.**

Change the method signature and assign the resolved code immediately after `buildPoint()`:

```java
private void persistPoints(
        Iterable<PointPlan> plans,
        Map<String, Long> deviceIds,
        Map<String, String> rowAlarmNames) {
    List<PointDeployment> pending = new ArrayList<>();
    for (PointPlan plan : plans) {
        DeploymentCsvRow row = plan.row();
        Long deviceId = deviceIds.get(plan.devicePlan().deviceKey());
        PointDeployment point = buildPoint(row, plan.pointTemplate(), deviceId);
        point.setAlarmCode(rowAlarmNames.get(rowKey(row)));
        // Preserve the existing insert/update handling below.
    }
}
```

Keep the existing point batching, restore behavior, metadata comparison, and final flush unchanged around this assignment.

- [ ] **Step 3: Remove the old per-point alarm-code update from link persistence.**

Delete the `PointDeployment existingPoint` comparison and `updateAlarmCodeByN4Address()` call from `persistAlarmCodeLinks()`. That method continues to update the point-template, device-template, and deployment-level alarm links, whose cardinality is small.

- [ ] **Step 4: Run the focused test and confirm it passes.**

Run:

```text
mvn.cmd -pl monitor-backend -am -Dtest=DeploymentTransferServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: the new-point alarm-code assertion passes and the focused test class is green.

## Task 3: Add the failing test for changed existing-point links

**Files:**
- Modify: `monitor-backend/src/main/java/com/n4monitor/deployment/mapper/PointDeploymentMapper.java`
- Modify: `monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java`

- [ ] **Step 1: Add the mapper method signature needed by the test.**

Add this method to `PointDeploymentMapper` after `insertBatch()`:

```java
int updateAlarmCodesBatch(@Param("points") List<PointDeployment> points);
```

This exposes only the new mapper contract; no service behavior is changed in this step.

- [ ] **Step 2: Add a changed-alarm-code service test.**

Add `updatesChangedAlarmCodesInBatch()` to `DeploymentTransferServiceTest.java`. Use the existing numeric-rule fixture shape, set the existing point alarm code to `OldAlarm`, import a row whose matching existing numeric rule resolves to `Temperature_00`, mock `updateAlarmCodesBatch()` to return `1`, and assert:

```java
verify(pointDeploymentMapper).updateAlarmCodesBatch(pointCaptor.capture());
assertThat(pointCaptor.getValue())
        .extracting(PointDeployment::getN4Address, PointDeployment::getAlarmCode)
        .containsExactly(tuple(existingPoint.getN4Address(), "Temperature_00"));
verify(pointDeploymentMapper, never()).updateAlarmCodeByN4Address(any(), any());
```

- [ ] **Step 3: Run the focused test and confirm the expected failure.**

Run:

```text
mvn.cmd -pl monitor-backend -am -Dtest=DeploymentTransferServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: the new test fails because `DeploymentTransferService` does not yet collect or call `updateAlarmCodesBatch()`.

## Task 4: Implement bounded batch updates for existing points

**Files:**
- Modify: `monitor-backend/src/main/java/com/n4monitor/transfer/deployment/DeploymentTransferService.java`
- Modify: `monitor-backend/src/main/resources/mapper/PointDeploymentMapper.xml`
- Test: `monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java`

- [ ] **Step 1: Add a batch-size constant and collect only changed non-null codes.**

Add the constant beside `POINT_BATCH_SIZE`:

```java
private static final int ALARM_CODE_BATCH_SIZE = 500;
```

In `persistPoints()`, collect a lightweight `PointDeployment` update item for an existing point only when the imported alarm code is non-null and differs from the existing value:

```java
List<PointDeployment> alarmCodeUpdates = new ArrayList<>();
String alarmCode = rowAlarmNames.get(rowKey(row));
if (alarmCode != null && !Objects.equals(plan.existingPoint().getAlarmCode(), alarmCode)) {
    PointDeployment alarmUpdate = new PointDeployment();
    alarmUpdate.setN4Address(row.mqttPath());
    alarmUpdate.setAlarmCode(alarmCode);
    alarmCodeUpdates.add(alarmUpdate);
}
```

After the final point insert flush, send updates in chunks:

```java
for (int start = 0; start < alarmCodeUpdates.size(); start += ALARM_CODE_BATCH_SIZE) {
    int end = Math.min(start + ALARM_CODE_BATCH_SIZE, alarmCodeUpdates.size());
    pointDeploymentMapper.updateAlarmCodesBatch(alarmCodeUpdates.subList(start, end));
}
```

Do not collect rows with a null imported alarm code; this preserves the current behavior of not clearing an existing alarm code when the CSV has no rule.

- [ ] **Step 2: Add the set-based MyBatis update.**

Add this statement to `PointDeploymentMapper.xml`:

```xml
<update id="updateAlarmCodesBatch">
    UPDATE point_deployment
    SET alarm_code = CASE n4_address
    <foreach collection="points" item="point">
        WHEN #{point.n4Address} THEN #{point.alarmCode}
    </foreach>
    END,
    update_time = NOW()
    WHERE n4_address IN
    <foreach collection="points" item="point" open="(" separator="," close=")">
        #{point.n4Address}
    </foreach>
</update>
```

The service always calls this statement with a non-empty sublist of at most 500 items. The `n4_address` unique index remains the lookup path.

- [ ] **Step 3: Update unchanged-code coverage to assert no batch call.**

In `skipsAlarmCodeWriteWhenImportedAlarmCodeIsUnchanged()`, replace the old per-address verification with:

```java
verify(pointDeploymentMapper, never()).updateAlarmCodesBatch(anyList());
```

- [ ] **Step 4: Run the focused tests and confirm they pass.**

Run:

```text
mvn.cmd -pl monitor-backend -am -Dtest=DeploymentTransferServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: all `DeploymentTransferServiceTest` methods pass, including new-point inserts, changed existing links, unchanged links, no-clear behavior, large lookup batching, and asynchronous Redis scheduling.

## Task 5: Full verification and delivery checkpoint

**Files:**
- Verify: all files changed by Tasks 1-4

- [ ] **Step 1: Run the complete backend test suite.**

Run:

```text
mvn.cmd -pl monitor-backend -am test
```

Expected result: Maven completes successfully.

- [ ] **Step 2: Run the frontend production build.**

Run from `E:\Curzsu\N4-monitor\frontend`:

```text
npm.cmd run build
```

Expected result: `vue-tsc -b` and `vite build` complete successfully. Existing dependency annotation and large-chunk warnings are acceptable if the command exits successfully.

- [ ] **Step 3: Review the final diff and verify scope.**

Run from `E:\Curzsu\N4-monitor`:

```text
git diff --check
git status --short --branch
git diff --stat
```

Confirm that only the mapper, transfer service, transfer service test, and implementation-plan/design documentation are changed, with no generated artifacts or unrelated files staged.

- [ ] **Step 4: Commit only the implementation files.**

Use a local commit with the repository’s conventional Chinese message style:

```text
git add monitor-backend/src/main/java/com/n4monitor/transfer/deployment/DeploymentTransferService.java monitor-backend/src/main/java/com/n4monitor/deployment/mapper/PointDeploymentMapper.java monitor-backend/src/main/resources/mapper/PointDeploymentMapper.xml monitor-backend/src/test/java/com/n4monitor/transfer/deployment/DeploymentTransferServiceTest.java
git commit -m "perf: 优化设备部署导入报警编码批量写入"
```

Do not push to any remote.
