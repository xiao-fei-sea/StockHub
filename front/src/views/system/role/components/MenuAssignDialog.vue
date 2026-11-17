<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Folder, Document, Menu as MenuIcon } from '@element-plus/icons-vue'
import type { MenuTreeNode } from '@/types/entity'
import { getMenuTree } from '@/api/menu'
import { assignRoleMenus, getRoleDetail } from '@/api/role'

/** 分配菜单权限弹窗：C/M/B 三级树全选/半选联动，提交 menuIds。 */
defineOptions({ name: 'MenuAssignDialog' })

const props = withDefaults(
  defineProps<{
    visible: boolean
    roleId: number | null
    roleName?: string
  }>(),
  { roleName: '' },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const loading = ref(false)
const submitting = ref(false)
const menuTree = ref<MenuTreeNode[]>([])
const treeRef = ref()
const error = ref('')
const ready = ref(false)
/** 当前勾选节点数（el-tree 勾选态非响应式，事件驱动同步）。 */
const checkedCount = ref(0)

/** 树内全部节点 id（供全选使用）。 */
function collectIds(list: MenuTreeNode[], acc: number[] = []): number[] {
  for (const n of list) {
    acc.push(n.id)
    if (n.children?.length) collectIds(n.children, acc)
  }
  return acc
}

const allIds = computed(() => collectIds(menuTree.value))

async function load() {
  if (props.roleId == null) return
  loading.value = true
  error.value = ''
  ready.value = false
  try {
    const [tree, detail] = await Promise.all([getMenuTree(), getRoleDetail(props.roleId)])
    menuTree.value = tree ?? []
    await nextTick()
    const checked = detail.menuIds ?? []
    treeRef.value?.setCheckedKeys(checked)
    checkedCount.value = treeRef.value?.getCheckedKeys?.().length ?? 0
    ready.value = true
  } catch {
    error.value = '权限数据加载失败，请关闭后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      menuTree.value = []
      error.value = ''
      ready.value = false
      load()
    }
  },
)

function checkAll() {
  treeRef.value?.setCheckedKeys(allIds.value)
  checkedCount.value = allIds.value.length
  error.value = ''
}

function clearAll() {
  treeRef.value?.setCheckedKeys([])
  error.value = ''
  checkedCount.value = 0
}

function onCheck() {
  checkedCount.value = treeRef.value?.getCheckedKeys?.().length ?? 0
  error.value = ''
}

async function submit() {
  if (props.roleId == null || !ready.value) return
  const checked: number[] = treeRef.value?.getCheckedKeys?.() ?? []
  const halfChecked: number[] = treeRef.value?.getHalfCheckedKeys?.() ?? []
  if (!checked.length) {
    error.value = '请至少勾选一项菜单权限'
    return
  }
  // 级联提交：勾选子节点自动带上父节点（含半选父级），保证后端路径完整
  const menuIds = [...new Set([...checked, ...halfChecked])]
  submitting.value = true
  try {
    await assignRoleMenus(props.roleId, menuIds)
    ElMessage.success('菜单权限分配成功')
    emit('update:visible', false)
    emit('success')
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}

const TYPE_LABEL: Record<string, string> = { C: '目录', M: '菜单', B: '按钮' }
const TYPE_ICON: Record<string, unknown> = { C: Folder, M: Document, B: MenuIcon }
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="分配菜单权限"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="menu-assign">
      <div class="menu-assign__bar">
        <span class="t-sm t-2">
          为角色「<strong>{{ roleName || '—' }}</strong>」勾选可访问的目录、菜单与按钮
        </span>
        <span class="menu-assign__ops">
          <el-button size="small" :disabled="!ready || !allIds.length" @click="checkAll">全选</el-button>
          <el-button size="small" :disabled="!ready" @click="clearAll">清空</el-button>
        </span>
      </div>

      <div class="menu-assign__tree">
        <el-tree
          ref="treeRef"
          :data="menuTree"
          node-key="id"
          show-checkbox
          default-expand-all
          :expand-on-click-node="false"
          :props="{ label: 'menuName', children: 'children' }"
          empty-text="暂无菜单权限数据"
          @check="onCheck"
        >
          <template #default="{ data }">
            <span class="menu-node">
              <el-icon class="menu-node__icon" :size="14">
                <component :is="TYPE_ICON[data.menuType] ?? Document" />
              </el-icon>
              <span class="menu-node__name" :title="data.menuName">{{ data.menuName }}</span>
              <span class="menu-node__type">{{ TYPE_LABEL[data.menuType] ?? data.menuType }}</span>
              <span v-if="data.perms" class="menu-node__perms mono t-xs" :title="data.perms">
                {{ data.perms }}
              </span>
            </span>
          </template>
        </el-tree>
      </div>

      <div class="menu-assign__foot">
        <span class="t-xs t-3">已勾选 {{ checkedCount }} 项；半选父级将自动随子项提交</span>
        <span v-if="error" class="menu-assign__error t-sm">{{ error }}</span>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!ready" @click="submit">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.menu-assign {
  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    margin-bottom: var(--sp-3);
  }

  &__ops {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    flex-shrink: 0;
  }

  &__tree {
    height: 380px;
    overflow: auto;
    padding: var(--sp-2) var(--sp-3);
    border: 1px solid var(--c-border);
    border-radius: var(--radius-md);
    background-color: var(--c-bg-subtle);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    min-height: 20px;
    margin-top: var(--sp-3);
  }

  &__error {
    color: var(--c-danger);
  }
}

.menu-node {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
  padding-right: var(--sp-1);
  font-size: var(--fs-sm);

  &__icon {
    color: var(--c-text-3);
    flex-shrink: 0;
  }

  &__name {
    flex-shrink: 0;
    max-width: 40%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__type {
    flex-shrink: 0;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    border: 1px solid var(--c-border);
    border-radius: var(--radius-sm);
    padding: 0 4px;
    line-height: 16px;
  }

  &__perms {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--c-text-3);
  }
}

:deep(.el-tree-node__content) {
  height: 28px;

  &:hover {
    background-color: var(--c-bg-muted);
  }
}
</style>
