import request from '@/utils/request'
import type { RequestConfig } from '@/utils/request'
import type { AuthInfoVO, LoginDTO, LoginVO, RegisterDTO } from '@/types/entity'

/** 登录（API-AUTH-001，免认证）。silent=true 时由调用方接管错误提示（登录页内联反馈）。 */
export function login(data: LoginDTO, silent = false): Promise<LoginVO> {
  const config: RequestConfig = { silent }
  return request.post('/auth/login', data, config)
}

/** 注册（免认证）。silent=true 时由调用方接管错误提示（注册页内联反馈）。 */
export function register(data: RegisterDTO, silent = false): Promise<void> {
  const config: RequestConfig = { silent }
  return request.post('/auth/register', data, config)
}

/** 退出登录（API-AUTH-003）。 */
export function logout(data?: { refreshToken?: string }): Promise<void> {
  return request.post('/auth/logout', data ?? {})
}

/** 获取当前用户信息、菜单与权限（API-AUTH-004）。 */
export function getAuthInfo(): Promise<AuthInfoVO> {
  return request.get('/auth/info')
}
