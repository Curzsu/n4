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
      const errMsg = apiResponse.message || `业务逻辑错误(代码: ${apiResponse.code})`
      showErrorToast(errMsg)
      return Promise.reject(new Error(errMsg))
    }
    
    // Handle non-standard responses
    return response.data
  },
  (error) => {
    let msg = '网络连接异常，请检查后端服务'
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请稍后重试'
    } else if (error.response) {
      const status = error.response.status
      const data = error.response.data
      if (status === 404) {
        msg = '接口不存在(404)'
      } else if (status === 500) {
        msg = '服务器内部错误(500)'
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

