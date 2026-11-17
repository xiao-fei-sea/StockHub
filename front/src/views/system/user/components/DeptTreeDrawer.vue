<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { confirmDialog } from '@/components'
import type { DeptForm, DeptNode } from '@/types/entity'
import { createDept, deleteDept, getDeptTree, updateDept } from '@/api/dept'
import { usePermission } from '@/composables/usePermission'

/** 部门组织树维护抽屉（FR-004）：树内增删改部门，权限 sys:dept:add/edit/delete。 */
defineOptions({ name: 'DeptTreeDrawer' })

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [value: boolean]; changed: [] }>()

const { hasPerm } = usePermission()
const canOperate = computed(() => hasPerm(['sys:dept:add', 'sys:dept:edit', 'sys:dept:delete']))

/* ---------------- 树数据 ---------------- */
const tree = ref<DeptNode[]>([])
const treeLoading = ref(false)

async function loadTree() {
  treeLoading.value = true
  try {
    tree.value = (await getDeptTree()) ?? []
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

/** 展示用树（根级「顶级部门」便于统一渲染）。 */
interface DeptTreeRow {
  id: number
  parentId: number
  deptName: string
  status: number
  sort?: number
  isRoot?: boolean
  raw?: DeptNode
  children?: DeptTreeRow[]
}

function wrap(n: DeptNode): DeptTreeRow {
  return {
    id: n.id,
    parentId: n.parentId,
    deptName: n.deptName,
    status: n.status,
    sort: n.sort,
    raw: n,
    children: n.children?.length ? n.children.map(wrap) : undefined,
  }
}

const displayTree = computed<DeptTreeRow[]>(() => [
  {
    id: 0,
    parentId: -1,
    deptName: '顶级部门',
    status: 1,
    isRoot: true,
    children: tree.value.map(wrap),
  },
])

/* ---------------- 部门表单 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<DeptForm>({ parentId: 0, deptName: '', sort: 0, status: 1 })

const rules: FormRules<DeptForm> = {
  parentId: [{ required: true, type: 'number', message: '请选择上级部门', trigger: 'change' }],
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { max: 30, message: '部门名称不能超过 30 字', trigger: 'blur' },
  ],
  sort: [{ type: 'integer', min: 0, message: '排序号需为不小于 0 的整数', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

/** 上级部门候选树：编辑时排除自身与子孙（防成环，接口文档 API-DEPT-003）。 */
const parentTree = computed(() => {
  if (!isEdit.value || editId.value == null) return displayTree.value
  const excluded = new Set<number>()
  const collect = (n: DeptNode) => {
    excluded.add(n.id)
    n.children?.forEach(collect)
  }
  const findTarget = (list: DeptNode[]) => {
    for (const n of list) {
      if (n.id === editId.value) collect(n)
      else findTarget(n.children ?? [])
    }
  }
  findTarget(tree.value)
  const filter = (list: DeptNode[]): DeptTreeRow[] =>
    list
      .filter((n) => !excluded.has(n.id))
      .map((n) => ({
        id: n.id,
        parentId: n.parentId,
        deptName: n.deptName,
        status: n.status,
        raw: n,
        children: n.children?.length ? filter(n.children) : undefined,
      }))
  return [{ id: 0, parentId: -1, deptName: '顶级部门', status: 1, isRoot: true, children: filter(tree.value) }]
})

function openCreate(parentId = 0) {
  isEdit.value = false
  editId.value = null
  Object.assign(form, { parentId, deptName: '', sort: 0, status: 1 })
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function openEdit(row: DeptTreeRow) {
  const raw = row.raw as DeptNode
  isEdit.value = true
  editId.value = raw.id
  Object.assign(form, {
    parentId: raw.parentId ?? 0,
    deptName: raw.deptName,
    sort: raw.sort ?? 0,
    status: raw.status ?? 1,
  })
  nextTick(() => formRef.value?.clearValidate())
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (isEdit.value && editId.value != null && form.parentId === editId.value) {
    ElMessage.warning('不能选择自身作为上级部门')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value && editId.value != null) {
      await updateDept(editId.value, { ...form })
    } else {
      await createDept({ ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadTree()
    emit('changed')
  } catch {
    /* 同级重名 40904 等业务错误由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: DeptTreeRow) {
  const ok = await confirmDialog({
    title: '删除部门',
    message: `确定删除部门「${row.deptName}」吗？\n删除为逻辑删除；若存在下级部门或部门下仍有用户，后端将拒绝删除，且该部门将不再出现在组织架构中。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    await loadTree()
    emit('changed')
  } catch {
    /* 级联引用拦截由 request 统一提示 */
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="部门组织架构维护"
    size="460px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="dept-drawer">
      <div class="dept-drawer__bar">
        <span class="t-xs t-3">点击节点右侧图标可增删改，改动即时生效</span>
        <div class="dept-drawer__ops">
          <el-button size="small" :icon="Refresh" :loading="treeLoading" @click="loadTree">刷新</el-button>
          <el-button
            v-if="hasPerm('sys:dept:add')"
            size="small"
            type="primary"
            plain
            :icon="Plus"
            @click="openCreate(0)"
          >
            新增部门
          </el-button>
        </div>
      </div>

      <div v-loading="treeLoading" class="dept-drawer__tree">
        <el-tree
          :data="displayTree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :props="{ label: 'deptName', children: 'children' }"
          empty-text="暂无部门数据"
        >
          <template #default="{ data }">
            <span class="tree-node" :class="{ 'is-inactive': data.status === 0 }">
              <span class="tree-node__label" :title="data.deptName">{{ data.deptName }}</span>
              <span v-if="data.status === 0" class="tree-node__flag">停用</span>
              <span v-if="canOperate" class="tree-node__actions" @click.stop>
                <el-button
                  v-if="hasPerm('sys:dept:add')"
                  link
                  size="small"
                  :icon="Plus"
                  :title="data.isRoot ? '新增顶级部门' : '新增子部门'"
                  @click="openCreate(data.id)"
                />
                <el-button
                  v-if="!data.isRoot && hasPerm('sys:dept:edit')"
                  link
                  size="small"
                  :icon="Edit"
                  title="编辑部门"
                  @click="openEdit(data)"
                />
                <el-button
                  v-if="!data.isRoot && hasPerm('sys:dept:delete')"
                  link
                  size="small"
                  :icon="Delete"
                  class="is-danger"
                  title="删除部门"
                  @click="onDelete(data)"
                />
              </span>
            </span>
          </template>
        </el-tree>
      </div>
    </div>

    <!-- 部门表单 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑部门' : '新增部门'"
      width="440px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            placeholder="请选择上级部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="同级下不可重复" maxlength="30" />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :precision="0" style="width: 100%" />
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
.dept-drawer {
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

  &__label {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__flag {
    flex-shrink: 0;
    font-size: var(--fs-xs);
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

  &.is-inactive .tree-node__label {
    color: var(--c-text-3);
  }
}

:deep(.el-tree-node__content) {
  height: 30px;
  border-radius: var(--radius-sm);

  &:hover {
    background-color: var(--c-bg-subtle);
  }
}
</style>
