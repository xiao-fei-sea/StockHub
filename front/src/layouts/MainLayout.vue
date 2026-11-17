<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Expand, Fold, FullScreen, RefreshRight, User } from '@element-plus/icons-vue'
import SidebarItem from './components/SidebarMenu.vue'
import TagsView from './components/TagsView.vue'
import { useAppStore } from '@/store/app'
import { usePermissionStore } from '@/store/permission'
import { useUserStore } from '@/store/user'
import { resetRouter } from '@/router'

defineOptions({ name: 'MainLayout' })

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const permStore = usePermissionStore()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

/** 面包屑（结构设计 §3.3）：首页仅显示“首页”；二级页显示“一级菜单 / 当前页面”。 */
const breadcrumbs = computed(() => {
  const title = route.meta.title ? String(route.meta.title) : ''
  if (!title) return []
  const items: { path: string; title: string }[] = []
  // 动态路由是 MainLayout 的扁平子路由，一级菜单需从菜单树反查（目录本身无路由，故不可点击）
  const parent = permStore.menus.find(
    (m) => m.menuType === 'C' && route.path.startsWith(`${m.path}/`),
  )
  if (parent) items.push({ path: parent.path, title: String(parent.menuName) })
  items.push({ path: route.path, title })
  return items
})

/* ---------- 侧栏折叠（独立页签，设计规范：操作时对当前标签隐藏侧栏） ---------- */
function toggleSidebar() {
  appStore.toggleSidebar()
}

/* ---------- 全屏 ---------- */
const fullscreen = ref(false)
function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    document.documentElement.requestFullscreen()
  }
}
document.addEventListener('fullscreenchange', () => {
  fullscreen.value = Boolean(document.fullscreenElement)
})

/* ---------- 用户下拉 ---------- */
const avatarChar = computed(() => (userStore.realName || userStore.userInfo?.username || '用').slice(0, 1))

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    await userStore.logout()
    resetRouter()
    appStore.reset()
    router.replace('/login')
  }
}
</script>

<template>
  <el-container class="layout">
    <!-- ===================== 侧边导航 ===================== -->
    <el-aside class="sidebar" :width="appStore.sidebarCollapsed ? 'var(--sidebar-width-collapsed)' : 'var(--sidebar-width)'">
      <div class="sidebar__logo">
        <span class="logo-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5v-9Z" />
            <path d="M3 7.5 12 12l9-4.5M12 12v9" />
          </svg>
        </span>
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">
          智仓云 <em>SmartWMS</em>
        </span>
      </div>

      <nav class="sidebar__menu" :aria-label="'主导航'">
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          router
          class="side-menu"
        >
          <SidebarItem
            v-for="item in permStore.sidebarMenus"
            :key="item.id"
            :item="item"
            base-path="/"
          />
        </el-menu>
      </nav>

      <div class="sidebar__footer">
        <span v-if="!appStore.sidebarCollapsed" class="version">v0.1.0 · 预生产</span>
      </div>
    </el-aside>

    <!-- ===================== 主区域 ===================== -->
    <el-container class="main">
      <!-- 必须用 el-header：el-container 依据子组件是否为 ElHeader 判定纵向排列，
           使用原生 header 会让主区域退化为横向，顶栏/页签/内容区挤成一行 -->
      <el-header class="topbar" height="50px">
        <div class="topbar__left">
          <button class="icon-btn" type="button" :title="appStore.sidebarCollapsed ? '展开导航' : '收起导航'" @click="toggleSidebar">
            <el-icon>
              <Fold v-if="!appStore.sidebarCollapsed" />
              <Expand v-else />
            </el-icon>
          </button>

          <el-breadcrumb class="breadcrumb" separator="/">
            <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="item.path + index">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="topbar__right">
          <button class="icon-btn" type="button" title="刷新页面" @click="$router.replace(`/redirect${route.fullPath}`)">
            <el-icon><RefreshRight /></el-icon>
          </button>
          <button class="icon-btn" type="button" :title="fullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen">
            <el-icon>
              <FullScreen v-if="!fullscreen" />
              <Expand v-else />
            </el-icon>
          </button>

          <span class="topbar__divider" />

          <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
            <button class="user-chip" type="button">
              <span class="user-chip__avatar">{{ avatarChar }}</span>
              <span class="user-chip__name">{{ userStore.realName }}</span>
              <el-icon class="user-chip__arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="user-menu__head">
                  <p class="user-menu__name">{{ userStore.realName }}</p>
                  <p class="user-menu__role">{{ userStore.userInfo?.username }} · {{ userStore.roles.join(' / ') || '普通用户' }}</p>
                </div>
                <el-dropdown-item command="logout" divided>
                  <el-icon><User /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <TagsView />

      <el-main class="content">
        <router-view v-slot="{ Component, route: current }">
          <keep-alive :include="appStore.cachedViews">
            <component :is="Component" :key="current.path" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout {
  height: 100vh;
  overflow: hidden;
  background-color: var(--c-bg-page);
}

