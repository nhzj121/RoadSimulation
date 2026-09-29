# Vehicle Scrap and Replacement Continuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a third breakdown severity that scraps the original vehicle for the current run, deterministically reserves a compatible idle vehicle, and continues the same assignment after a simulation-time handoff.

**Architecture:** Keep the original Assignment, shipment items, completed nodes, and inventory state. A replacement workflow owned by the random-event service locks and reserves candidates, while a focused capacity selector and driving-progress transfer API keep selection and handoff independently testable. Replacement attempts are persisted separately for audit; the frontend remains a backend-driven visualization.

**Tech Stack:** Java 17, Spring Boot/JPA, JUnit 5/Mockito, Vue 3, Element Plus, Node test runner, Vite.

**Spec:** User-approved “车辆报废与换车续运实施计划” in the task conversation.

## Global Constraints

- Existing MINOR and ASSISTANCE_REQUIRED behavior and stored breakdown-v2 scenes remain unchanged.
- Fresh scenes use breakdown-v3 weights 0.60 / 0.30 / 0.10; total event probability remains 0.02/hour.
- Replacement wait defaults to 60 simulation minutes, accepts integer 30..180; generated range is 60..90.
- Keep the same Assignment, ShipmentItem, and node identities; no inventory replay.
- Scrap lasts only for the current run; simulation reset restores vehicles to IDLE.
- No real replacement travel route, cost-model changes, precise split-leg mileage, or comparison-experiment integration.
- Keep ports 8080/5173 and use only the isolated QA database for live verification; never persist credentials.

---

### Task 1: Backend replacement domain and transactional lifecycle

**Files:**
- Modify the vehicle, random-event, weather-policy, DTO, repository, reset, monitor, and event-service backend files required by the spec.
- Create a focused replacement attempt entity/repository and capacity selector/service rather than growing unrelated dispatch code.
- Test in focused backend test classes for selection, lifecycle, controller binding, persistence, monitoring, reset, and v2 compatibility.

**Interfaces:**
- Produces `BreakdownLevel.REPLACEMENT_REQUIRED`; phases `WAITING_REPLACEMENT`, `REPLACEMENT_PREPARING`, `REPLACED`; statuses `SCRAPPED`, `RESERVED_REPLACEMENT`.
- Produces manual request field `replacementWaitMinutes` and replacement fields in event/monitor/run DTOs.
- Produces deterministic capacity selection and an idempotent simulation-time handoff preserving remaining driving work.

- [ ] Write failing tests for remaining-capacity projection, deterministic two-dimensional selection, no-candidate retry, exclusive reservation, candidate invalidation, handoff, idempotency, assignment guard failure, reset, nullable planned end, v2 replay, v3 decision weights, controller validation, DTO/monitor/export fields.
- [ ] Run the focused tests and record expected RED failures caused by missing replacement symbols/behavior.
- [ ] Implement the minimum domain, migration, policy, selection, attempt history, handoff, progress transfer, state-machine guards, timeout protection, API and monitoring changes.
- [ ] Run focused tests, then the existing 51-test breakdown/weather regression set; fix regressions without changing legacy behavior.
- [ ] Commit backend work and write the task report with RED/GREEN evidence and known limitations.

### Task 2: Frontend replacement controls and vehicle handoff rendering

**Files:**
- Modify the random-event API/presentation/panel and monitor/map rendering code.
- Add small pure helpers for replacement presentation and assignment+vehicle render identity when useful.
- Extend frontend behavior tests.

**Interfaces:**
- Consumes Task 1 event/monitor fields and POST request contract.
- Produces a replacement-level form, phase/outcome history, SCRAPPED/RESERVED display, and assignment marker replacement keyed by assignmentId+vehicleId.

- [ ] Write failing behavior tests for the request body, validation, three phases/outcomes, history-only non-impact, and assignment vehicle replacement identity.
- [ ] Run tests and record RED failures.
- [ ] Implement the form and backend-authoritative presentation; detect changed vehicle ownership, remove the old animation/marker, and restore the new vehicle at backend progress without locally advancing business state.
- [ ] Run all frontend tests and Vite build; inspect desktop/mobile controls with intercepted API data.
- [ ] Commit frontend work and write the task report.

### Task 3: Isolated QA, documentation, and final review

**Files:**
- Modify/add only focused handoff and verification documentation.
- Store generated screenshots/evidence outside the repository visualization directory.

**Interfaces:**
- Consumes Tasks 1–2 and produces reproducible API/database/browser evidence plus an honest limitation record.

- [ ] Start the latest backend on 8080 against the isolated QA database and frontend on 5173.
- [ ] Verify no-candidate waiting, later deterministic reservation, 60-minute preparation, atomic handoff, task/cargo/node/inventory continuity, continuation to delivery, and reset recovery.
- [ ] Verify refresh and marker replacement in Edge; if AMap is unavailable, capture failed requests and mark map acceptance incomplete rather than substituting component tests.
- [ ] Run fresh backend focused regression tests, frontend tests, Vite build, and diff checks.
- [ ] Update handoff/report documents with exact results, commits, evidence paths, and remaining limitations.
- [ ] Dispatch whole-branch review and address all Critical/Important findings before completion.
