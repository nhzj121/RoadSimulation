const PREFIX = 'road-simu:weather-route:v1:'
const VERSION = 1
const MAX_POINTS = 5000
const MAX_BYTES = 300000
const coord = value => Array.isArray(value) && value.length >= 2 && Number.isFinite(Number(value[0])) && Number.isFinite(Number(value[1])) && Number(value[0]) >= -180 && Number(value[0]) <= 180 && Number(value[1]) >= -90 && Number(value[1]) <= 90
const equalCoord = (a, b) => coord(a) && coord(b) && Number(a[0]) === Number(b[0]) && Number(a[1]) === Number(b[1])
const validRoute = route => route && Array.isArray(route.path) && route.path.length >= 2 && route.path.length <= MAX_POINTS && route.path.every(coord) && (route.distance == null || Number.isFinite(Number(route.distance)))
const key = (runId, routeKey) => `${PREFIX}${encodeURIComponent(String(runId))}:${encodeURIComponent(String(routeKey))}`

export const assignmentPollingMode = weather => weather?.runId ? 'active' : 'new'

export function createWeatherRouteCache(storage) {
  return {
    read(runId, routeKey, start, end) {
      if (!runId) return null
      try {
        const raw = storage?.getItem(key(runId, routeKey))
        if (!raw || raw.length > MAX_BYTES) return null
        const record = JSON.parse(raw)
        return record.version === VERSION && record.runId === String(runId) && record.routeKey === String(routeKey) && equalCoord(record.start, start) && equalCoord(record.end, end) && validRoute(record.route) ? record.route : null
      } catch (_) { return null }
    },
    write(runId, routeKey, start, end, route) {
      if (!runId || !coord(start) || !coord(end) || !validRoute(route)) return false
      try {
        const raw = JSON.stringify({ version: VERSION, runId: String(runId), routeKey: String(routeKey), start: start.map(Number), end: end.map(Number), route })
        if (raw.length > MAX_BYTES) return false
        storage?.setItem(key(runId, routeKey), raw)
        return true
      } catch (_) { return false }
    },
    clearRun(runId) {
      if (!runId) return
      try {
        const prefix = `${PREFIX}${encodeURIComponent(String(runId))}:`
        const owned = []
        for (let index = 0; index < storage.length; index += 1) {
          const itemKey = storage.key(index)
          if (itemKey?.startsWith(prefix)) owned.push(itemKey)
        }
        owned.forEach(itemKey => storage.removeItem(itemKey))
      } catch (_) {}
    }
  }
}
