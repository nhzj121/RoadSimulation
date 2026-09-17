import request from '../utils/request'
import { createRandomEventApi } from './randomEventApiFactory.js'

export const randomEventApi = createRandomEventApi(request)
