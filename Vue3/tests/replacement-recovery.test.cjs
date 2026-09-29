const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { pathToFileURL } = require('node:url')

const componentPath = path.join(__dirname, '../src/components/MapContainer.vue')
const source = fs.readFileSync(componentPath, 'utf8').replace(/\r\n/g, '\n')

const sliceBetween = (start, end) => {
  const startIndex = source.indexOf(start)
  assert.notEqual(startIndex, -1, `missing source marker: ${start}`)
  const endIndex = source.indexOf(end, startIndex)
  assert.notEqual(endIndex, -1, `missing source marker: ${end}`)
  return source.slice(startIndex, endIndex)
}

const loadOwnership = () => import(pathToFileURL(path.join(__dirname, '../src/utils/assignmentRenderOwnership.js')).href)
const flushAsync = () => new Promise(resolve => setImmediate(resolve))

function createClassHarness({ vrp = false, vehicleStatus = 'TRANSPORT_DRIVING', event = null, replacementRecovery = true, assignmentState = {}, arrivalHandler = null } = {}) {
  const statuses = []
  const posts = []
  const context = {
    console: { log() {}, info() {}, warn() {}, error() {} },
    Math,
    Number,
    Map,
    performance: { now: () => 1_000_000 },
    requestAnimationFrame: () => 1,
    cancelAnimationFrame() {},
    setTimeout: callback => { callback(); return 1 },
    clearTimeout() {},
    request: { post: async (...args) => { posts.push(args); return { data: {} } } },
    isStoppedVehicleStatus: status => ['BREAKDOWN', 'SCRAPPED', 'RESERVED_REPLACEMENT'].includes(status),
    handleVehicleArrived: async (...args) => {
      posts.push(['arrival', ...args])
      return arrivalHandler ? arrivalHandler(...args) : 'acknowledged'
    },
    map: null,
    activeRoutes: { value: new Map() }
  }
  vm.createContext(context)
  const classes = sliceBetween('class VehicleAnimation {', 'const vehicleStatusManager = ref(null);')
  vm.runInContext(`${classes}\nthis.AnimationManager = VehicleAnimationManager;`, context)

  const statusManager = {
    updateVehicleStatus(id, status) { statuses.push(status) }
  }
  const manager = new context.AnimationManager(statusManager)
  manager.setEventImpacts(event ? [event] : [])

  const assignment = {
    assignmentId: 88,
    vehicleId: 18,
    licensePlate: '川A018',
    vehicleStatus,
    vehicleCurrentLon: 104.015,
    vehicleCurrentLat: 30.6,
    currentActionIndex: 1,
    currentLoad: 12,
    currentVolume: 3,
    endPOIId: 20,
    vrp,
    ...assignmentState
  }
  const marker = { position: null, setPosition(position) { this.position = [...position] } }
  const routeData = {
    assignment,
    manager,
    movingMarker: marker,
    replacementRecovery,
    stage1Path: [[104, 30.6], [104.01, 30.6]],
    stage2Path: [[104.01, 30.6], [104.02, 30.6]],
    stages: vrp ? [
      { path: [[104, 30.6], [104.01, 30.6]], nodeInfo: { poiId: 10, actionType: 'LOAD', weightDelta: 12, volumeDelta: 3 } },
      { path: [[104.01, 30.6], [104.02, 30.6]], nodeInfo: { poiId: 20, actionType: 'UNLOAD', weightDelta: -12, volumeDelta: -3 } }
    ] : undefined
  }
  const animation = manager.addAnimation(assignment, routeData)
  return { animation, manager, statuses, posts, marker }
}

