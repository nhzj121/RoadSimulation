import { buildRandomEventPayload } from '../utils/breakdownPresentation.js'

export const createRandomEventApi = http => ({
  async trigger(eventType, vehicleId, durationOrOptions) {
    const values = typeof durationOrOptions === 'object' ? durationOrOptions : { durationMinutes: durationOrOptions }
    const response = await http.post('/api/simulation/random-events/trigger', buildRandomEventPayload(eventType, vehicleId, values))
    return response.data?.data || response.data
  },
  async getActive() {
    const response = await http.get('/api/simulation/random-events/active')
    return response.data?.data || []
  }
})
