import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/auth'
import type { Result } from '@/types/api'

/** 携带业务返回结果的请求配置扩展。 */
export interface RequestConfig extends AxiosRequestConfig {
  /** 静默：不弹出统一错误提示（由调用方自行处理表单级错误时使用）。 */
  silent?: boolean
  /** 已经历过一次 401 续签重放。 */
  _retried?: boolean
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '',
  timeout: 15000,
})

/** 免鉴权接口（API 统一规范 第 3 节：login / refresh / register）。 */
const WHITE_LIST = ['/auth/login', '/auth/refresh', '/auth/register']

function isWhiteList(url?: string): boolean {
  if (!url) return false
  const path = url.startsWith('/api/') ? url.slice(4) : url
  return WHITE_LIST.some((p) => path === p || path.startsWith(`${p}?`))
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/* ---------------- 请求拦截器 ---------------- */
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // 兼容模块层直接书写 '/api/...' 全路径：baseURL 已含 /api，避免重复前缀
  if (config.url?.startsWith('/api/')) {
    config.url = config.url.slice(4)
  }

  if (!isWhiteList(config.url)) {
    const token = getAccessToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
  }

  // 写接口附加幂等标识，配合后端 40905 重复提交防护
  const method = (config.method ?? 'get').toLowerCase()
  if (method !== 'get' && method !== 'head') {
    config.headers['X-Request-Id'] = uuid()
  }
  return config
})

/* ---------------- 401 续签（单飞，避免并发刷新风暴） ---------------- */
let refreshing: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  if (!refreshing) {
    refreshing = (async () => {
      const refreshToken = getRefreshToken()
      if (!refreshToken) throw new Error('no refresh token')
      const res = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
        `${import.meta.env.VITE_API_BASE ?? ''}/auth/refresh`,
        { refreshToken },
      )
      const data = res.data.data
      if (res.data.code !== 200 || !data) throw new Error('refresh failed')
      setTokens(data.accessToken, data.refreshToken)
      return data.accessToken
    })().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

function toLogin(message: string): void {
  clearTokens()
  const { pathname, search } = window.location
  if (pathname !== '/login') {
    ElMessage.warning(message)
    window.location.href = `/login?redirect=${encodeURIComponent(`${pathname}${search}`)}`
  }
}

/* ---------------- 响应拦截器：解包信封 ---------------- */
service.interceptors.response.use(
  async (response) => {
    const body = response.data as Result | undefined
    if (!body || typeof body.code !== 'number') return body as never
    if (body.code === 200) return body.data as never

    const config = response.config as RequestConfig

    // 业务码 401（HTTP 200 场景）：续签一次并重放
    if (body.code === 401 && !config._retried && !isWhiteList(config.url)) {
      config._retried = true
      try {
        const token = await refreshAccessToken()
        config.headers = config.headers ?? {}
        config.headers.Authorization = `Bearer ${token}`
        return service.request(config)
      } catch {
        toLogin('登录已过期，请重新登录')
        return Promise.reject(response)
      }
    }

    if (body.code === 401) {
      toLogin('登录已过期，请重新登录')
    } else if (!config.silent) {
      if (body.code === 40302) {
        ElMessage.error('没有该仓库数据权限')
      } else if (body.code === 403 || body.code === 40301) {
        ElMessage.error(body.message || '没有操作权限')
      } else {
        ElMessage.error(body.message || '操作失败')
      }
    }
    return Promise.reject(response)
  },
  async (error: AxiosError<Result>) => {
    const config = error.config as RequestConfig | undefined
    const status = error.response?.status
    const body = error.response?.data

    // HTTP 401：尝试续签一次并重放原请求
    if (status === 401 && config && !config._retried && !isWhiteList(config.url)) {
      config._retried = true
      try {
        const token = await refreshAccessToken()
        config.headers = config.headers ?? {}
        config.headers.Authorization = `Bearer ${token}`
        return service.request(config)
      } catch {
        toLogin('登录已过期，请重新登录')
        return Promise.reject(error)
      }
    }

    if (config?.silent) return Promise.reject(error)

    if (status === 401) {
      toLogin('登录已过期，请重新登录')
    } else if (status === 403) {
      ElMessage.error(body?.message || (body?.code === 40302 ? '没有该仓库数据权限' : '没有操作权限'))
    } else if (body?.message) {
      ElMessage.error(body.message)
    } else {
      ElMessage.error('系统异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default service
