const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const source = fs.readFileSync(path.join(__dirname, '../src/components/MapContainer.vue'), 'utf8')
const classes = source.slice(source.indexOf('class VehicleAnimation {'), source.indexOf('// ==================== 车辆动画管理器类'))

function createAnimation(vrp = false) {
  let now = 100
  const context = {
    console: { log() {}, info() {}, warn() {}, error() {} },
    Math,
    Number,
    Map,
    performance: { now: () => now },
    requestAnimationFrame: () => 1,
    cancelAnimationFrame() {},
    setTimeout() {},
    clearTimeout() {},
    request: { post: async () => ({ data: {} }) }
  }
  vm.createContext(context)
  vm.runInContext(`${classes}\nthis.Animation = VehicleAnimation; this.Vrp = VrpVehicleAnimation;`, context)

  const assignment = { assignmentId: 5, vehicleId: 8, licensePlate: 'TEST', endPOIId: 20 }
  const statuses = []
  const marker = { position: null, setPosition(position) { this.position = [...position] } }
  const route = {
    assignment,
    stage1Path: [[0, 0], [0.01, 0]],
    stage2Path: [[0.01, 0], [0.02, 0]],
    movingMarker: marker
  }
  route.stages = [
    { path: route.stage1Path, nodeInfo: { poiId: 10, actionType: 'LOAD' } },
    { path: route.stage2Path, nodeInfo: { poiId: 20, actionType: 'UNLOAD' } }
  ]
  const animation = new (vrp ? context.Vrp : context.Animation)(assignment, route, {
    updateVehicleStatus: (id, status) => statuses.push(status)
  })
  return { animation, statuses, marker, setNow(value) { now = value } }
}

function snapshot(overrides = {}) {
  return {
    vehicleId: 8,
    assignmentId: 5,
    status: 'TRANSPORT_DRIVING',
    drivingStatus: 'TRANSPORT_DRIVING',
    drivingPhaseKey: 'phase-1',
    drivingLegIndex: 1,
    drivingProgress: 0.5,
    effectiveSpeedFactor: 0.32,
    ...overrides
  }
}

test('backend snapshots retain identity without overwriting frontend stage, progress, position or clock', () => {
  const { animation } = createAnimation()
  animation.currentStage = 1
  animation.currentProgress = 0.2
  animation.currentPosition = [0.002, 0]
  animation.animationTime = 11

  animation.updateDrivingSnapshot(snapshot({ drivingLegIndex: 1, drivingProgress: 0.8 }))

  assert.equal(animation.drivingSnapshot.drivingPhaseKey, 'phase-1')
  assert.equal(animation.currentStage, 1)
  assert.equal(animation.currentProgress, 0.2)
  assert.deepEqual(animation.currentPosition, [0.002, 0])
  assert.equal(animation.animationTime, 11)
  assert.equal(animation.eventSpeedFactor, 1)
})

test('user speed, weather and congestion factors compose on the frontend clock', () => {
  const { animation, setNow } = createAnimation()
  animation.speedFactor = 20
  animation.updateEnvironmentImpact({ speedFactor: 0.5 })
  animation.updateEventImpact({ eventType: 'TRAFFIC_CONGESTION', status: 'ACTIVE', speedFactor: 0.25 })
  animation.lastUpdateTime = 100
  setNow(1100)

  animation._animate()

  assert.equal(animation.animationTime, 2.5)
  assert(animation.currentProgress > 0)
  assert(animation.currentProgress < 1)
})

test('breakdown freezes the current frontend position and resolved event resumes movement', () => {
  const { animation, statuses, marker, setNow } = createAnimation()
  animation.start()
  const before = [...marker.position]
  animation.updateEventImpact({ eventType: 'VEHICLE_BREAKDOWN', status: 'ACTIVE', breakdownLevel: 'MINOR', speedFactor: 0 })
  setNow(1100)
  animation._animate()
  assert.deepEqual(marker.position, before)
  assert.equal(statuses.at(-1), 'BREAKDOWN')

  animation.updateEventImpact({ eventType: 'VEHICLE_BREAKDOWN', status: 'RESOLVED', breakdownLevel: 'MINOR', speedFactor: 0 })
  setNow(2100)
  animation._animate()
  assert.notDeepEqual(marker.position, before)
})

