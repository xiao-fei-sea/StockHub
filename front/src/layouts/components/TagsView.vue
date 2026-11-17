<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Close, RefreshRight } from '@element-plus/icons-vue'
import { useAppStore, type TagView } from '@/store/app'

defineOptions({ name: 'TagsView' })

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const tagsRef = ref<HTMLElement | null>(null)

/* ---------- 登记当前页签 ---------- */
function isBusinessRoute(): boolean {
  // 注意：vue-router 的 route.meta 是整条 matched 链的浅合并，父级 MainLayout 的
  // meta.hidden 会泄漏到子路由上，因此以最末级记录（页面自身）的 meta 为准。
  const leaf = route.matched[route.matched.length - 1]
  if (!leaf) return false
  return Boolean(route.name) && Boolean(leaf.meta.title) && !leaf.meta.hidden && route.name !== 'Redirect'
}

watch(
  () => route.fullPath,
  () => {
    if (!isBusinessRoute()) return
    appStore.ensureTag({
      title: String(route.meta.title),
      name: String(route.name),
      path: route.path,
      affix: route.meta.affix,
    })
    scrollToActive()
  },
  { immediate: true },
)

/* ---------- 滚动到激活页签 ---------- */
function scrollToActive() {
  nextTick(() => {
    const container = tagsRef.value
    const active = container?.querySelector('.tag-item.is-active') as HTMLElement | null
    if (!container || !active) return
    const left = active.offsetLeft - container.clientWidth / 2 + active.clientWidth / 2
    container.scrollTo({ left: Math.max(0, left), behavior: 'smooth' })
  })
}

/* ---------- 点击切换 ---------- */
function selectTag(tag: TagView) {
  if (route.path === tag.path) return
  router.push(tag.path)
}

/* ---------- 关闭 ---------- */
function closeTag(tag: TagView, event?: Event) {
  event?.stopPropagation()
  const removed = appStore.closeTag(tag.name)
  if (!removed) return
  if (route.name === removed.name) {
    const last = appStore.tags[appStore.tags.length - 1]
    router.push(last?.path ?? '/dashboard')
  }
}

/* ---------- 刷新当前 ---------- */
function refreshCurrent() {
  const name = String(route.name)
  if (!name || name === 'Redirect') return
  appStore.uncacheView(name)
  router.replace(`/redirect${route.fullPath}`)
}

/* ---------- 右键菜单 ---------- */
const menuVisible = ref(false)
const menuPos = ref({ x: 0, y: 0 })
const menuTarget = ref<TagView | null>(null)

function openMenu(tag: TagView, event: MouseEvent) {
  event.preventDefault()
  menuTarget.value = tag
  menuPos.value = { x: event.clientX, y: event.clientY }
  menuVisible.value = true
}

function closeMenu() {
  menuVisible.value = false
}