/* ===================== 侧栏 ===================== */
.sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  background-color: var(--c-bg-surface);
  border-right: 1px solid var(--c-border);
  transition: width var(--dur) var(--ease-out);
  overflow: hidden;

  &__logo {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    height: var(--topbar-height);
    padding: 0 var(--sp-4);
    flex-shrink: 0;
    overflow: hidden;
    white-space: nowrap;
  }

  &__menu {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    padding: var(--sp-2) var(--sp-3) var(--sp-4);
  }

  &__footer {
    flex-shrink: 0;
    padding: var(--sp-3) var(--sp-4);
    border-top: 1px solid var(--c-border);
    color: var(--c-text-3);
    font-size: var(--fs-xs);
    white-space: nowrap;
    overflow: hidden;
  }
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background-color: var(--c-neutral-900);
  color: #fff;
}

.logo-text {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: var(--fs-md);
  font-weight: var(--fw-semibold);
  letter-spacing: -0.01em;
  color: var(--c-text);

  em {
    font-style: normal;
    font-size: var(--fs-xs);
    font-weight: var(--fw-regular);
    color: var(--c-text-3);
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
}

/* 侧栏菜单：轻量、留白充分、激活态用浅底而非色块 */
.side-menu {
  background-color: transparent;

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 36px;
    line-height: 36px;
    margin-bottom: 2px;
    border-radius: var(--radius-md);
    color: var(--c-text-2);
    font-size: var(--fs-sm);

    .el-icon {
      color: var(--c-text-3);
    }

    &:hover {
      background-color: var(--c-bg-subtle);
      color: var(--c-text);
      .el-icon { color: var(--c-text-2); }
    }
  }

  :deep(.el-menu-item.is-active) {
    background-color: var(--c-primary-soft);
    color: var(--c-primary-hover);
    font-weight: var(--fw-semibold);

    .el-icon {
      color: var(--c-primary-hover);
    }
  }

  :deep(.el-sub-menu .el-menu) {
    background: transparent;
    padding-left: var(--sp-4);
  }

  :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
    color: var(--c-text);
    font-weight: var(--fw-medium);
  }

  &.el-menu--collapse {
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      padding-left: 0 !important;
      text-align: center;

      span,
      .el-sub-menu__icon-arrow {
        display: none;
      }
    }
  }
}

/* ===================== 顶栏 ===================== */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: var(--topbar-height);
  flex-shrink: 0;
  padding: 0 var(--sp-3);
  background-color: var(--c-bg-surface);
  border-bottom: 1px solid var(--c-border);

  &__left {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    min-width: 0;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: var(--sp-1);
    flex-shrink: 0;
  }

  &__divider {
    width: 1px;
    height: 18px;
    margin: 0 var(--sp-2);
    background-color: var(--c-border);
  }
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--c-text-2);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);

  &:hover {
    background-color: var(--c-bg-subtle);
    color: var(--c-text);
  }
}

.breadcrumb {
  :deep(.el-breadcrumb__inner.is-link),
  :deep(.el-breadcrumb__inner) {
    color: var(--c-text-3);
    font-weight: var(--fw-regular);
  }
  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: var(--c-text);
    font-weight: var(--fw-medium);
  }
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  height: 34px;
  padding: 0 var(--sp-2) 0 var(--sp-1);
  border: none;
  border-radius: var(--radius-full);
  background: transparent;
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out);

  &:hover {
    background-color: var(--c-bg-subtle);
  }

  &__avatar {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border-radius: var(--radius-full);
    background-color: var(--c-neutral-800);
    color: #fff;
    font-size: var(--fs-xs);
    font-weight: var(--fw-semibold);
  }

  &__name {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  &__arrow {
    font-size: 12px;
    color: var(--c-text-3);
  }
}

:deep(.el-dropdown-menu__item.is-divided) {
  border-top: 1px solid var(--c-border);
}

.user-menu__head {
  padding: var(--sp-2) var(--sp-3) var(--sp-3);
  border-bottom: 1px solid var(--c-border);
  margin-bottom: var(--sp-1);

  .user-menu__name {
    font-size: var(--fs-base);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
  }

  .user-menu__role {
    margin-top: 2px;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    word-break: break-all;
  }
}

/* ===================== 内容区 ===================== */
/* 结构设计 §3.3：内容区为白底圆角卡片（radius 4px），页面级滚动在 el-main 上，卡片间留 16px 灰底 */
.content {
  margin: var(--sp-4);
  padding: var(--sp-4);
  overflow: auto;
  background-color: var(--c-bg-surface);
  border-radius: var(--radius-sm);
}

@media (max-width: 768px) {
  .breadcrumb,
  .user-chip__name {
    display: none;
  }
}
</style>
