import type { SandboxJobView, SandboxStartRequest } from '../types/sandbox'

export const pendingStartKey = 'roadsimulation.sandbox.pending-start/v1'
export const terminalStatuses = new Set(['SUCCEEDED', 'CANCELLED', 'FAILED', 'ABORTED'])
export function taskLabel(status: string) {
  return ({ACCEPTED: '已接受，等待工作进程', PREPARING: '准备沙箱数据及车辆初态', RUNNING: '仿真运行中',
    CANCEL_REQUESTED: '取消已请求，等待完整轮次边界', FINALIZING: '核验与收尾中', SUCCEEDED: '正常完成',
    CANCELLED: '已取消（保留完整前缀）', FAILED: '失败（保留已记录前缀）', INTERRUPTED: '异常中断，需人工检查', ABORTED: '已显式异常收尾'} as Record<string, string>)[status] || `未知状态：${status}`
}
export function canCancelTask(view: SandboxJobView | null, stale: boolean) {
  return !!view && !stale && !view.attentionRequired && view.actions.canCancel && ['ACCEPTED', 'RUNNING'].includes(view.status)
}
export function parsePendingStart(raw: string | null): SandboxStartRequest | null {
  if (!raw) return null
  const value = JSON.parse(raw)
  if (!value || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value.clientRequestId)
    || typeof value.runSpecKey !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(value.runSpecKey)
    || !Number.isSafeInteger(value.revision) || value.revision < 1) throw new Error('本地待核对请求记录无效；不能据此重新启动')
  return {clientRequestId: value.clientRequestId, runSpecKey: value.runSpecKey, revision: value.revision}
}
