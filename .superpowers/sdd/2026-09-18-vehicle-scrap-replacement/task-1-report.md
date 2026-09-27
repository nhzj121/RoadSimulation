# Task 1 report: backend vehicle scrap and replacement continuation

## Outcome

Implemented the backend replacement domain and transactional lifecycle for `REPLACEMENT_REQUIRED` breakdowns. The original vehicle is settled and retained as `SCRAPPED`; the same active assignment is deterministically handed to a compatible `RESERVED_REPLACEMENT` vehicle after a fixed simulation-time wait. Cargo, location, node progress, assignment identity, and driving work are continued rather than recreated.

No Vue3 files, credentials, databases, services, ports, or prior handoff reports were touched.

## TDD evidence

Tests were added before production changes.

Initial RED command:

```text
.\mvnw.cmd '-Dtest=TransportRandomEventServiceTest,DrivingProgressServiceTest,RandomEventSchemaMigrationTest' test
```

Expected RED: test compilation failed with three missing symbols for `VehicleReplacementAttempt` and `VehicleReplacementAttemptRepository`. This proved the new persistence boundary did not exist before implementation.

A second focused RED covered archive/export persistence:

```text
.\mvnw.cmd '-Dtest=WeatherEnvironmentServiceTest,BreakdownDecisionPolicyTest' test
```

Expected RED: 12 tests ran, 11 passed, and the archive/export test failed because `WeatherEnvironmentService` did not yet have replacement-attempt storage.

Focused GREEN command:

```text
.\mvnw.cmd '-Dtest=TransportRandomEventServiceTest,DrivingProgressServiceTest,RandomEventSchemaMigrationTest,WeatherEnvironmentServiceTest,BreakdownDecisionPolicyTest,TransportRandomEventControllerTest,RandomEventDTOTest,StateTransitionRandomEventTest' test
```

Result: 51 tests, 0 failures, 0 errors, 0 skipped; build success.

Final required 17-class breakdown/weather regression:

```text
.\mvnw.cmd '-Dtest=WeatherMainLoopStartTest,DrivingProgressServiceTest,WeatherEnvironmentServiceTest,WeatherLifecycleTest,TransportRandomEventServiceTest,StateTransitionRandomEventTest,SimulationControllerRandomEventTest,TransportLifecycleRandomEventTest,TransportRandomEventControllerTest,RandomEventDTOTest,RandomEventDecisionPolicyTest,RandomEventSchemaMigrationTest,WeatherCargoDeliveryTest,WeatherChangingEnvironmentTest,WeatherMonitorConsistencyTest,WeatherAssignmentRestoreTest,BreakdownDecisionPolicyTest' test
```

Fresh final result: 65 tests, 0 failures, 0 errors, 0 skipped; build success.

The build retains the pre-existing duplicate springdoc dependency warning and Mockito dynamic-agent warning.

## Implemented behavior

- Added replacement level/phases, protected vehicle statuses, explicit reservation/release/activation/reset transitions, strict manual integer binding, default 60-minute wait, and 30..180 validation with legacy conflict ordering preserved.
- Fresh scenes now store `breakdown-v3` with MINOR/ASSISTANCE/REPLACEMENT weights `.60/.30/.10` and replacement waits 60..90. Explicit v2 policies use their original draw path; missing policy remains legacy.
- Required capacity projects the current runtime load/volume through every incomplete node delta in sequence and uses nonnegative peaks.
- Eligible vehicles must be idle, distinct, assignment-free, event-free, capacity-complete, and non-null in both capacities. Selection sorts load slack, volume slack, then ID; candidates are locked and rechecked. Active replacement events are processed by event ID.
- No candidate leaves the event ACTIVE/WAITING_REPLACEMENT with null planned end and retries every event tick. Reservations create independent attempt-history rows. Invalid reservations are cancelled and retried without overwriting unrelated vehicle state.
- Ready handoff checks original assignment ownership/status, original SCRAPPED state, and candidate reservation. It maintains both sides of the in-memory assignment/vehicle relationship, copies location/load/volume, clears original cargo, restores the captured driving status, and keeps the original scrapped.
- Driving progress is copied into a replacement phase with the source work/impact/completion values and `lastSettledTime=readyTime`, then immediately settled through the observing tick. This makes a large tick deduct only `[readyTime, simNow]` work.
- Success resolves at model ready time with processed time and `REPLACED`; repeated ticks are no-ops. Guard failures resolve with specific outcomes and release only a still-reserved candidate.
- Nullable `planned_end_time` has an idempotent metadata-driven schema migration. Every progress/effective-factor/transition consumer is null-safe; the open-ended event freezes the original scrapped vehicle at factor 0.
- DTOs, monitor association, live/history event responses, run export, archived run JSON, and cleanup order include replacement data/attempts. Candidate transition blocking recognizes `replacementVehicleId`.
- `AssignmentLeg` was not modified, and no cost or comparison-experiment behavior was added.