test('durable ordinary replacement recovery acknowledges exactly once only after backend readiness', async () => {
  const { animation, manager, posts } = createClassHarness({
    replacementRecovery: true,
    vehicleStatus: 'UNLOADING',
    assignmentState: {
      replacementRecovery: true,
      replacementEventId: 101,
      currentOwnerVehicleId: 18,
      replacementArrivalReady: false
    }
  })

  manager.setDrivingSnapshots([{
    vehicleId: 18,
    assignmentId: 88,
    assignmentIds: [88],
    status: 'UNLOADING',
    replacementRecovery: true,
    replacementEventId: 101,
    currentOwnerVehicleId: 18,
    replacementArrivalReady: false
  }], false)
  await Promise.resolve()
  assert.deepEqual(posts, [])

  const ready = {
    vehicleId: 18,
    assignmentId: 88,
    assignmentIds: [88],
    status: 'UNLOADING',
    replacementRecovery: true,
    replacementEventId: 101,
    currentOwnerVehicleId: 18,
    replacementArrivalReady: true
  }
  manager.setDrivingSnapshots([ready], false)
  manager.setDrivingSnapshots([ready], false)
  await Promise.resolve()
  await Promise.resolve()

  const arrivals = posts.filter(call => call[0] === 'arrival')
  assert.equal(arrivals.length, 1)
  assert.deepEqual(arrivals[0], ['arrival', 88, 18, 20, '川A018', 101])
  assert.equal(animation.isCompleted, false, 'ack does not replay or infer local animation completion')
})

test('blocked, failed and pending replacement arrivals release the latch until acknowledgement succeeds', async () => {
  const outcomes = ['blocked', 'failed', 'pending', new Error('network unavailable'), 'acknowledged']
  const { manager, posts } = createClassHarness({
    replacementRecovery: true,
    vehicleStatus: 'UNLOADING',
    assignmentState: {
      replacementRecovery: true,
      replacementEventId: 101,
      currentOwnerVehicleId: 18,
      replacementArrivalReady: false
    },
    arrivalHandler: async () => {
      const outcome = outcomes.shift()
      if (outcome instanceof Error) throw outcome
      return outcome
    }
  })
  const ready = {
    vehicleId: 18, assignmentId: 88, assignmentIds: [88], status: 'UNLOADING',
    replacementRecovery: true, replacementEventId: 101,
    currentOwnerVehicleId: 18, replacementArrivalReady: true
  }

  for (let attempt = 1; attempt <= 5; attempt += 1) {
    manager.setDrivingSnapshots([ready], false)
    await flushAsync()
    assert.equal(posts.filter(call => call[0] === 'arrival').length, attempt)
  }
  manager.setDrivingSnapshots([ready], false)
  await flushAsync()
  assert.equal(posts.filter(call => call[0] === 'arrival').length, 5, 'success permanently deduplicates later polls')
})

test('replacement arrival keeps one request in flight and retries after a non-success result', async () => {
  let resolveFirst
  let calls = 0
  const { manager, posts } = createClassHarness({
    replacementRecovery: true,
    vehicleStatus: 'UNLOADING',
    assignmentState: {
      replacementRecovery: true,
      replacementEventId: 101,
      currentOwnerVehicleId: 18,
      replacementArrivalReady: false
    },
    arrivalHandler: async () => {
      calls += 1
      if (calls === 1) return new Promise(resolve => { resolveFirst = resolve })
      return 'acknowledged'
    }
  })
  const ready = {
    vehicleId: 18, assignmentId: 88, assignmentIds: [88], status: 'UNLOADING',
    replacementRecovery: true, replacementEventId: 101,
    currentOwnerVehicleId: 18, replacementArrivalReady: true
  }

  manager.setDrivingSnapshots([ready], false)
  manager.setDrivingSnapshots([ready], false)
  await Promise.resolve()
  assert.equal(posts.filter(call => call[0] === 'arrival').length, 1)
  resolveFirst('blocked')
  await flushAsync()
  manager.setDrivingSnapshots([ready], false)
  await flushAsync()
  assert.equal(posts.filter(call => call[0] === 'arrival').length, 2)
})

test('VRP replacement recovery never emits the ordinary arrival acknowledgement', async () => {
  const { manager, posts } = createClassHarness({
    vrp: true,
    replacementRecovery: true,
    vehicleStatus: 'UNLOADING',
    assignmentState: {
      replacementRecovery: true,
      replacementEventId: 101,
      currentOwnerVehicleId: 18,
      replacementArrivalReady: true
    }
  })
  manager.setDrivingSnapshots([{
    vehicleId: 18, assignmentId: 88, assignmentIds: [88], status: 'UNLOADING',
    replacementRecovery: true, replacementEventId: 101,
    currentOwnerVehicleId: 18, replacementArrivalReady: true
  }], false)
  await Promise.resolve()
  assert.deepEqual(posts, [])
})

