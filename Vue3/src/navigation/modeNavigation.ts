import axios from 'axios'
import { computed, reactive, ref } from 'vue'
import { pendingStartKey } from '../sandbox/execution'

export interface ModeNavigationState {
  artifactVersion: 'mode-navigation-state/v1'
  confirmed: boolean
  canSwitch: boolean
  reason: string | null
  ordinaryRunning: boolean
  ordinaryBusy: boolean
  comparisonActive: boolean
  sandboxManagementEnabled: boolean
  sandboxBusy: boolean | null
  sandboxWorkspaceState: string | null
}

const client = axios.create({ baseURL: 'http://localhost:8080', timeout: 5000 })
export const navigationApi = {
  readState: async (): Promise<unknown> => (await client.get('/api/simulation/navigation-state')).data,
}
export const navigationState = reactive({ confirmed: false, checking: false, reason: 'NAVIGATION_STATE_UNCONFIRMED', observedAt: 0 })
const localOrdinary = reactive({ running: false, comparison: false, busy: false })
const operations = new Map<number, string>()
const operationRevision = ref(0)
let operationId = 0, queryId = 0

export function isSandboxPath(path: string) { return path === '/sandbox' || path.startsWith('/sandbox/') }
export function crossesSimulationModes(from: string, to: string) { return isSandboxPath(from) !== isSandboxPath(to) }
export function setLocalOrdinaryNavigation(value: {running: boolean; comparison: boolean; busy: boolean}) { Object.assign(localOrdinary, value) }
export function clearLocalOrdinaryNavigation() { setLocalOrdinaryNavigation({running:false, comparison:false, busy:false}) }

/** Immediate UI-operation fence only, not a backend run lock. Release exactly once. */
export function beginNavigationOperation(reason = 'ORDINARY_TRANSITION_BUSY') {
  const id = ++operationId; operations.set(id, reason); operationRevision.value++
  return () => { if (operations.delete(id)) operationRevision.value++ }
}
export function localNavigationReason(): string | null {
  operationRevision.value
  if (localOrdinary.comparison) return 'COMPARISON_ACTIVE'
  if (localOrdinary.running) return 'ORDINARY_RUNNING'
  if (localOrdinary.busy) return 'ORDINARY_TRANSITION_BUSY'
  if (operations.size) return operations.values().next().value || 'ORDINARY_TRANSITION_BUSY'
  try {
    if (typeof localStorage !== 'undefined' && localStorage.getItem(pendingStartKey)) return 'SANDBOX_START_UNCONFIRMED'
  } catch { return 'NAVIGATION_STATE_UNCONFIRMED' }
  return null
}

export function validateNavigationState(value: unknown): ModeNavigationState {
  const state = value as ModeNavigationState
  if (!state || state.artifactVersion !== 'mode-navigation-state/v1' || state.confirmed !== true
      || ['canSwitch','ordinaryRunning','ordinaryBusy','comparisonActive','sandboxManagementEnabled'].some(key => typeof (state as unknown as Record<string,unknown>)[key] !== 'boolean')
      || typeof state.sandboxBusy !== 'boolean'
      || (state.sandboxManagementEnabled && typeof state.sandboxWorkspaceState !== 'string')) throw new Error('NAVIGATION_STATE_UNCONFIRMED')
  const reason = state.comparisonActive ? 'COMPARISON_ACTIVE' : state.ordinaryBusy ? 'ORDINARY_TRANSITION_BUSY'
    : state.ordinaryRunning ? 'ORDINARY_RUNNING' : state.sandboxBusy ? 'SANDBOX_BUSY' : null
  if (state.reason !== reason || state.canSwitch !== (reason === null)
      || (!state.sandboxManagementEnabled && state.sandboxBusy)) throw new Error('NAVIGATION_STATE_UNCONFIRMED')
  return state
}

/** Always query anew for a cross-mode route guard; polling results alone cannot authorize leaving. */
export async function refreshModeNavigation(): Promise<boolean> {
  const current = ++queryId; navigationState.checking = true
  try {
    const state = validateNavigationState(await navigationApi.readState())
    if (current !== queryId) return false
    navigationState.confirmed = true; navigationState.reason = state.reason || ''; navigationState.observedAt = Date.now()
    return state.canSwitch && !localNavigationReason()
  } catch {
    if (current === queryId) { navigationState.confirmed = false; navigationState.reason = 'NAVIGATION_STATE_UNCONFIRMED' }
    return false
  } finally { if (current === queryId) navigationState.checking = false }
}

export const navigationReason = computed(() => localNavigationReason()
  || (navigationState.checking ? 'NAVIGATION_CHECKING' : !navigationState.confirmed ? 'NAVIGATION_STATE_UNCONFIRMED' : navigationState.reason))
export function navigationMessage(reason: string) {
  return ({ORDINARY_RUNNING:'普通仿真运行中，请先暂停并等待当前轮次结束。',
    ORDINARY_TRANSITION_BUSY:'普通仿真正在执行操作或完成当前轮次，请稍后切换。',
    COMPARISON_ACTIVE:'主界面调度对比实验尚未结束；暂停也不能切换模式。',
    SANDBOX_BUSY:'沙箱任务正在准备、运行、收尾，或尚待显式异常收尾，请先查看任务状态。',
    SANDBOX_START_UNCONFIRMED:'沙箱启动请求尚未核对，请先核对请求或任务。',
    NAVIGATION_CHECKING:'正在确认运行状态，请稍候。',
    NAVIGATION_STATE_UNCONFIRMED:'运行状态未确认，暂不能切换模式；请检查后端连接后重试。'} as Record<string,string>)[reason] || '运行状态未确认，暂不能切换模式。'
}

export function createModeNavigationGuard(notify: (message:string) => void) {
  return async (to: {path:string}, from: {path:string; matched:unknown[]}) => {
    if (!from.matched.length || !crossesSimulationModes(from.path,to.path)) return true
    const local = localNavigationReason()
    if (local) { notify(navigationMessage(local)); return false }
    const allowed = await refreshModeNavigation()
    if (!allowed) { notify(navigationMessage(navigationReason.value || 'NAVIGATION_STATE_UNCONFIRMED')); return false }
    // Recheck a click/start race after the read completes.
    const late = localNavigationReason()
    if (late) { notify(navigationMessage(late)); return false }
    return true
  }
}