test('resolved replacement history has no movement impact and active replacement does not invent BREAKDOWN status', () => {
  const { animation, statuses } = createAnimation()
  const initialStatuses = [...statuses]
  animation.updateEventImpact({ eventType: 'VEHICLE_BREAKDOWN', status: 'RESOLVED', breakdownLevel: 'REPLACEMENT_REQUIRED', speedFactor: 0 })
  assert.equal(animation.eventSpeedFactor, 1)
  assert.deepEqual(statuses, initialStatuses)
  animation.updateEventImpact({ eventType: 'VEHICLE_BREAKDOWN', status: 'ACTIVE', breakdownLevel: 'REPLACEMENT_REQUIRED', speedFactor: 0 })
  assert.equal(animation.eventSpeedFactor, 0)
  assert.deepEqual(statuses, initialStatuses)
})

for (const status of ['SCRAPPED', 'RESERVED_REPLACEMENT']) {
  test(`${status} remains a backend-authoritative exceptional stop`, () => {
    const { animation, marker, setNow } = createAnimation()
    animation.start()
    const before = [...marker.position]
    animation.updateDrivingSnapshot(snapshot({ status }))
    setNow(1100)
    animation._animate()
    assert.equal(animation.backendVehicleStatus, status)
    assert.deepEqual(marker.position, before)
    assert.equal(animation.animationTime, 0)
  })
}

test('VRP snapshots do not force the frontend leg or runtime load', () => {
  const { animation } = createAnimation(true)
  animation.currentStageIndex = 0
  animation.runtimeLoad = 4
  animation.runtimeVolume = 1

  animation.updateDrivingSnapshot(snapshot({ drivingLegIndex: 1, currentLoad: 17, currentVolume: 3 }))

  assert.equal(animation.currentStageIndex, 0)
  assert.equal(animation.runtimeLoad, 4)
  assert.equal(animation.runtimeVolume, 1)
})

test('missing snapshot does not freeze frontend-owned animation', () => {
  const { animation, marker, setNow } = createAnimation()
  animation.start()
  const before = [...marker.position]
  animation.updateDrivingSnapshot(null)
  setNow(1100)
  animation._animate()
  assert.notDeepEqual(marker.position, before)
})

test('a stale other-assignment snapshot cannot replace this animation snapshot', () => {
  const { animation } = createAnimation()
  animation.updateDrivingSnapshot(snapshot({ assignmentId: 99 }))
  assert.equal(animation.drivingSnapshot, null)
  assert.equal(animation.currentStage, 1)
})

test('frontend display no longer contains backend-progress caps or authoritative animation loop', () => {
  assert.equal(source.includes('_animateAuthoritative('), false)
  assert.equal(source.includes('visualLimit'), false)
  assert.equal(source.includes('animationTime = progress *'), false)
})

for (const running of [true, false]) {
  test(`refresh restores ${running ? 'running' : 'paused'} weather run without starting backend`, async () => {
    const calls = []
    const ctx = {
      updateVehicleInfo: async () => {},
      monitorWeather: { value: { runId: 'saved-run' } },
      isExperimentRunActive: { value: false },
      simulationController: { getConfig: async () => ({ success: true, data: { running } }) },
      beginSimulationGeneration: () => 7,
      isSimulationRunning: { value: false },
      restoringWeatherView: false,
      animationManager: { isPaused: false, startAll: () => calls.push('start'), pauseAll: () => calls.push('pause') },
      fetchCurrentAssignments: async generation => {
        assert.equal(generation, 7)
        assert.equal(ctx.restoringWeatherView, true)
        assert.equal(ctx.animationManager.isPaused, !running)
        calls.push('draw')
      },
      isActiveTransportGeneration: () => running,
      startSimulationTimer: () => calls.push('timer'),
      arrivalMonitor: { startMonitoring: () => calls.push('monitor') },
      getVehiclePositions() {},
      getPOIList() {}
    }
    vm.createContext(ctx)
    const method = source.slice(source.indexOf('const restoreWeatherRunView ='), source.indexOf('onMounted(() => {', source.indexOf('const restoreWeatherRunView =')))
    vm.runInContext(`${method}\nthis.restore = restoreWeatherRunView;`, ctx)
    await ctx.restore()
    assert.equal(ctx.restoringWeatherView, false)
    assert.equal(ctx.isSimulationRunning.value, running)
    assert.deepEqual(calls, running ? ['draw', 'start', 'timer', 'monitor'] : ['draw', 'pause'])
  })
}
