const identityPart = value => value === null || value === undefined ? null : String(value)

export function assignmentRenderIdentity(assignment) {
  const assignmentId = identityPart(assignment?.assignmentId)
  const vehicleId = identityPart(assignment?.vehicleId)
  return assignmentId && vehicleId ? `${assignmentId}:${vehicleId}` : null
}

export async function reconcileAssignmentRenderOwner({
  assignment,
  currentAssignment,
  removeCurrent,
  createCurrent
}) {
  const identity = assignmentRenderIdentity(assignment)
  const previousIdentity = assignmentRenderIdentity(currentAssignment)
  if (!identity) return { identity: null, previousIdentity, ownerChanged: false, created: null }
  if (identity === previousIdentity) return { identity, previousIdentity, ownerChanged: false, created: null }

  if (previousIdentity && typeof removeCurrent === 'function') {
    await removeCurrent(currentAssignment)
  }
  const created = typeof createCurrent === 'function' ? await createCurrent(assignment) : null
  return { identity, previousIdentity, ownerChanged: Boolean(previousIdentity), created: created || null }
}

export function removeVehicleRenderRegistration(manager, vehicleId, removeMarker) {
  if (!manager || vehicleId === null || vehicleId === undefined) return false
  const marker = manager.vehicleMarkers?.get(vehicleId)
  const registered = Boolean(marker) || manager.assignmentData?.has(vehicleId)
  if (marker && typeof removeMarker === 'function') removeMarker(marker)
  manager.vehicleMarkers?.delete(vehicleId)
  manager.assignmentData?.delete(vehicleId)
  return registered
}
