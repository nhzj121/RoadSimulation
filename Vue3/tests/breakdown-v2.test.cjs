const { test } = require('node:test')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const path = require('node:path')

const load = name => import(pathToFileURL(path.join(__dirname, `../src/utils/${name}.js`)).href)

test('builds legacy congestion and staged breakdown payloads without mixing schemas', async () => {
  const { buildRandomEventPayload } = await load('breakdownPresentation')
  assert.deepEqual(buildRandomEventPayload('TRAFFIC_CONGESTION', 4, { durationMinutes: 60 }), {
    eventType: 'TRAFFIC_CONGESTION', vehicleId: 4, durationMinutes: 60
  })
  assert.deepEqual(buildRandomEventPayload('VEHICLE_BREAKDOWN', 4, {
    breakdownLevel: 'ASSISTANCE_REQUIRED', rescueWaitMinutes: 30, repairMinutes: 90
  }), { eventType: 'VEHICLE_BREAKDOWN', vehicleId: 4, breakdownLevel: 'ASSISTANCE_REQUIRED', rescueWaitMinutes: 30, repairMinutes: 90 })
})

test('production random-event API posts the constructed body at the HTTP boundary', async () => {
  const { createRandomEventApi } = await import(pathToFileURL(path.join(__dirname, '../src/api/randomEventApiFactory.js')).href)
  const calls = []
  const api = createRandomEventApi({
    async post(url, body) { calls.push({ url, body }); return { data: { data: { eventId: 7 } } } },
    async get() { return { data: { data: [] } } }
  })
  assert.deepEqual(await api.trigger('VEHICLE_BREAKDOWN', 5, { breakdownLevel: 'MINOR', rescueWaitMinutes: 0, repairMinutes: 60 }), { eventId: 7 })
  assert.deepEqual(calls[0], { url: '/api/simulation/random-events/trigger', body: { eventType: 'VEHICLE_BREAKDOWN', vehicleId: 5, breakdownLevel: 'MINOR', rescueWaitMinutes: 0, repairMinutes: 60 } })
})

test('history API and recent rows expose restored and guarded recovery outcomes', async () => {
  const { createRandomEventApi } = await import(pathToFileURL(path.join(__dirname, '../src/api/randomEventApiFactory.js')).href)
  const { recentBreakdownRows } = await load('breakdownPresentation')
  const calls = []
  const history = [
    { eventId: 2, eventType: 'VEHICLE_BREAKDOWN', status: 'RESOLVED', breakdownPhase: 'RECOVERED', recoveryOutcome: 'ASSIGNMENT_CHANGED', licensePlate: 'B' },
    { eventId: 1, eventType: 'VEHICLE_BREAKDOWN', status: 'RESOLVED', breakdownPhase: 'RECOVERED', recoveryOutcome: 'RESTORED', licensePlate: 'A' }
  ]
  const api = createRandomEventApi({ async get(url, config) { calls.push({url, config}); return { data: { data: history } } } })
  assert.deepEqual(await api.getHistory(5), history)
  assert.deepEqual(calls, [{ url: '/api/simulation/random-events/history', config: { params: { limit: 5 } } }])
  const rows = recentBreakdownRows(history)
  assert.match(rows[0].description, /任务已变化，未自动恢复/)
  assert.match(rows[1].description, /原运输任务已恢复/)
  assert.doesNotMatch(rows[0].description, /原运输任务已恢复/)
})

test('active to empty transition refreshes history and exposes the resolved result', async () => {
  const { activeEventSignature, createEventHistoryTransitionHandler, recentBreakdownRows } = await load('breakdownPresentation')
  let rows = []
  let calls = 0
  const onTransition = createEventHistoryTransitionHandler(async () => {
    calls += 1
    rows = recentBreakdownRows([{ eventId: 4, eventType: 'VEHICLE_BREAKDOWN', status: 'RESOLVED', breakdownPhase: 'RECOVERED', recoveryOutcome: 'RESTORED' }])
  })
  const active = activeEventSignature([{ eventId: 4, status: 'ACTIVE' }])
  const empty = activeEventSignature([])
  await onTransition(empty, active)
  assert.equal(calls, 1)
  assert.match(rows[0].description, /原运输任务已恢复/)
  await onTransition(empty, empty)
  assert.equal(calls, 1)
})

test('validates integer stage bounds, minor wait and total staged duration', async () => {
  const { validateRandomEventInput } = await load('breakdownPresentation')
  assert.equal(validateRandomEventInput('VEHICLE_BREAKDOWN', { breakdownLevel: 'MINOR', rescueWaitMinutes: 30, repairMinutes: 60 }), '轻微故障无需等待救援')
  assert.match(validateRandomEventInput('VEHICLE_BREAKDOWN', { breakdownLevel: 'ASSISTANCE_REQUIRED', rescueWaitMinutes: 180, repairMinutes: 90 }), /240/)
  assert.match(validateRandomEventInput('VEHICLE_BREAKDOWN', { breakdownLevel: 'MINOR', rescueWaitMinutes: 0, repairMinutes: 31.5 }), /整数分钟/)
  assert.equal(validateRandomEventInput('VEHICLE_BREAKDOWN', { breakdownLevel: 'MINOR', rescueWaitMinutes: 0, repairMinutes: 60 }), null)
  assert.equal(validateRandomEventInput('VEHICLE_BREAKDOWN', { breakdownLevel: 'MINOR', rescueWaitMinutes: 0, repairMinutes: 45 }), null)
  assert.equal(validateRandomEventInput('TRAFFIC_CONGESTION', { durationMinutes: 45 }), null)
})

