import { defineStore } from 'pinia'

/** 页签（tags-view）条目，唯一键为路由 name。 */
export interface TagView {
  title: string
  name: string
  path: string
  /** 首页固定不可关闭 */
  affix?: boolean
}

const SIDEBAR_KEY = 'smartwms_sidebar_collapsed'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: localStorage.getItem(SIDEBAR_KEY) === '1',
    /** 已打开页签 */
    tags: [] as TagView[],
    /** keep-alive 缓存的组件名集合（页签打开→加入，关闭/刷新→移除） */
    cachedViews: [] as string[],
  }),

  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem(SIDEBAR_KEY, this.sidebarCollapsed ? '1' : '0')
    },

    /** 登记页签与缓存（幂等；affix 首页排在最前）。 */
    ensureTag(tag: TagView) {
      if (!this.tags.some((t) => t.name === tag.name)) {
        this.tags.push(tag)
        this.tags.sort((a, b) => Number(Boolean(b.affix)) - Number(Boolean(a.affix)))
      }
      if (!this.cachedViews.includes(tag.name)) {
        this.cachedViews.push(tag.name)
      }
    },

    /** 关闭页签；返回被关闭的页签（用于决定跳转目标），固定页签返回 null。 */
    closeTag(name: string): TagView | null {
      const idx = this.tags.findIndex((t) => t.name === name)
      if (idx === -1 || this.tags[idx].affix) return null
      const [removed] = this.tags.splice(idx, 1)
      this.cachedViews = this.cachedViews.filter((n) => n !== name)
      return removed
    },

    closeOthers(name: string) {
      this.tags = this.tags.filter((t) => t.affix || t.name === name)
      this.cachedViews = this.cachedViews.filter((n) => this.tags.some((t) => t.name === n))
    },

    closeAll() {
      this.tags = this.tags.filter((t) => t.affix)
      this.cachedViews = this.cachedViews.filter((n) => this.tags.some((t) => t.name === n))
    },

    /** 刷新：先把组件移出 keep-alive，落地后由 ensureTag 重新加入，实现重新加载。 */
    uncacheView(name: string) {
      this.cachedViews = this.cachedViews.filter((n) => n !== name)
    },

    reset() {
      this.tags = []
      this.cachedViews = []
    },
  },
})