for (const vrp of [false, true]) {
  test(`running ${vrp ? 'VRP' : 'normal'} animation accepts non-weather SCRAPPED status from assignmentIds`, () => {
    const activeEvent = {
      vehicleId: 18,
      eventType: 'VEHICLE_BREAKDOWN',
      breakdownLevel: 'REPLACEMENT_REQUIRED',
      status: 'ACTIVE',
      speedFactor: 0
    }
    const { animation, manager, statuses, marker, posts } = createClassHarness({
      vrp,
      event: activeEvent,
      replacementRecovery: false
    })
    manager.setDrivingSnapshots([{
      vehicleId: 18,
      assignmentId: null,
      assignmentIds: [88],
      status: 'SCRAPPED'
    }], false)
    const stoppedPosition = marker.position && [...marker.position]
    manager.setEventImpacts([{ ...activeEvent, status: 'RESOLVED' }])
    animation.animationTime = 1_000_000
    animation._animate()

    assert.equal(animation.backendVehicleStatus, 'SCRAPPED')
    assert.equal(statuses.at(-1), 'SCRAPPED')
    assert.deepEqual(marker.position, stoppedPosition)
    assert.equal(animation.isCompleted, false)
    assert.deepEqual(posts, [])
  })
}

for (const vrp of [false, true]) {
  test(`owner replacement without phase snapshot stays backend-authoritative for ${vrp ? 'VRP' : 'normal'} animation`, () => {
    const { animation, statuses, posts, marker } = createClassHarness({ vrp })
    const actionStatuses = () => statuses.filter(status => ['LOADING', 'UNLOADING', 'WAITING'].includes(status))

    assert.equal(animation.replacementRecoveryMode, true)
    assert.equal(vrp ? animation.currentStageIndex : animation.currentStage, vrp ? 1 : 2)
    assert.deepEqual(marker.position, [104.015, 30.6])
    assert.deepEqual(actionStatuses(), [])

    animation.animationTime = 1_000_000
    animation._animate()
    assert.deepEqual(actionStatuses(), [])
    assert.deepEqual(posts, [])
    assert.equal(animation.isCompleted, false)
  })
}

for (const [vrp, status, eventStatus] of [
  [false, 'SCRAPPED', 'ACTIVE'],
  [false, 'SCRAPPED', 'RESOLVED'],
  [true, 'RESERVED_REPLACEMENT', 'ACTIVE'],
  [true, 'RESERVED_REPLACEMENT', 'RESOLVED']
]) {
  test(`${status} remains stopped without a phase snapshot after ${eventStatus.toLowerCase()} replacement event (${vrp ? 'VRP' : 'normal'})`, () => {
    const event = {
      vehicleId: 18,
      eventType: 'VEHICLE_BREAKDOWN',
      breakdownLevel: 'REPLACEMENT_REQUIRED',
      status: eventStatus,
      speedFactor: 0
    }
    const { animation, statuses, posts } = createClassHarness({ vrp, vehicleStatus: status, event })
    const before = vrp ? animation.currentStageIndex : animation.currentStage
    animation.animationTime = 1_000_000
    animation._animate()

    assert.equal(animation.backendVehicleStatus, status)
    assert.equal(statuses.at(-1), status)
    assert.equal(vrp ? animation.currentStageIndex : animation.currentStage, before)
    assert.equal(animation.isCompleted, false)
    assert.deepEqual(posts, [])
    assert.equal(statuses.some(value => ['LOADING', 'UNLOADING', 'WAITING', 'BREAKDOWN'].includes(value)), false)
  })
}

