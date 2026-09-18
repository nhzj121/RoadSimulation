# Breakdown v2 frontend report

## Delivered

- Random-event controls now keep congestion on the legacy `durationMinutes` contract and send breakdowns with `breakdownLevel`, `rescueWaitMinutes`, and `repairMinutes` only. Defaults are MINOR `0 + 60` and ASSISTANCE_REQUIRED `30 + 90`; client validation accepts any integer minute value within the documented ranges (including 45) and enforces the total constraint while backend errors remain visible.
- Active vehicle impact details distinguish waiting for simulated rescue, repairing, recovered outcomes, and legacy repairs. Breakdown speed remains visibly zero and the existing `车辆故障：暂停行驶` text is retained.
- The random-event panel reads the existing history endpoint and shows the five most recent resolved breakdowns. `RESTORED` is presented as task restoration; guarded outcomes explicitly state why the original task was not restored. It refreshes once when the active event ID set changes, including ACTIVE → empty, without a separate timer. History is explanatory only and never feeds animation speed/state.
- Weather scenario copy describes `breakdown-v2` policy ranges; saved scenarios without a policy are explicitly presented as legacy.
- In weather-run mode the floating vehicle panel merges the matching live vehicle and assignment as authority, including zero load/volume/quantity and live status, while refusing cross-assignment data. Other modes retain the previous display path.
- Road route geometry is cached in bounded, validated, run-scoped `sessionStorage` records. Corruption, unavailable/quota storage, invalid coordinates, and oversized paths fail closed without touching unrelated keys. Paused refresh reads cache before network planning and never resumes the backend. Missing routes are indicated and all active assignments are retried on explicit resume.
- While a weather run is active, the regular timer polls `/assignments/active` and draws only assignments absent from local animations; the existing draw lock prevents overlap. Non-weather and experiment behavior keeps the `/assignments/new` path.
- Moving marker content carries one stable `data-vehicle-id`, including after status icon replacement.

## TDD evidence

RED command:

```text
node --test tests/breakdown-v2.test.cjs
```

Initial result: 7/7 failed because the requested production helpers did not exist. A later HTTP-boundary test failed before `createRandomEventApi` existed as a Node-loadable production seam. Each was then implemented and rerun green.

GREEN command:

```text
node --test tests
```

Result after review fixes: 21 tests passed, 0 failed. Coverage includes old/new request bodies through the production API factory, arbitrary integer-minute validation, the history HTTP boundary, ACTIVE → empty refresh and restored/guarded rows, weather-vs-non-weather polling selection, phase/legacy/outcome presentation, authoritative zeros and assignment isolation, route-cache roundtrip/corruption/oversize/unavailable/reset isolation, paused restored position, breakdown freeze, and stale-assignment rejection.

Build command:

```text
npx vite build --outDir D:\桌面\road_simu\RoadSimulation\.worktrees\random-transport-events-week1\.task2-vite-build --emptyOutDir
```

Result: exit 0, 1517 modules transformed. The temporary output directory was removed after verification. Vite reported only the pre-existing large-chunk advisory.

## Browser QA

Browser plugin was not available, so the installed Playwright runtime launched Microsoft Edge (`channel: msedge`). The harness intercepted monitor/config/assignment data and the random-event POST; it did not start, stop, reset, or otherwise mutate the backend simulation.

- URL: `http://127.0.0.1:5173/`
- Viewports: 1440×1000 and 390×844
- Page meaningful/not blank: pass
- Framework overlay: absent
- Console/page errors: 0
- Interaction: selected the controlled vehicle, changed fault level to ASSISTANCE_REQUIRED, observed rescue `30` and repair `90`, submitted, and observed the success message
- Captured POSTs: 2/2 exact staged bodies, with no `durationMinutes`
- Evidence: `breakdown-v2-frontend-desktop.png` and `breakdown-v2-frontend-mobile.png` in the external visualization directory

The new form fits and remains readable at both viewports. The existing whole-page mobile header is cramped; this predates and is outside the fault-control scope.

## Remaining acceptance boundary

The parent task owns destructive/long-running real simulation acceptance. This frontend task did not mutate its database or lifecycle. Real map route recovery depends on a route having been successfully planned and cached in the same tab before refresh; arbitrary new browsers intentionally receive no invented straight-line geometry.
