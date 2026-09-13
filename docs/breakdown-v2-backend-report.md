# Breakdown v2 backend report

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
