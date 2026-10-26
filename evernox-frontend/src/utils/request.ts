import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { AxiosHeaders } from 'axios'
import { ElMessage } from 'element-plus'
import type { AuthResponse } from '@/types/user'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const request: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('accessToken')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 会话失效时打开全局登录弹窗（不跳转路由）
 *
 * 用动态 import 引入 store，避免 request → stores/user → api/auth → request 的循环依赖。
 */
const openAuthModal = async () => {
  try {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().clearToken()
  } catch {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }
  try {
    const { useAuthDialogStore } = await import('@/stores/authDialog')
    useAuthDialogStore().open('login')
  } catch {
    // store 未就绪时忽略
  }
}

/** 请求是否携带了 token：匿名请求的 401 应静默处理，不打扰浏览 */
const hasAuthHeader = (config?: { headers?: AxiosHeaders | Record<string, unknown> }) => {
  if (!config?.headers) return false
  const headers = config.headers as Record<string, unknown>
  return Boolean(headers.Authorization || headers.authorization)
}

/** 403 不是登录态问题：别清 token 也别跳登录页，只把停留在管理员页的用户退回首页 */
const redirectFromForbidden = async () => {
  const { default: router } = await import('@/router')
  if (router.currentRoute.value.path.startsWith('/admin')) {
    await router.replace('/')
  }
}

/**
 * 刷新成功后把新 token 写回 localStorage 与 Pinia store
 */
const persistAuth = async (auth: AuthResponse) => {
  localStorage.setItem('accessToken', auth.accessToken)
  localStorage.setItem('refreshToken', auth.refreshToken)
  try {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().setToken(auth)
  } catch {
    // store 未就绪时忽略：localStorage 已是最新，后续请求会从 localStorage 读
  }
}

/**
 * 用 refresh token 换取新的 access/refresh token。
 * 走裸 axios 而非 request 实例，避免触发自身拦截器造成递归刷新。
 */
const doRefresh = async (): Promise<boolean> => {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) return false
  try {
    const res = await axios.post(`${API_BASE_URL}/auth/refresh`, null, {
      headers: { 'Refresh-Token': refreshToken },
    })
    const body = res.data
    if (body?.code === 200 && body?.data?.accessToken) {
      await persistAuth({
        accessToken: body.data.accessToken,
        refreshToken: body.data.refreshToken,
        tokenType: body.data.tokenType ?? 'Bearer',
        expiresIn: body.data.expiresIn ?? 0,
      })
      return true
    }
    return false
  } catch {
    return false
  }
}

/** 单飞刷新：并发多个 401 只触发一次 refresh */
let refreshPromise: Promise<boolean> | null = null
const refreshAccessToken = (): Promise<boolean> => {
  if (refreshPromise) return refreshPromise
  refreshPromise = doRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

request.interceptors.response.use(
  (response: AxiosResponse) => {
    // 二进制响应（图片字节流）直接返回，不做 JSON 解析
    if (response.config.responseType === 'arraybuffer' || response.config.responseType === 'blob') {
      return response
    }

    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401) {
        if (hasAuthHeader(response.config)) {
          // 会话过期：打开登录弹窗，不打断当前页面
          void openAuthModal()
        } else {
          // 匿名请求：不弹窗，但保留提示（登录失败等场景需要反馈）
          ElMessage.error(res.message || '请求失败')
        }
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    // 处理 HTTP 401 状态码：token 过期 → 尝试刷新后重试；刷新失败再打开登录弹窗
    if (error.response?.status === 401) {
      const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
      // 匿名请求（没有 Authorization 头）静默失败，避免匿名浏览时被弹窗打扰
      if (!hasAuthHeader(config)) {
        return Promise.reject(error)
      }
      if (config && !config._retried) {
        config._retried = true
        return refreshAccessToken()
          .then((ok) => {
            if (ok) {
              return request(config)
            }
            void openAuthModal()
            return Promise.reject(error)
          })
          .catch(() => {
            void openAuthModal()
            return Promise.reject(error)
          })
      }
      void openAuthModal()
      return Promise.reject(error)
    }
    // 403：权限不足（如普通账号尝试访问管理员接口）
    if (error.response?.status === 403) {
      ElMessage.error(error.response?.data?.message || '无权限访问')
      void redirectFromForbidden()
      return Promise.reject(error)
    }
    // 提取后端返回的错误信息
    const message = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request

export const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> => {
  return request.get(url, config) as unknown as Promise<T>
}

export const post = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> => {
  return request.post(url, data, config) as unknown as Promise<T>
}

export const put = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> => {
  return request.put(url, data, config) as unknown as Promise<T>
}

export const del = <T>(url: string, config?: AxiosRequestConfig): Promise<T> => {
  return request.delete(url, config) as unknown as Promise<T>
}