## Files

New:

- `entity/VehicleReplacementAttempt.java`
- `repository/VehicleReplacementAttemptRepository.java`

Core changes:

- event/vehicle/weather-run entities and DTO/request/controller binding
- `TransportRandomEventService`, `DrivingProgressService`, `BreakdownDecisionPolicy`, `WeatherEnvironmentService`
- schema migration, monitor association, reset/cleanup integration
- focused service, controller, DTO, policy, progress, schema, and archive/export tests

## Self-review

- Verified assignment ownership is updated on both owning and inverse sides before same-tick progress settlement.
- Verified candidate transition blocking uses either original `vehicleId` or `replacementVehicleId`.
- Verified open-ended replacement events do not dereference null planned-end values.
- Verified attempt rows contain run/event/assignment/original/replacement snapshots, selection/ready/handoff times, wait, capacity, status, outcome, and rule version.
- Verified deterministic sharing prevention and retry/cancellation behavior in focused tests.
- Verified existing timeout cleanup already excludes shipments with ASSIGNED/IN_PROGRESS assignments, so keeping the same active assignment protects replacement waits without an extra cleanup exception.
- `git diff --check` was clean before reporting.

## Concerns / limits

- Per instruction, no live database or running service was used. Schema behavior is covered by the idempotent migration unit test and JPA compilation, not a live MySQL migration.
- The repository still emits its existing Maven model and Mockito agent warnings; these are unrelated to this change.

## Review fix round (2026-09-19)

Addressed all three confirmed review findings without changing frontend code:

- Replacement reservation now uses a MySQL conditional update (`IDLE` plus an unowned reservation) carrying an event-owner token. The update clears the persistence context, after which the service reloads the vehicle under a pessimistic lock and rechecks current capacity, assignment, and event eligibility. Release is another owner-matched conditional update, so one event cannot clear another event's reservation. A nullable unique owner column additionally prevents one event from owning multiple candidates.
- The concurrency test starts two independent threads from detached, stale `IDLE` snapshots and synchronizes their reads before an atomic repository claim. It proves exactly one event reserves the shared candidate and the loser remains waiting. A focused assertion verifies the post-claim active-assignment recheck uses the locking repository query.
- Progress transfer validates the latest source against the current run, current assignment, and event-captured phase key before ownership changes. The replacement row is ordered at `replacementReadyTime`, retains all work/impact counters, and settles through the large observing tick so only `[readyTime, simNow]` is deducted. A prior, newer completed replacement task no longer hides the transferred row.
- Both the migration DDL and entity mapping preserve `DATETIME(6)` while allowing `planned_end_time` to be null.
- Focused monitor coverage verifies replacement fields and reserved-candidate event association. Focused reset coverage verifies a reserved vehicle returns to `IDLE` and clears its reservation owner.

Review RED evidence, written and run before the production fixes:

```text
.\mvnw.cmd '-Dtest=TransportRandomEventServiceTest,DrivingProgressServiceTest,RandomEventSchemaMigrationTest,WeatherMonitorConsistencyTest,SimulationDataCleanupReplacementTest' test
```

Expected RED: test compilation failed with eight missing-contract errors for the reservation owner accessors, atomic repository operations, and phase-validating transfer signature. After the first implementation pass, a further focused RED failed compilation on the missing `findActiveAssignmentByVehicleForUpdate` contract, proving the fresh assignment eligibility check was not yet locked.

Final review-focused GREEN:

```text
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
```

Re-run of the implementer's focused suite:

```text
Tests run: 56, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
```

Re-run of the named 17-class regression:

```text
Tests run: 70, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
```

