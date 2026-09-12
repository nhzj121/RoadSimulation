import request from '../utils/request'

async function unwrap(promise) {
  const { data } = await promise
  if (data?.success === false) throw new Error(data.message || '天气请求失败')
  return data?.data ?? data
}

export const weatherApi = {
  current: () => unwrap(request.get('/api/simulation/weather/current')),
  list: () => unwrap(request.get('/api/simulation/weather/scenarios')),
  create: (body) => unwrap(request.post('/api/simulation/weather/scenarios', body)),
  import: (body) => unwrap(request.post('/api/simulation/weather/scenarios/import', body)),
  export: (id) => unwrap(request.get(`/api/simulation/weather/scenarios/${encodeURIComponent(id)}`)),
  exportRun: (id) => unwrap(request.get(`/api/simulation/weather/runs/${encodeURIComponent(id)}`))
}