function onDocClick() {
  closeMenu()
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

const ctxAffix = computed(() => menuTarget.value?.affix ?? false)

/* ---------- 固定页签（结构设计 §3.3：首页页签常驻且不可关闭，不依赖是否访问过） ---------- */
function registerAffixTags() {
  router
    .getRoutes()
    .filter((r) => r.meta.affix && r.meta.title && r.name)
    .forEach((r) =>
      appStore.ensureTag({
        title: String(r.meta.title),
        name: String(r.name),
        path: r.path,
        affix: true,
      }),
    )
}
registerAffixTags()

function ctx(action: 'refresh' | 'close' | 'closeOthers' | 'closeAll') {
  const target = menuTarget.value
  closeMenu()
  if (!target) return
  if (action === 'refresh') {
    if (route.name === target.name) refreshCurrent()
    else router.push(target.path).then(refreshCurrent)
    return
  }
  if (action === 'close') return closeTag(target)
  if (action === 'closeOthers') {
    appStore.closeOthers(target.name)
    if (route.name !== target.name) router.push(target.path)
    return
  }
  appStore.closeAll()
  const currentKept = appStore.tags.some((t) => t.name === route.name)
  if (!currentKept) router.push(appStore.tags[appStore.tags.length - 1]?.path ?? '/dashboard')
}

/* ---------- 页签工具 ---------- */
function closeAllTabs() {
  appStore.closeAll()
  const currentKept = appStore.tags.some((t) => t.name === route.name)
  if (!currentKept) router.push(appStore.tags[appStore.tags.length - 1]?.path ?? '/dashboard')
}
</script>

<template>
  <div class="tags-view">
    <div ref="tagsRef" class="tags-view__scroll">
      <div
        v-for="tag in appStore.tags"
        :key="tag.name"
        class="tag-item"
        :class="{ 'is-active': route.name === tag.name }"
        @click="selectTag(tag)"
        @contextmenu="openMenu(tag, $event)"
      >
        <span class="tag-item__label">{{ tag.title }}</span>
        <el-icon
          v-if="!tag.affix"
          class="tag-item__close"
          @click="closeTag(tag, $event)"
        >
          <Close />
        </el-icon>
      </div>
    </div>

    <div class="tags-view__tools">
      <el-tooltip content="刷新当前页" placement="bottom">
        <button class="tool-btn" type="button" @click="refreshCurrent">
          <el-icon><RefreshRight /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="关闭全部" placement="bottom">
        <button class="tool-btn" type="button" @click="closeAllTabs">
          <el-icon><Close /></el-icon>
        </button>
      </el-tooltip>
    </div>

    <!-- 右键菜单 -->
    <teleport to="body">
      <transition name="ctx-fade">
        <div
          v-if="menuVisible"
          class="tag-context-menu"
          :style="{ left: `${menuPos.x}px`, top: `${menuPos.y}px` }"
          @click.stop
        >
          <button type="button" @click="ctx('refresh')">
            <el-icon><RefreshRight /></el-icon>刷新当前
          </button>
          <button v-if="!ctxAffix" type="button" @click="ctx('close')">
            <el-icon><Close /></el-icon>关闭当前
          </button>
          <button type="button" @click="ctx('closeOthers')">
            <el-icon><ArrowRight /></el-icon>关闭其它
          </button>
          <button type="button" @click="ctx('closeAll')">
            <el-icon><Close /></el-icon>关闭全部
          </button>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<style scoped lang="scss">
.tags-view {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--tags-height);
  flex-shrink: 0; // 纵向容器中保持规范高度 34px，不被 el-main 挤压
  padding: 0 var(--sp-2) 0 var(--sp-3);
  background-color: var(--c-bg-surface);
  border-bottom: 1px solid var(--c-border);

  &__scroll {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    overflow-x: auto;
    scrollbar-width: none;
    height: 100%;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  &__tools {
    display: flex;
    align-items: center;
    gap: var(--sp-1);
    padding-left: var(--sp-2);
    flex-shrink: 0;
  }
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 var(--sp-2);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  background-color: var(--c-bg-subtle);
  color: var(--c-text-2);
  font-size: var(--fs-xs);
  cursor: pointer;
  white-space: nowrap;
  transition: background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out), border-color var(--dur-fast) var(--ease-out);

  &:hover {
    color: var(--c-text);
    background-color: var(--c-bg-muted);
  }

  &.is-active {
    background-color: var(--c-bg-surface);
    border-color: var(--c-border-strong);
    color: var(--c-text);
    font-weight: var(--fw-medium);
  }

  &__close {
    font-size: 12px;
    border-radius: var(--radius-full);
    padding: 1px;
    color: var(--c-text-3);

    &:hover {
      background-color: var(--c-bg-muted);
      color: var(--c-text);
    }
  }
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--c-text-3);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);

  &:hover {
    background-color: var(--c-bg-muted);
    color: var(--c-text);
  }
}

.tag-context-menu {
  position: fixed;
  z-index: var(--z-dropdown);
  min-width: 132px;
  padding: 4px;
  background: var(--c-bg-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);

  button {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    width: 100%;
    padding: 6px 10px;
    border: none;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--c-text-2);
    font-size: var(--fs-sm);
    cursor: pointer;
    text-align: left;

    &:hover {
      background-color: var(--c-bg-subtle);
      color: var(--c-text);
    }
  }
}

.ctx-fade-enter-active,
.ctx-fade-leave-active {
  transition: opacity var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.ctx-fade-enter-from,
.ctx-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
