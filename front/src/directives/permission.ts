import type { Directive } from 'vue'
import { useUserStore } from '@/store/user'

/**
 * 按钮权限指令：v-permission="'sys:inbound:audit'"
 * 支持字符串或字符串数组（数组为“任一命中即显示”）。
 * 约束（前端开发规范 §9.2）：前端隐藏仅为体验优化，后端必须二次校验。
 */
function hasPerm(code: string | string[]): boolean {
  const userStore = useUserStore()
  const codes = Array.isArray(code) ? code : [code]
  return codes.some((c) => userStore.hasPerm(c))
}

export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    if (!binding.value) return
    if (!hasPerm(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default permission
