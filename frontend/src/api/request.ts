import axios from 'axios'
import type { ApiResponse } from '@/api/types'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '',
  timeout: 10000,
})

// Identical error messages within this window collapse into one toast, so a
// Promise.all burst of failures (e.g. dashboard parallel loads) does not spam.
const TOAST_DEDUPE_MS = 1500
let lastErrorToast = { msg: '', at: 0 }

function showErrorToast(msg: string) {
  const now = Date.now()
  if (msg === lastErrorToast.msg && now - lastErrorToast.at < TOAST_DEDUPE_MS) {
    return
  }
  lastErrorToast = { msg, at: now }
  ElMessage.error(msg)
}

request.interceptors.response.use(
  (response) => {
    const data = response.data
    // Handle standard ApiResponse format
    if (data && typeof data === 'object' && 'code' in data) {
      const apiResponse = data as ApiResponse<any>
      if (apiResponse.code === 200) {
        return apiResponse.data
      }
      const errMsg = apiResponse.message || `Business logic error (Code: ${apiResponse.code})`
      showErrorToast(errMsg)
      return Promise.reject(new Error(errMsg))
    }
    
    // Handle non-standard responses
    return response.data
  },
  (error) => {
    let msg = 'Network connection error, please check backend service'
    if (error.code === 'ECONNABORTED') {
      msg = 'Request timed out, please try again later'
    } else if (error.response) {
      const status = error.response.status
      const data = error.response.data
      if (status === 404) {
        msg = 'Endpoint not found (404)'
      } else if (status === 500) {
        msg = 'Internal server error (500)'
      } else if (data && typeof data === 'object') {
        msg = data.message || data.error || msg
      }
    } else if (error.message) {
      msg = error.message
    }
    
    showErrorToast(msg)
    return Promise.reject(new Error(msg))
  }
)

export default request

