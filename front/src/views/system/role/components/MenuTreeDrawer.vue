<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { confirmDialog } from '@/components'
import type { MenuForm, MenuTreeNode } from '@/types/entity'
import type { MenuType } from '@/types/enum'
import { createMenu, deleteMenu, getMenuTree, updateMenu } from '@/api/menu'
import { usePermission } from '@/composables/usePermission'

/** 菜单权限树维护抽屉（sys:menu:add/edit/delete）：树内维护目录 C / 菜单 M / 按钮 B。 */
defineOptions({ name: 'MenuTreeDrawer' })

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [value: boolean]; changed: [] }>()

const { hasPerm } = usePermission()
const canOperate = computed(() =>
  hasPerm(['sys:menu:add', 'sys:menu:edit', 'sys:menu:delete']),
)

/* ---------------- 树数据 ---------------- */
const tree = ref<MenuTreeNode[]>([])
const treeLoading = ref(false)

async function loadTree() {
  treeLoading.value = true
  try {
    tree.value = (await getMenuTree()) ?? []
  } finally {
    treeLoading.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) loadTree()
  },
)

const TYPE_LABEL: Record<string, string> = { C: '目录', M: '菜单', B: '按钮' }

/** 节点合法性约束：根级仅目录；目录下建菜单；菜单下建按钮。 */
function childTypeOf(parentType: string | undefined): MenuType {
  if (parentType === 'M') return 'B'
  if (parentType === 'C') return 'M'
  return 'C'
}

function allowedChildLabel(parentType: string | undefined): string {
  return TYPE_LABEL[childTypeOf(parentType)] ?? '目录'
}

/* ---------------- 表单 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<MenuForm>({
  parentId: 0,
  menuName: '',
  menuType: 'C',
  perms: '',
  path: '',
  component: '',
  icon: '',
  sort: 0,
  visible: 1,
  status: 1,
})

const isMenuOrDir = computed(() => form.menuType !== 'B')
const isButton = computed(() => form.menuType === 'B')

const rules = computed<FormRules<MenuForm>>(() => {
  const base: FormRules<MenuForm> = {
    parentId: [{ required: true, type: 'number', message: '请选择上级节点', trigger: 'change' }],
    menuName: [
      { required: true, message: '请输入菜单名称', trigger: 'blur' },
      { max: 30, message: '菜单名称不能超过 30 字', trigger: 'blur' },
    ],
    sort: [{ type: 'integer', min: 0, message: '排序号需为不小于 0 的整数', trigger: 'change' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  }
  // 按类型联动（API-MENU-002 条件校验）：M 必填路由与组件，B 必填权限标识
  if (form.menuType === 'M') {
    base.path = [{ required: true, message: '请输入路由地址', trigger: 'blur' }]
    base.component = [{ required: true, message: '请输入组件路径', trigger: 'blur' }]
  }
  if (form.menuType === 'B') {
    base.perms = [{ required: true, message: '请输入权限标识', trigger: 'blur' }]
  }
  return base
})

/** 编辑时排除自身与子孙（防成环，API-MENU-003）。 */
const parentTree = computed(() => {
  interface Row {
    id: number
    menuName: string
    menuType: string
    children?: Row[]
  }
  if (!isEdit.value || editId.value == null) {
    // 新增：全量候选
    const all = (list: MenuTreeNode[]): Row[] =>
      list.map((n) => ({
        id: n.id,
        menuName: n.menuName,
        menuType: n.menuType,
        children: n.children?.length ? all(n.children) : undefined,
      }))
    return [{ id: 0, menuName: '顶级节点', menuType: 'C', children: all(tree.value) }]
  }
  const excluded = new Set<number>()
  const collect = (n: MenuTreeNode) => {
    excluded.add(n.id)
    n.children?.forEach(collect)
  }
  const findTarget = (list: MenuTreeNode[]) => {
    for (const n of list) {
      if (n.id === editId.value) collect(n)
      else findTarget(n.children ?? [])
    }
  }
  findTarget(tree.value)
  const filter = (list: MenuTreeNode[]): Row[] =>
    list
      .filter((n) => !excluded.has(n.id))
      .map((n) => ({
        id: n.id,
        menuName: n.menuName,
        menuType: n.menuType,
        children: n.children?.length ? filter(n.children) : undefined,
      }))
  return [{ id: 0, menuName: '顶级节点', menuType: 'C', children: filter(tree.value) }]
})