Review-fix limits: per instruction, no live/shared database was accessed. This repository has no isolated database/testcontainer harness, so the concurrency proof is the strongest available service/repository-boundary test (two real threads with detached stale snapshots and an atomic CAS repository double), rather than a live MySQL two-connection test. Microsecond behavior is verified at generated migration SQL and JPA mapping level, not by live persistence round-trip.

## Second review fix round (2026-09-19)

Fixed the two follow-up integration findings while preserving the atomic reservation owner CAS, source-progress validation, `DATETIME(6)`, and reset behavior:

- Removed `clearAutomatically=true` from both native reservation updates. They still flush before the conditional update, but no longer detach every assignment buffered by an automatic event tick.
- After each successful reserve or release CAS, `TransportRandomEventService` refreshes only the affected vehicle with `PESSIMISTIC_WRITE`. This replaces the stale first-level-cache state with the current database status/owner without invalidating unrelated managed assignments or their lazy node collections.
- Added coverage for two buffered automatic replacement assignments in one tick, each reserving a distinct candidate, plus a stale-managed-candidate test that only succeeds after targeted refresh.
- The active monitor now loads replacement vehicle IDs from active events in addition to assignment-owned vehicles. Before handoff it therefore shows both the original `SCRAPPED` vehicle (still owning the assignment) and the unassigned `RESERVED_REPLACEMENT` candidate, with the same active event attached to both rows.
- `BREAKDOWN`, `SCRAPPED`, and `RESERVED_REPLACEMENT` monitor rows explicitly report effective speed factor `0.0`; scrapped/reserved statuses also have distinct status text.

Initial RED:

```text
.\mvnw.cmd '-Dtest=TransportRandomEventServiceTest,WeatherMonitorConsistencyTest' test
Tests run: 29, Failures: 3, Errors: 0
```

The three expected failures proved that the CAS annotations still globally cleared the persistence context, a successful CAS left a managed candidate stale (`replacementVehicleId` remained null), and the realistic unassigned replacement candidate was missing from the monitor (`expected 2 rows, was 1`).

Additional monitor-factor RED:

```text
.\mvnw.cmd '-Dtest=WeatherMonitorConsistencyTest' test
Tests run: 2, Failures: 1, Errors: 0
```

Expected failure: the scrapped row reported the mocked moving factor `0.75` instead of `0.0`.

Fresh final GREEN evidence:

```text
Review-focused: 40 tests, 0 failures, 0 errors, 0 skipped
Broader focused: 59 tests, 0 failures, 0 errors, 0 skipped
Named 17-class regression: 73 tests, 0 failures, 0 errors, 0 skipped
```

Second-round limit: no live/shared database was accessed. Persistence behavior is covered by the repository annotation contract, targeted-refresh service behavior, and the strongest available two-assignment tick regression in this repository; a live MySQL persistence-context test remains outside the authorized environment.

## Final review fixes (2026-09-27)

- Added legacy node-less capacity projection. Pre-pickup `ORDER_DRIVING` starts at zero and adds the weight and volume of still-`ASSIGNED` items independently. Post-pickup runtime load/volume remains the baseline, while `LOADED`/`IN_TRANSIT` items are not added again. The existing ordered incomplete-node peak projection is unchanged for VRP assignments.
- Added durable `ReplacementRecoveryState`, sourced from the latest persisted successful replacement event and checked against the Assignment's current owner. Active-assignment and monitor DTOs now expose the event, original owner, current owner, recovery flag, and backend arrival readiness.
- Readiness is true only for an active node-less Assignment owned by the persisted replacement vehicle after the backend reaches `UNLOADING`. VRP tasks remain node-driven. A replacement acknowledgement carries `replacementEventId`; the controller revalidates readiness and event identity, and already-closed Assignments remain idempotent HTTP 200.

Strict RED evidence:

```text
Capacity focus: 3 tests, 2 expected failures
- pre-pickup expected 10.0, old result 99.0
- partial pickup expected 8.0, old result 5.0

Recovery contract focus: test compilation failed on the intentionally missing
ReplacementRecoveryState, DTO accessors, repository lookup, and request field.
```

Final named 18-class backend regression:

```text
82 tests, 0 failures, 0 errors, 0 skipped
```

No database, service, port, or shared configuration was used or changed. The existing Maven model and Mockito agent warnings remain.