test('failed owner redraw remains on active polling until matching animation registers, then returns to new polling', async () => {
  const {
    assignmentRenderIdentity,
    reconcileAssignmentRenderOwner,
    createAssignmentRecoveryTracker
  } = await loadOwnership()

  const assignment = { assignmentId: 88, vehicleId: 18, licensePlate: '川A018' }
  const oldAssignment = { assignmentId: 88, vehicleId: 12, licensePlate: '川A012' }
  const animations = new Map([[88, { routeData: { assignment: oldAssignment } }]])
  const endpointCalls = []
  const scheduled = []
  let drawAttempt = 0
  let timerCallback = null

  const context = {
    console: { log() {}, info() {}, warn() {}, error() {} },
    Promise,
    Map,
    Set,
    assignmentRenderIdentity,
    reconcileAssignmentRenderOwner,
    createAssignmentRecoveryTracker,
    ref: value => ({ value }),
    simulationTimer: { value: null },
    simulationInterval: { value: 4000 },
    simulationGeneration: { value: 7 },
    isTransportAnimationActive: () => true,
    isActiveTransportGeneration: () => true,
    fetchSimulationCosts: async () => {},
    updateVehicleInfo: async () => {},
    checkAndCleanupCompletedAssignments: async () => {},
    isExperimentRunActive: { value: false },
    syncExperimentRunAfterStatusRefresh: async () => {},
    monitorAssignments: [assignment],
    monitorWeather: { value: null },
    assignmentPollingMode: () => 'new',
    animationManager: { animations },
    scheduleAssignmentDrawing(drawer, generation, label) { scheduled.push({ drawer, generation, label }) },
    fetchAndDrawNewAssignments: async () => { endpointCalls.push('/api/assignments/new') },
    request: {
      async get(url) {
        endpointCalls.push(url)
        return { data: [assignment] }
      }
    },
    map: {},
    drawnAssignmentIds: { value: new Set() },
    missingRouteAssignmentIds: new Set(),
    stats: {},
    clearRouteByAssignmentId(id) { animations.delete(id) },
    async drawTwoStageRouteForAssignment(current) {
      drawAttempt += 1
      if (drawAttempt === 1) return null
      const routeData = { assignment: current }
      animations.set(current.assignmentId, { routeData })
      return routeData
    },
    async drawMultiStageRouteForVrpAssignment() { throw new Error('unexpected VRP draw') },
    ElMessage: { error() {} },
    setInterval(callback) { timerCallback = callback; return 123 },
    clearInterval() {},
    arrivalMonitor: { stopMonitoring() {} }
  }
  vm.createContext(context)

  const recoveryMarker = '// ==================== assignment owner recovery polling ===================='
  const recoveryStart = source.indexOf(recoveryMarker)
  if (recoveryStart !== -1) {
    const recoveryEnd = source.indexOf('/**\n * 启动仿真定时器', recoveryStart)
    assert.notEqual(recoveryEnd, -1, 'missing end of recovery script section')
    vm.runInContext(source.slice(recoveryStart, recoveryEnd), context)
  }
  vm.runInContext(`${sliceBetween('const fetchCurrentAssignments =', '// 增量获取并绘制新Assignment')}\nthis.fetchCurrentAssignments = fetchCurrentAssignments;`, context)
  vm.runInContext(`${sliceBetween('const startSimulationTimer =', '/**\n * 停止仿真定时器')}\nthis.startTimer = startSimulationTimer;`, context)

  context.startTimer()

  await timerCallback()
  assert.equal(scheduled.at(-1).drawer, context.fetchCurrentAssignments)
  await scheduled.at(-1).drawer(7)
  assert.deepEqual(endpointCalls, ['/api/assignments/active'])
  assert.equal(animations.has(88), false)

  await timerCallback()
  assert.equal(scheduled.at(-1).drawer, context.fetchCurrentAssignments)
  await scheduled.at(-1).drawer(7)
  assert.deepEqual(endpointCalls, ['/api/assignments/active', '/api/assignments/active'])
  assert.equal(animations.get(88).routeData.assignment.vehicleId, 18)

  await timerCallback()
  assert.equal(scheduled.at(-1).drawer, context.fetchAndDrawNewAssignments)
  await scheduled.at(-1).drawer(7)
  assert.deepEqual(endpointCalls, ['/api/assignments/active', '/api/assignments/active', '/api/assignments/new'])
  assert.equal(drawAttempt, 2)
})