function openCreate(parentId = 0, parentType?: string) {
  isEdit.value = false
  editId.value = null
  Object.assign(form, {
    parentId,
    menuName: '',
    menuType: childTypeOf(parentType),
    perms: '',
    path: '',
    component: '',
    icon: '',
    sort: 0,
    visible: 1,
    status: 1,
  })
  nextTick(() => formRef.value?.clearValidate())
  dialogVisible.value = true
}

function openEdit(node: MenuTreeNode) {
  isEdit.value = true
  editId.value = node.id
  Object.assign(form, {
    parentId: node.parentId ?? 0,
    menuName: node.menuName,
    menuType: node.menuType,
    perms: node.perms ?? '',
    path: node.path ?? '',
    component: node.component ?? '',
    icon: node.icon ?? '',
    sort: node.sort ?? 0,
    visible: node.visible ?? 1,
    status: node.status ?? 1,
  })
  nextTick(() => formRef.value?.clearValidate())
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload: MenuForm = {
    parentId: form.parentId,
    menuName: form.menuName.trim(),
    menuType: form.menuType,
    sort: form.sort,
    visible: form.visible,
    status: form.status,
  }
  if (form.menuType === 'B') {
    payload.perms = form.perms?.trim()
  } else {
    payload.path = form.path?.trim()
    payload.component = form.component?.trim()
    payload.icon = form.icon?.trim() || undefined
  }
  if (isEdit.value && editId.value != null && form.parentId === editId.value) {
    ElMessage.warning('不能选择自身作为上级节点')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value && editId.value != null) {
      await updateMenu(editId.value, payload)
    } else {
      await createMenu(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadTree()
    emit('changed')
  } catch {
    /* 环形引用、重复等业务错误由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}

async function onDelete(node: MenuTreeNode) {
  const ok = await confirmDialog({
    title: '删除菜单节点',
    message: `确定删除${TYPE_LABEL[node.menuType] ?? ''}「${node.menuName}」吗？\n存在下级节点时后端将拒绝删除；删除会同时清除各角色对该节点的权限关联（级联影响：使用该节点标识的按钮权限将全部失效）。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteMenu(node.id)
    ElMessage.success('删除成功')
    await loadTree()
    emit('changed')
  } catch {
    /* 级联拦截由 request 统一提示 */
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="菜单权限树维护"
    size="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="menu-drawer">
      <div class="menu-drawer__bar">
        <span class="t-xs t-3">目录 → 菜单 → 按钮 三级结构，按钮须挂在菜单下</span>
        <div class="menu-drawer__ops">
          <el-button size="small" :icon="Refresh" :loading="treeLoading" @click="loadTree">刷新</el-button>
          <el-button
            v-if="hasPerm('sys:menu:add')"
            size="small"
            type="primary"
            :icon="Plus"
            @click="openCreate()"
          >
            新增顶级目录
          </el-button>
        </div>
      </div>

      <div v-loading="treeLoading" class="menu-drawer__tree">
        <el-tree
          :data="tree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :props="{ label: 'menuName', children: 'children' }"
          empty-text="暂无菜单数据"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <span class="tree-node__name" :title="data.menuName">{{ data.menuName }}</span>
              <span class="tree-node__type">{{ TYPE_LABEL[data.menuType] ?? data.menuType }}</span>
              <span v-if="data.status === 0" class="tree-node__flag">停用</span>
              <span v-if="data.perms" class="tree-node__perms mono t-xs" :title="data.perms">
                {{ data.perms }}
              </span>
              <span v-if="canOperate" class="tree-node__actions" @click.stop>
                <el-button
                  v-if="hasPerm('sys:menu:add') && data.menuType !== 'B'"
                  link
                  size="small"
                  :icon="Plus"
                  :title="`新增${allowedChildLabel(data.menuType)}（下级仅可为${allowedChildLabel(data.menuType)}）`"
                  @click="openCreate(data.id, data.menuType)"
                />
                <el-button
                  v-if="hasPerm('sys:menu:edit')"
                  link
                  size="small"
                  :icon="Edit"
                  title="编辑节点"
                  @click="openEdit(data)"
                />
                <el-button
                  v-if="hasPerm('sys:menu:delete')"
                  link
                  size="small"
                  :icon="Delete"
                  class="is-danger"
                  title="删除节点"
                  @click="onDelete(data)"
                />
              </span>
            </span>
          </template>
        </el-tree>
      </div>
    </div>

    <!-- 菜单表单 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑菜单节点' : '新增菜单节点'"
      width="520px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="上级节点" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTree"
            :props="{ label: 'menuName', children: 'children' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            placeholder="请选择上级节点"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="节点类型" prop="menuType">
          <el-radio-group v-model="form.menuType" :disabled="isEdit && !!editId">
            <el-radio :value="'C'">目录</el-radio>
            <el-radio :value="'M'">菜单</el-radio>
            <el-radio :value="'B'">按钮</el-radio>
          </el-radio-group>
          <p class="menu-form__hint t-xs t-3">
            {{ isEdit ? '类型创建后不可变更' : `按上级节点自动推荐：当前将创建${TYPE_LABEL[form.menuType]}` }}
          </p>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="如：用户管理" maxlength="30" />
        </el-form-item>
        <el-form-item v-if="isMenuOrDir" label="路由地址" prop="path">
          <el-input v-model="form.path" placeholder="如：/system/user" class="mono" />
        </el-form-item>
        <el-form-item v-if="isMenuOrDir" label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="如：system/user/index" class="mono" />
        </el-form-item>
        <el-form-item v-if="isButton" label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如：sys:user:add" maxlength="100" class="mono" />
        </el-form-item>
        <el-form-item v-if="isMenuOrDir" label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="选填，图标标识" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="isMenuOrDir" label="是否可见" prop="visible">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped lang="scss">
.menu-drawer {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    padding: var(--sp-3);
    background-color: var(--c-bg-subtle);
    border: 1px solid var(--c-border);
    border-radius: var(--radius-md);
  }

  &__ops {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    flex-shrink: 0;
  }

  &__tree {
    flex: 1;
    min-height: 0;
    overflow: auto;
    margin-top: var(--sp-3);
    padding: var(--sp-2);
    border: 1px solid var(--c-border);
    border-radius: var(--radius-md);
  }
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  padding-right: var(--sp-1);
  font-size: var(--fs-sm);

  &__name {
    flex-shrink: 0;
    max-width: 34%;
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

  &__flag {
    flex-shrink: 0;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }

  &__perms {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--c-text-3);
  }

  &__actions {
    display: flex;
    align-items: center;
    opacity: 0;
    flex-shrink: 0;
    transition: opacity var(--dur-fast) var(--ease-out);

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }

    .is-danger:hover {
      color: var(--c-danger);
    }
  }

  &:hover &__actions,
  &__actions:focus-within {
    opacity: 1;
  }
}

.menu-form__hint {
  width: 100%;
  margin: 4px 0 0;
  line-height: 1.5;
}

:deep(.el-tree-node__content) {
  height: 30px;
  border-radius: var(--radius-sm);

  &:hover {
    background-color: var(--c-bg-subtle);
  }
}
</style>
