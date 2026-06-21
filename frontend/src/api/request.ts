import axios from 'axios'
import type { ApiResponse } from '@/api/types'

const request = axios.create({
  baseURL: '',
  timeout: 10000,
})

request.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse<any>
    if (data.code === 200) {
      return data.data
    }
    return Promise.reject(new Error(data.message || 'API error'))
  },
  (error) => {
    return Promise.reject(error)
  }
)

export default request