test('refresh without an owner tracker reconstructs static recovery from durable active assignment metadata', async () => {
  const { assignmentRenderIdentity, reconcileAssignmentRenderOwner, createAssignmentRecoveryTracker } = await loadOwnership()
  const assignment = {
    assignmentId: 88, vehicleId: 18, licensePlate: '川A018', replacementRecovery: true,
    replacementEventId: 101, currentOwnerVehicleId: 18, replacementArrivalReady: false
  }
  const animations = new Map()
  const scheduled = []
  let timerCallback
  let drawOptions
  const context = {
    console: { log() {}, info() {}, warn() {}, error() {} }, Promise, Map, Set,
    assignmentRenderIdentity, reconcileAssignmentRenderOwner, createAssignmentRecoveryTracker,
    ref: value => ({ value }), simulationTimer: { value: null }, simulationInterval: { value: 4000 },
    simulationGeneration: { value: 7 }, isTransportAnimationActive: () => true,
    isActiveTransportGeneration: () => true, fetchSimulationCosts: async () => {}, updateVehicleInfo: async () => {},
    checkAndCleanupCompletedAssignments: async () => {}, isExperimentRunActive: { value: false },
    syncExperimentRunAfterStatusRefresh: async () => {}, monitorAssignments: [assignment], monitorWeather: { value: null },
    assignmentPollingMode: () => 'new', animationManager: { animations },
    scheduleAssignmentDrawing(drawer, generation, label) { scheduled.push({ drawer, generation, label }) },
    fetchAndDrawNewAssignments: async () => { throw new Error('durable recovery must use active polling') },
    request: { async get(url) { assert.equal(url, '/api/assignments/active'); return { data: [assignment] } } },
    map: {}, drawnAssignmentIds: { value: new Set() }, missingRouteAssignmentIds: new Set(), stats: {},
    clearRouteByAssignmentId() {},
    async drawTwoStageRouteForAssignment(current, generation, options) {
      drawOptions = options
      const routeData = { assignment: current }
      animations.set(current.assignmentId, { routeData })
      return routeData
    },
    async drawMultiStageRouteForVrpAssignment() { throw new Error('unexpected VRP draw') },
    ElMessage: { error() {} }, setInterval(callback) { timerCallback = callback; return 123 }, clearInterval() {},
    arrivalMonitor: { stopMonitoring() {} }
  }
  vm.createContext(context)
  const recoveryMarker = '// ==================== assignment owner recovery polling ===================='
  const recoveryStart = source.indexOf(recoveryMarker)
  const recoveryEnd = source.indexOf('/**\n * 启动仿真定时器', recoveryStart)
  assert.notEqual(recoveryEnd, -1, 'missing end of recovery script section')
  vm.runInContext(source.slice(recoveryStart, recoveryEnd), context)
  vm.runInContext(`${sliceBetween('const fetchCurrentAssignments =', '// 增量获取并绘制新Assignment')}\nthis.fetchCurrentAssignments = fetchCurrentAssignments;`, context)
  vm.runInContext(`${sliceBetween('const startSimulationTimer =', '/**\n * 停止仿真定时器')}\nthis.startTimer = startSimulationTimer;`, context)

  context.startTimer()
  await timerCallback()
  assert.equal(scheduled.at(-1).drawer, context.fetchCurrentAssignments)
  await scheduled.at(-1).drawer(7)
  assert.equal(drawOptions.replacementRecovery, true)
  assert.equal(animations.get(88).routeData.assignment.replacementEventId, 101)
})

test('pending recovery identity clears when assignment disappears and when generation changes', async () => {
  const { createAssignmentRecoveryTracker } = await loadOwnership()
  const assignment = { assignmentId: 88, vehicleId: 18 }
  const tracker = createAssignmentRecoveryTracker()
  tracker.mark(assignment)
  const reassignedAgain = { assignmentId: 88, vehicleId: 19 }
  tracker.retainActive([reassignedAgain])
  assert.equal(tracker.isPending(reassignedAgain), true)
  assert.equal(tracker.confirmRegistered(assignment, assignment), false)
  assert.equal(tracker.confirmRegistered(reassignedAgain, reassignedAgain), true)

  tracker.mark(assignment)
  tracker.retainActive([])
  assert.equal(tracker.hasPending(), false)

  let aborts = 0
  let clears = 0
  const context = {
    simulationGeneration: { value: 4 },
    assignmentRecoveryTracker: { clear() { clears += 1 } },
    abortRoutePlanningRequests() { aborts += 1 },
    AbortController: class {},
    routePlanningAbortController: null
  }
  vm.createContext(context)
  vm.runInContext(`${sliceBetween('const beginSimulationGeneration =', 'const isActiveSimulationGeneration =')}\nthis.begin = beginSimulationGeneration; this.invalidate = invalidateSimulationGeneration;`, context)
  assert.equal(context.begin(), 5)
  context.invalidate()
  assert.equal(context.simulationGeneration.value, 6)
  assert.equal(clears, 2)
  assert.equal(aborts, 2)
})
