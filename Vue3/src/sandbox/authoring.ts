import type { SandboxRunDefinition, SandboxSelection } from '../types/sandbox'

export const cloneDefinition = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
export const draftIdentity = (value: unknown): string => JSON.stringify(value)
export function parseIds(text: string): number[] {
  if (!text.trim()) return []
  const parts = text.trim().split(/[\s,，]+/)
  if (parts.some(value => !/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) <= 0))
    throw new Error('ID必须是正整数，用逗号或空格分隔')
  return Array.from(new Set(parts.map(Number))).sort((a, b) => a - b)
}
export function toggleId(selection: SandboxSelection, field: 'includeIds' | 'excludeIds', id: number, selected: boolean): void {
  selection[field] = selected ? Array.from(new Set([...selection[field], id])).sort((a, b) => a - b) : selection[field].filter(value => value !== id)
}
export function validateSeed(seed: string): void {
  // Never convert a signed 64-bit seed to a JavaScript Number.
  if (typeof seed !== 'string') throw new Error('种子必须以整数文本提供，禁止使用JSON数值类型')
  const magnitude = seed.startsWith('-') ? seed.slice(1) : seed
  const bound = seed.startsWith('-') ? '9223372036854775808' : '9223372036854775807'
  if (!/^-?(0|[1-9]\d*)$/.test(seed) || magnitude.length > bound.length || magnitude.length === bound.length && magnitude > bound)
    throw new Error('种子须为有符号64位整数文本')
}
export function setWeatherMode(run: SandboxRunDefinition, mode: SandboxRunDefinition['weather']['sourceMode']): void {
  run.weather = mode === 'SEEDED_GENERATION'
    ? { sourceMode: mode, generatorVersion: 'weather-v2', intervalMinutes: 60, weights: [60, 25, 5, 10], speedFactors: { SUNNY: 1, RAIN: .8, SNOW: .6, FOG: .7 }, timeSlices: [] }
    : { sourceMode: mode, generatorVersion: null, intervalMinutes: null, weights: null, speedFactors: null,
      timeSlices: [{ startMinute: 0, endMinute: run.simulationClock.totalLoops * run.simulationClock.tickDurationSeconds / 60, weatherType: 'SUNNY', speedFactor: 1 }] }
}
export const errorHints: Record<string, string> = {
  DRAFT_CONFLICT: '草稿已被其它页面修改。请先导出本地修改，再重新加载服务端草稿；不会自动覆盖。',
  PRODUCTION_ACTIVE_CHAIN_REQUIRED: 'PRODUCTION运行至少需要一条已选中的ACTIVE加工链；库存或货物选择不能替代加工链。',
  LOCAL_ACCESS_REQUIRED: '仅允许本机访问；请使用localhost:5173并检查后端本机访问配置。',
  CONTROL_SCHEMA_NOT_READY: '沙箱控制结构尚未准备完成，请检查后端配置。',
  WEATHER_HORIZON_TOO_SHORT: '显式天气时间线必须覆盖全部仿真轮次。',
  INVALID_WEATHER_TIMELINE: '显式天气时间段必须从0开始、连续无重叠。',
  SANDBOX_UNAVAILABLE: '沙箱管理未启用、后端不可达或接口不可用；普通仿真不受影响。',
}
