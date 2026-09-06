# Device Deployment Import Performance Optimization

## Background

The `Device Deployment` import currently validates the selected files during
preview and parses and validates them again during confirmation. More
importantly, `DeploymentTransferService.persistAlarmCodeLinks()` updates the
alarm code of each imported point with a separate database statement.

The provided sample contains about 2,488 CSV rows. Existing audit logs show a
preview request taking about 6.6 seconds and the confirmation request taking
about 36.3 seconds. The sample contains about 1,662 rows with alarm
configuration, so the point-by-point alarm-code updates are the primary
optimization target.

## Goals

1. Reduce the number of MySQL round trips during a real import.
2. Preserve the current import results, reuse detection, validation rules,
   transaction boundary, and asynchronous Redis synchronization.
3. Keep unchanged existing points from receiving unnecessary updates.
4. Add automated coverage for the new write path.

## Non-goals

1. Do not change the preview and confirmation API contract in this phase.
2. Do not add asynchronous import jobs or progress polling in this phase.
3. Do not change the CSV parser or database schema unless verification proves
   it is required.

## Design

### 1. Write alarm codes with new point batches

`importFiles()` already builds `rowAlarmNames` before persistence. Pass this
map into `persistPoints()`. When a new `PointDeployment` is constructed, set
its `alarmCode` from the corresponding imported row before adding it to the
existing `insertBatch()` buffer. New points will therefore receive their
alarm code in the same batch insert and will not require a follow-up update.

### 2. Batch updates for existing points

For an existing point, preserve the current comparison rule:

- If the imported row has no alarm code, do not clear an existing code.
- If the imported row has an alarm code equal to the existing code, skip it.
- If the imported row has a different non-null code, collect one update item.

Add a mapper operation that applies a set-based update using a `CASE
n4_address` expression and a matching `WHERE n4_address IN (...)` clause. Send
updates in chunks of 500 items. This retains the unique `n4_address` lookup
while reducing thousands of individual statements to a few statements.

The batch update must execute inside the existing `@Transactional` method.
The existing point-template, device-template, and device-deployment alarm-code
link updates remain unchanged because their cardinality is already small.

### 3. Preserve post-commit synchronization

Keep `registerRedisSyncAfterCommit()` unchanged. The import response should
still return after the MySQL transaction commits, while the existing Redis
sync remains scheduled after commit.

## Testing

Add or update service tests to verify:

1. A newly inserted point carries the resolved alarm code in `insertBatch()`.
2. Existing points with changed alarm codes are sent to the batch mapper.
3. Existing points with unchanged alarm codes do not generate an update.
4. Rows without imported alarm rules do not clear an existing alarm code.
5. The existing large-import lookup and asynchronous Redis tests continue to
   pass.

Run the backend transfer tests and the frontend build after implementation.
For the supplied sample, compare the import request duration and confirm that
the number of alarm-code update statements is reduced from one per point to a
small number of batches.

## Acceptance Criteria

1. Import output and alarm-code behavior remain compatible with the current
   implementation.
2. New point inserts include alarm codes without a second per-point update.
3. Existing changed alarm codes are updated in chunks of at most 500 items.
4. No unbounded SQL statement is generated for a large import.
5. Automated tests and the frontend build pass.
