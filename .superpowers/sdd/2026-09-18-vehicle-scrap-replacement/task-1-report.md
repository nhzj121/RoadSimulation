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
