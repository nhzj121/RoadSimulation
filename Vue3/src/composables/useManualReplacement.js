import { ref } from 'vue'
import { measurePath, pointOnPath } from '../utils/manualReplacementPlayback.js'

const URL = '/api/simulation/manual-replacements'
const PLAYING = new Set(['WAITING', 'EN_ROUTE', 'HANDED_OFF', 'VISUAL_ONLY'])
const AFTER_ARRIVAL = new Set(['HANDED_OFF', 'VISUAL_ONLY'])
const cacheKey = id => `road-simulation:manual-replacement:${id}`
const message = error => error?.response?.data?.message || error?.message || '操作失败'

export function useManualReplacement({ http, enabled, running, speed, render, ownRoutes }) {
  const runId = ref(null)
  const available = ref(false)
  const rows = ref([])
  const errors = ref({})
  const entries = new Map()
  const snapshots = new Map()
  const pending = new Map()
  let finished = new Set()
  let revision = 0
  let refreshRequest = null
  let polling = null
  let frame = 0
  let lastTime = null
  let disposed = false

  function load(requestId) {
    if (snapshots.has(requestId)) return snapshots.get(requestId)
    try { return JSON.parse(sessionStorage.getItem(cacheKey(requestId))) } catch { return null }
  }

  function save(requestId, value) {
    if (value) snapshots.set(requestId, value)
    else snapshots.delete(requestId)
    try {
      if (value) sessionStorage.setItem(cacheKey(requestId), JSON.stringify(value))
      else sessionStorage.removeItem(cacheKey(requestId))
    } catch { /* Playback can still continue in the current tab. */ }
  }

  function recordFinished() {
    let changed = false
    for (const row of rows.value) {
      if (row.phase !== 'FINISHED' || finished.has(String(row.assignmentId))) continue
      finished.add(String(row.assignmentId))
      changed = true
    }
    if (!changed || !runId.value) return
    try { sessionStorage.setItem(cacheKey(`finished:${runId.value}`), JSON.stringify([...finished])) } catch { /* Current tab retains ownership. */ }
  }

  function publishOwnership() {
    ownRoutes(
      new Set([...pending.values(), ...[...entries.values()].map(entry => String(entry.row.assignmentId))]),
      finished
    )
  }

  function finishedForRun(run) {
    try { return new Set(JSON.parse(sessionStorage.getItem(cacheKey(`finished:${run}`))) || []) }
    catch { return new Set() }
  }

  function errorFor(id, value) {
    if ((errors.value[id] || null) === value) return
    const updated = { ...errors.value }
    if (value) updated[id] = value
    else delete updated[id]
    errors.value = updated
  }

  function disposeEntry(id, forget = false) {
    const entry = entries.get(id)
    if (!entry) return
    entry.overlay.clear()
    entries.delete(id)
    if (forget) save(entry.row.requestId, null)
  }

  function reconcile() {
    const wanted = new Set()
    for (const row of rows.value) {
      if (!PLAYING.has(row.phase)) continue
      const entry = entries.get(row.eventId)
      if (entry && entry.row.attemptId === row.attemptId) {
        entry.row = row
        wanted.add(row.eventId)
        continue
      }
      disposeEntry(row.eventId)
      const cache = load(row.requestId)
      if (!cache?.snapshot || cache.runId !== row.runId) {
        errorFor(row.eventId, '本页缺少故障画面，可终止接替并释放预留')
        continue
      }
      const saved = cache.attemptId === row.attemptId ? cache.playback : null
      const overlay = render(row, cache.snapshot)
      if (!overlay) continue
      errorFor(row.eventId, null)
      entries.set(row.eventId, {
        row, cache, overlay, approach: null, preparing: false, busy: false, failed: false,
        playback: saved || { approach: 0, transfer: 0, leg: 0, meters: 0, service: 0, loaded: cache.snapshot.loaded },
        routes: cache.snapshot.remaining.map(leg => measurePath(leg.path)),
        savedAt: 0
      })
      wanted.add(row.eventId)
    }
    for (const id of entries.keys()) {
      if (!wanted.has(id)) disposeEntry(id, true)
    }
    recordFinished()
    publishOwnership()
    if (entries.size && !frame) frame = requestAnimationFrame(tick)
  }

  function saveProgress(entry) {
    save(entry.row.requestId, { ...entry.cache, attemptId: entry.row.attemptId, playback: entry.playback })
  }

  function setRow(row) {
    if (disposed || row.runId !== runId.value) return
    rows.value = [...rows.value.filter(other => other.eventId !== row.eventId), row].sort((a, b) => a.eventId - b.eventId)
    reconcile()
  }

  async function refresh() {
    if (disposed || !enabled()) return
    if (refreshRequest) return refreshRequest
    const observedRevision = revision
    refreshRequest = http.get(URL, { timeout: 4000 }).then(response => {
      if (disposed || !enabled() || observedRevision !== revision) return
      const monitor = response.data.data
      if (runId.value !== monitor.runId) {
        for (const id of entries.keys()) disposeEntry(id, true)
        pending.clear()
        finished = finishedForRun(monitor.runId)
        errors.value = {}
      }
      runId.value = monitor.runId
      available.value = monitor.available
      rows.value = monitor.replacements
      reconcile()
    }).catch(() => {
      // A failed poll retains the last confirmed course and reservation.
    }).finally(() => { refreshRequest = null })
    return refreshRequest
  }

  function startMonitoring() {
    if (disposed || polling !== null) return
    polling = setInterval(() => { void refresh() }, 1500)
  }

  async function start(vehicleId, assignmentId, snapshot) {
    const requestId = crypto.randomUUID()
    const run = runId.value
    save(requestId, { runId: run, snapshot, attemptId: null })
    pending.set(requestId, String(assignmentId))
    publishOwnership()
    revision++
    try {
      const response = await http.post(URL, {
        runId: run, requestId, vehicleId, assignmentId, longitude: snapshot.position[0],
        latitude: snapshot.position[1], nextStopIndex: snapshot.nextStopIndex
      })
      revision++
      setRow(response.data.data)
    } catch (error) {
      if (error?.response?.status >= 400 && error?.response?.status < 500) save(requestId, null)
      throw error
    } finally {
      pending.delete(requestId)
      if (!disposed && runId.value === run) publishOwnership()
    }
  }

  async function operate(row, operation) {
    revision++
    const response = await http.post(`${URL}/${row.eventId}/${operation}`, {
      runId: row.runId, attemptId: row.attemptId
    })
    revision++
    setRow(response.data.data)
    return response.data.data
  }

  function advance(entry, operation) {
    if (entry.busy) return
    entry.busy = true
    operate(entry.row, operation).catch(error => {
      entry.failed = true
      errorFor(entry.row.eventId, message(error))
    }).finally(() => { entry.busy = false })
  }

  async function prepareApproach(entry) {
    if (entry.preparing || entry.approach || entry.failed) return
    entry.preparing = true
    try {
      const row = entry.row
      const response = await http.get(`${URL}/${row.eventId}/approach-route`, {
        params: { runId: row.runId, attemptId: row.attemptId }
      })
      if (entries.get(row.eventId) !== entry) return
      const { attemptId, path } = response.data.data || {}
      if (attemptId !== row.attemptId || !path?.length) throw new Error('红色赶赴路线规划失败')
      entry.approach = measurePath(path)
      entry.overlay.drawApproach(path, pointOnPath(entry.approach, entry.playback.approach), row)
    } catch (error) {
      if (entries.get(entry.row.eventId) !== entry) return
      entry.failed = true
      errorFor(entry.row.eventId, message(error))
    } finally {
      entry.preparing = false
    }
  }

  function tick(now) {
    frame = 0
    const seconds = running() && lastTime != null ? Math.min((now - lastTime) / 1000, 0.25) : 0
    lastTime = running() ? now : null
    for (const entry of entries.values()) {
      if (!seconds || entry.failed || entry.busy) continue
      const { row, playback, overlay } = entry
      const meters = seconds * 20 * speed()
      if (row.phase === 'EN_ROUTE') {
        if (!entry.approach) {
          void prepareApproach(entry)
          continue
        }
        playback.approach = Math.min(entry.approach.length, playback.approach + meters)
        overlay.move(pointOnPath(entry.approach, playback.approach))
        if (playback.approach === entry.approach.length) {
          saveProgress(entry)
          advance(entry, 'arrived')
        }
      } else if (AFTER_ARRIVAL.has(row.phase)) {
        const transferSeconds = entry.cache.snapshot.loaded ? 3 : 0.75
        if (playback.transfer < transferSeconds) {
          overlay.transfer(entry.cache.snapshot.position, row, entry.cache.snapshot.loaded)
          playback.transfer += seconds
        } else if (playback.leg < entry.routes.length) {
          const route = entry.routes[playback.leg]
          const action = entry.cache.snapshot.remaining[playback.leg].action
          playback.meters = Math.min(route.length, playback.meters + meters)
          overlay.continueTrip(pointOnPath(route, playback.meters), row,
            playback.meters < route.length ? (playback.loaded ? 'TRANSPORT_DRIVING' : 'ORDER_DRIVING')
              : (action === 'LOAD' ? 'LOADING' : 'UNLOADING'))
          if (playback.meters === route.length) {
            playback.service += seconds * speed()
            if (playback.service >= 2) {
              if (action === 'LOAD') playback.loaded = true
              if (action === 'UNLOAD') {
                playback.loaded = entry.cache.snapshot.remaining[playback.leg + 1]?.loadBefore > 0
              }
              playback.leg++
              playback.meters = 0
              playback.service = 0
            }
          }
        } else {
          saveProgress(entry)
          advance(entry, 'finished')
        }
      }
      if (now - entry.savedAt >= 1000) {
        saveProgress(entry)
        entry.savedAt = now
      }
    }
    if (entries.size) frame = requestAnimationFrame(tick)
    else lastTime = null
  }

  function retry(row) {
    const entry = entries.get(row.eventId)
    if (!entry) return
    entry.failed = false
    errorFor(row.eventId, null)
  }

  const canRetry = row => Boolean(entries.get(row.eventId))

  function clear({ forget = false, unmount = false } = {}) {
    revision++
    disposed = unmount
    if (unmount && polling !== null) {
      clearInterval(polling)
      polling = null
    }
    if (forget) rows.value.forEach(row => save(row.requestId, null))
    if (forget && runId.value) {
      try { sessionStorage.removeItem(cacheKey(`finished:${runId.value}`)) } catch { /* Storage may be unavailable. */ }
    }
    for (const id of entries.keys()) disposeEntry(id, forget)
    if (forget) pending.forEach((_assignment, id) => save(id, null))
    pending.clear()
    snapshots.clear()
    finished = new Set()
    publishOwnership()
    if (frame) cancelAnimationFrame(frame)
    frame = 0
    lastTime = null
    runId.value = null
    rows.value = []
    errors.value = {}
  }

  return { runId, available, rows, errors, refresh, startMonitoring, start, operate, retry, canRetry, clear }
}
