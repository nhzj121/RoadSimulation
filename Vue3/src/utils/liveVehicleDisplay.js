const same = (a, b) => a != null && b != null && String(a) === String(b)

export function mergeLiveVehicleDisplay(cached = {}, liveVehicle, liveAssignment) {
  const cachedAssignmentId = cached.assignmentId
  const vehicleMatches = liveVehicle && same(liveVehicle.vehicleId, cached.vehicleId)
  const liveVehicleAssignment = liveVehicle?.assignmentId
  const vehicleAssignmentMatches = liveVehicleAssignment == null || cachedAssignmentId == null || same(liveVehicleAssignment, cachedAssignmentId)
  const assignmentMatches = liveAssignment && same(liveAssignment.vehicleId, cached.vehicleId) && (cachedAssignmentId == null || same(liveAssignment.assignmentId, cachedAssignmentId))
  const authoritativeVehicle = vehicleMatches && vehicleAssignmentMatches ? liveVehicle : null
  const authoritativeAssignment = assignmentMatches ? liveAssignment : null
  const assignment = authoritativeAssignment ? { ...cached, ...authoritativeAssignment } : { ...cached }
  const vehicleInfo = authoritativeVehicle ? { ...cached, ...authoritativeAssignment, ...authoritativeVehicle } : { ...cached }
  return { assignment, vehicleInfo, currentStatus: authoritativeVehicle?.status ?? cached.status ?? cached.vehicleStatus }
}
