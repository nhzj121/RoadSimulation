const { test } = require('node:test')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const path = require('node:path')

const load = name => import(pathToFileURL(path.join(__dirname, `../src/utils/${name}.js`)).href)

test('replacement vehicle statuses are readable, visually distinct and stopped', async () => {
  const { vehicleStatusPresentation, isStoppedVehicleStatus } = await load('vehicleStatusPresentation')
  assert.deepEqual(vehicleStatusPresentation('SCRAPPED'), { text: '已报废', color: '#7f1d1d', stopped: true })
  assert.deepEqual(vehicleStatusPresentation('RESERVED_REPLACEMENT'), { text: '替换车辆准备中', color: '#7c3aed', stopped: true })
  assert.equal(isStoppedVehicleStatus('SCRAPPED'), true)
  assert.equal(isStoppedVehicleStatus('RESERVED_REPLACEMENT'), true)
  assert.equal(isStoppedVehicleStatus('TRANSPORT_DRIVING'), false)
})

test('render identity includes assignment and vehicle ownership', async () => {
  const { assignmentRenderIdentity } = await load('assignmentRenderOwnership')
  assert.equal(assignmentRenderIdentity({ assignmentId: 88, vehicleId: 12 }), '88:12')
  assert.equal(assignmentRenderIdentity({ assignmentId: 88, vehicleId: 18 }), '88:18')
  assert.equal(assignmentRenderIdentity({ assignmentId: 88 }), null)
})

test('owner-change reconciliation removes the old render once and creates the current backend owner once', async () => {
  const { reconcileAssignmentRenderOwner } = await load('assignmentRenderOwnership')
  const rendered = new Map([[88, { assignmentId: 88, vehicleId: 12, marker: 'old' }]])
  const removals = []
  const creations = []
  const next = { assignmentId: 88, vehicleId: 18, drivingProgress: 0.42, currentLongitude: 104.1, currentLatitude: 30.6 }
  const reconcile = assignment => reconcileAssignmentRenderOwner({
    assignment,
    currentAssignment: rendered.get(assignment.assignmentId),
    removeCurrent(current) {
      removals.push(`${current.assignmentId}:${current.vehicleId}`)
      rendered.delete(current.assignmentId)
    },
    async createCurrent(current) {
      creations.push(`${current.assignmentId}:${current.vehicleId}@${current.drivingProgress}`)
      rendered.set(current.assignmentId, current)
      return current
    }
  })

  const changed = await reconcile(next)
  assert.equal(changed.ownerChanged, true)
  assert.equal(changed.created, next)
  assert.deepEqual(removals, ['88:12'])
  assert.deepEqual(creations, ['88:18@0.42'])
  assert.equal(rendered.get(88).vehicleId, 18)

  const repeated = await reconcile({ ...next })
  assert.equal(repeated.ownerChanged, false)
  assert.equal(repeated.created, null)
  assert.deepEqual(removals, ['88:12'])
  assert.deepEqual(creations, ['88:18@0.42'])
})

test('old vehicle render registration is removed without touching the replacement vehicle', async () => {
  const { removeVehicleRenderRegistration } = await load('assignmentRenderOwnership')
  const oldMarker = { id: 'old' }
  const newMarker = { id: 'new' }
  const manager = {
    vehicleMarkers: new Map([[12, oldMarker], [18, newMarker]]),
    assignmentData: new Map([[12, { assignmentId: 88 }], [18, { assignmentId: 88 }]])
  }
  const removed = []
  assert.equal(removeVehicleRenderRegistration(manager, 12, marker => removed.push(marker)), true)
  assert.deepEqual(removed, [oldMarker])
  assert.equal(manager.vehicleMarkers.has(12), false)
  assert.equal(manager.assignmentData.has(12), false)
  assert.equal(manager.vehicleMarkers.get(18), newMarker)
  assert.equal(manager.assignmentData.has(18), true)
  assert.equal(removeVehicleRenderRegistration(manager, 12, marker => removed.push(marker)), false)
  assert.deepEqual(removed, [oldMarker])
})
