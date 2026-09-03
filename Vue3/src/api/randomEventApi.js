import request from '../utils/request'

export const randomEventApi = {
  async trigger(eventType, vehicleId, durationMinutes) {
    const response = await request.post('/api/simulation/random-events/trigger', {
      eventType,
      vehicleId,
      durationMinutes
    })
    return response.data?.data || response.data
  },

  async getActive() {
    const response = await request.get('/api/simulation/random-events/active')
    return response.data?.data || []
  }
}