test('presents staged phases, legacy repairs and recovery outcomes distinctly', async () => {
  const { describeBreakdown } = await load('breakdownPresentation')
  assert.match(describeBreakdown({ breakdownLevel: 'ASSISTANCE_REQUIRED', breakdownPhase: 'WAITING_RESCUE', rescueWaitMinutes: 30, repairMinutes: 90 }), /等待模拟救援.*30 分钟/)
  assert.match(describeBreakdown({ breakdownLevel: 'ASSISTANCE_REQUIRED', breakdownPhase: 'REPAIRING', repairMinutes: 90, repairStartTime: '2026-09-17T10:00:00' }), /维修中.*90 分钟/)
  assert.match(describeBreakdown({ eventType: 'VEHICLE_BREAKDOWN', breakdownLevel: null }), /原版故障维修/)
  assert.match(describeBreakdown({ breakdownPhase: 'RECOVERED', recoveryOutcome: 'ASSIGNMENT_STAGE_CHANGED' }), /维修已完成.*任务阶段已变化，未自动恢复/)
})

test('weather breakdown text uses v2 policy while old saved scenes remain legacy', async () => {
  const { describeBreakdownPolicy } = await load('breakdownPresentation')
  assert.match(describeBreakdownPolicy({ version: 'breakdown-v2', minorProbability: .7, minorRepairMin: 30, minorRepairMax: 60, rescueWaitMin: 30, rescueWaitMax: 60, assistanceRepairMin: 60, assistanceRepairMax: 120 }), /轻微 70%.*30–60.*救援等待 30–60.*维修 60–120/)
  assert.equal(describeBreakdownPolicy(null, { minDurationMinutes: 60, maxDurationMinutes: 120 }), '原版故障维修 60–120 分钟')
})

test('weather polling selects active recovery while non-weather keeps new-only behavior', async () => {
  const { assignmentPollingMode } = await load('weatherRouteCache')
  assert.equal(assignmentPollingMode({ runId: 'weather-run' }), 'active')
  assert.equal(assignmentPollingMode(null), 'new')
})

test('weather live monitor owns zeros/status/load and rejects another assignment', async () => {
  const { mergeLiveVehicleDisplay } = await load('liveVehicleDisplay')
  const cached = { assignmentId: 8, vehicleId: 2, status: 'TRANSPORT_DRIVING', currentLoad: 9, currentVolume: 4, goodsName: '木材' }
  const liveVehicle = { vehicleId: 2, assignmentId: 8, status: 'BREAKDOWN', currentLoad: 0, currentVolume: 0, actionDescription: '车辆故障' }
  const liveAssignment = { assignmentId: 8, vehicleId: 2, quantity: 0, status: 'IN_PROGRESS' }
  const result = mergeLiveVehicleDisplay(cached, liveVehicle, liveAssignment)
  assert.equal(result.vehicleInfo.currentLoad, 0)
  assert.equal(result.vehicleInfo.currentVolume, 0)
  assert.equal(result.currentStatus, 'BREAKDOWN')
  assert.equal(result.assignment.quantity, 0)
  const isolated = mergeLiveVehicleDisplay(cached, { ...liveVehicle, assignmentId: 99 }, liveAssignment)
  assert.equal(isolated.vehicleInfo.currentLoad, 9)
  assert.equal(isolated.assignment.assignmentId, 8)
})

function memoryStorage() {
  const values = new Map()
  return { get length(){ return values.size }, key(i){ return [...values.keys()][i] ?? null }, getItem(k){ return values.get(k) ?? null }, setItem(k,v){ values.set(k,String(v)) }, removeItem(k){ values.delete(k) }, values }
}

test('route cache roundtrips valid geometry and rejects corrupt, mismatched and oversized records', async () => {
  const { createWeatherRouteCache } = await load('weatherRouteCache')
  const storage = memoryStorage()
  const cache = createWeatherRouteCache(storage)
  const route = { path: [[104.1,30.1],[104.2,30.2]], distance: 123 }
  assert.equal(cache.write('run-a','a_stage1',[104,30],[105,31],route), true)
  assert.deepEqual(cache.read('run-a','a_stage1',[104,30],[105,31]), route)
  assert.equal(cache.read('run-b','a_stage1',[104,30],[105,31]), null)
  storage.setItem('road-simu:weather-route:v1:bad', '{no')
  assert.equal(cache.read('run-a','bad',[104,30],[105,31]), null)
  assert.equal(cache.write('run-a','huge',[104,30],[105,31],{ path: Array.from({length: 6000},()=>[104,30]), distance: 1 }), false)
})

test('route cache tolerates unavailable storage and clears only owned records for selected run', async () => {
  const { createWeatherRouteCache } = await load('weatherRouteCache')
  const broken = { getItem(){ throw new Error('blocked') }, setItem(){ throw new Error('quota') }, removeItem(){ throw new Error('blocked') }, get length(){ throw new Error('blocked') } }
  const cache = createWeatherRouteCache(broken)
  assert.equal(cache.read('run','key',[104,30],[105,31]), null)
  assert.equal(cache.write('run','key',[104,30],[105,31],{path:[[104,30],[105,31]]}), false)
  const storage = memoryStorage(); storage.setItem('unrelated','keep')
  const good = createWeatherRouteCache(storage)
  good.write('run-a','x',[104,30],[105,31],{path:[[104,30],[105,31]]})
  good.write('run-b','x',[104,30],[105,31],{path:[[104,30],[105,31]]})
  good.clearRun('run-a')
  assert.equal(storage.getItem('unrelated'),'keep')
  assert.equal(good.read('run-a','x',[104,30],[105,31]),null)
  assert.ok(good.read('run-b','x',[104,30],[105,31]))
})
