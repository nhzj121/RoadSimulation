const radians = value => value * Math.PI / 180

function distance(a, b) {
  const lat = Math.sin(radians(b[1] - a[1]) / 2)
  const lon = Math.sin(radians(b[0] - a[0]) / 2)
  const arc = lat ** 2 + Math.cos(radians(a[1])) * Math.cos(radians(b[1])) * lon ** 2
  return 12742000 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, arc))))
}

export function measurePath(path) {
  const offsets = [0]
  for (let i = 1; i < path.length; i++) offsets.push(offsets[i - 1] + distance(path[i - 1], path[i]))
  return { path, offsets, length: offsets[offsets.length - 1] }
}

export function pointOnPath(route, meters) {
  const { path, offsets, length } = route
  const next = offsets.findIndex(offset => offset > meters)
  if (next < 1 || meters >= length) return path[path.length - 1]
  const fraction = (meters - offsets[next - 1]) / (offsets[next] - offsets[next - 1])
  return path[next - 1].map((value, i) => value + fraction * (path[next][i] - value))
}

export function captureRemainingRoute(animation) {
  const position = animation.currentPosition?.slice()
  const currentPath = animation._getCurrentPath()
  if (!position || !currentPath?.length || animation.isCompleted) return null
  const route = measurePath(currentPath)
  const meters = route.length * Math.min(1, Math.max(0, animation.currentProgress || 0))
  const tail = [position, ...currentPath.filter((_, i) => i > 0 && route.offsets[i] > meters)]
  const vrp = Array.isArray(animation.stages)
  const nextStopIndex = vrp ? animation.currentStageIndex : animation.currentStage - 1
  const remaining = vrp
    ? animation.stages.slice(nextStopIndex).map(stage => ({ path: stage.path, action: stage.nodeInfo.actionType }))
    : nextStopIndex === 0
      ? [{ path: animation.stage1Path, action: 'LOAD' }, { path: animation.stage2Path, action: 'UNLOAD' }]
      : [{ path: animation.stage2Path, action: 'UNLOAD' }]
  if (!remaining.length) return null
  remaining[0].path = tail
  if (vrp) {
    let load = animation.runtimeLoad
    remaining.forEach((leg, i) => {
      leg.loadBefore = load
      load = Math.max(0, load + (animation.stages[nextStopIndex + i].nodeInfo.weightDelta || 0))
    })
  }
  const loaded = vrp ? animation.runtimeLoad > 0 : nextStopIndex > 0
  return { position, nextStopIndex, loaded, vrp, remaining }
}
