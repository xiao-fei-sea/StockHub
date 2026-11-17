import { useUserStore } from '@/store/user'

/**
 * 按钮权限判断（前端开发规范 §9.1）。
 * const { hasPerm } = usePermission(); v-if="hasPerm('sys:user:add')"
 * 注意：前端隐藏仅为体验优化，后端必须二次校验。
 */
export function usePermission() {
  const userStore = useUserStore()

  /** code 支持单个或数组（数组：任一命中即为 true）。 */
  function hasPerm(code?: string | string[] | null): boolean {
    if (!code) return true
    if (Array.isArray(code)) return code.some((c) => userStore.hasPerm(c))
    return userStore.hasPerm(code)
  }

  return { hasPerm, userStore }
}

export default usePermission
