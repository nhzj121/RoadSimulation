# Breakdown v2 backend report

## Live API follow-up (2026-09-17)

Live QA found a gap in the standalone MockMvc setup: malformed minute JSON was rejected
by the deserializer but the application's generic GlobalExceptionHandler turned the response
into HTTP 500. Added the actual advice to the request-binding tests; RED reproduced two
400-versus-500 failures for event triggering and weather policy import. Controller-local
HttpMessageNotReadableException handlers now return 400 without changing unrelated controllers.
GREEN: `./mvnw.cmd -Dtest=TransportRandomEventControllerTest,WeatherEnvironmentServiceTest,TransportRandomEventServiceTest,BreakdownDecisionPolicyTest test`
completed with 31 tests, zero failures/errors/skips on 2026-09-17 11:47.

Runtime recheck passed after restart on 2026-09-17 against the isolated QA database.
Run `ef176fd9-f402-4e19-a02c-4d3c01e11975`, vehicle 57, assignment 10:
fractional/string minutes returned 400; mixed legacy/v2 fields returned 400 even during
an active fault; a valid duplicate returned 409. Both MINOR and ASSISTANCE_REQUIRED
completed with RECOVERED / RESTORED, with waiting and repair phases observed.
The two-item, four-stop assignment reached COMPLETED; both items were DELIVERED,
all four actions completed, and inventory changed from 10 to 8. This is API/database
evidence, not a substitute for the pending map refresh/movement acceptance.
Evidence: `breakdown-v2-api-evidence.json` in the local visualization output folder.
The QA run was then reset for a separate browser case; its archived run remains saved.

## Delivered contract

`POST /api/simulation/random-events/trigger` retains `eventType`, `vehicleId`, and legacy
`durationMinutes`. Breakdown v2 requests add `breakdownLevel`, `rescueWaitMinutes`, and
`repairMinutes`; v2 fields cannot be combined with `durationMinutes`, and congestion rejects
all breakdown fields. Defaults are MINOR `0 + 60` and ASSISTANCE_REQUIRED `30 + 90` minutes.
Non-zero stages are limited to 30..180 minutes, totals to 240, and MINOR wait to zero.

Event records and `RandomEventDTO` expose `breakdownLevel`, `breakdownPhase`,
`rescueWaitMinutes`, `repairMinutes`, `repairStartTime`, `recoveryProcessedTime`,
`recoveryOutcome`, and `breakdownRuleVersion`. The fixed enums are MINOR /
ASSISTANCE_REQUIRED and WAITING_RESCUE / REPAIRING / RECOVERED. Persistence additionally
captures `originalAssignmentStatus`, `originalLegIndex`, and `originalDrivingPhaseKey`, along
with the existing previous vehicle state, to prevent stale restoration.

Fresh presets store this optional scene object:

```json
{
  "breakdownPolicy": {
    "version": "breakdown-v2",
    "minorProbability": 0.7,
    "minorRepairMin": 30,
    "minorRepairMax": 60,
    "rescueWaitMin": 30,
    "rescueWaitMax": 60,
    "assistanceRepairMin": 60,
    "assistanceRepairMax": 120
  }
}
```

Imported or stored scenes without the object remain legacy. Automatic event occurrence still
uses the existing random-event decision stream and `.02` hourly breakdown probability; only
v2 breakdown detail uses the independent seed/loop/vehicle stream.

## Recovery behavior

Phases use half-open simulation-time boundaries. A large tick can move directly through both
stages, `resolvedTime` remains the planned model end, and `recoveryProcessedTime` records the
tick that observed completion. Repeated processing of a stale resolved object is a no-op.
Restoration occurs only when the original vehicle, assignment, assignment status, pending leg,
and driving phase still match. Failure outcomes include `VEHICLE_MISSING`,
`VEHICLE_STATUS_CHANGED`, `ASSIGNMENT_CHANGED`, `ASSIGNMENT_STATUS_CHANGED`,
`ASSIGNMENT_STAGE_CHANGED`, and `DRIVING_PHASE_CHANGED`; success is `RESTORED`.
Historical records whose new guard fields are null keep legacy restoration behavior, while
new events created through the legacy request capture the available guards.

## TDD and verification evidence

RED was observed before production edits: the focused Maven compile reported 30 missing-symbol
errors for the new enums, fields, policy, DTO accessors, and service overload. A later behavioral
RED caught non-idempotent repeat recovery (`RESTORED` was overwritten by
`VEHICLE_STATUS_CHANGED`), and another test run caught a strict Mockito unused-stub error in the
automatic-policy fixture. Each was corrected before proceeding.

Final directed command covered the random-event, lifecycle/state-transition, controller/DTO,
schema migration, driving-progress, weather lifecycle/cargo/monitor/assignment, and main-loop
classes. Result: **45 tests run, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

The build retains pre-existing warnings about duplicate springdoc dependency declarations and
Mockito dynamic agent attachment. No dependencies, ports, credentials, databases, or server
processes were changed.

## Review round 1

Three contract regressions and one integration edge were added test-first. RED showed that
Jackson accepted fractional minute values, malformed mixed fields reached vehicle lookup,
and a completed captured assignment was reported as `ASSIGNMENT_CHANGED`. A separate boundary
test showed an ACTIVE breakdown stopped blocking exactly at `plannedEndTime` before its recovery
tick.

Minute fields now use a field-scoped strict deserializer: only JSON integer tokens are accepted
for request `durationMinutes`, `rescueWaitMinutes`, and `repairMinutes`, and for all six integer
range fields in `breakdownPolicy`. Floating tokens including `30.0` and numeric strings are
rejected without changing global Jackson coercion. Request shape, mutual exclusion, defaults,
and duration limits are computed before enabled-state, vehicle, assignment, driving-state, or
active-event checks.

Recovery loads the captured assignment by ID first, verifies its vehicle and status, then checks
the currently active assignment identity. Thus COMPLETED/CANCELLED status changes remain
`ASSIGNMENT_STATUS_CHANGED` rather than being masked by the active-assignment query. An ACTIVE
breakdown continues blocking legacy arrival/state transitions at and after its planned end until
the event tick resolves it; congestion keeps its existing planned-end boundary behavior.

Review-round focused GREEN: **28 tests, 0 failures/errors/skips**. Final directed regression
GREEN: **50 tests, 0 failures/errors/skips; BUILD SUCCESS**.
