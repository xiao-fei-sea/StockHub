<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, Plus, Refresh } from '@element-plus/icons-vue'
import {
  AppTable,
  OrderDetailDrawer,
  PageHeader,
  SearchForm,
  StatusTag,
  confirmDialog,
} from '@/components'
import type { DetailField, SearchField, TableColumn } from '@/components'
import {
  deleteUser,
  getUserDetail,
  getUsers,
  getUserWarehouses,
  resetUserPassword,
  updateUserStatus,
} from '@/api/user'
import { getDeptTree } from '@/api/dept'
import { getRoles } from '@/api/role'
import type { DeptNode, RoleOption, SysUser } from '@/types/entity'
import { ENABLE_STATUS, enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '@/views/base/components/status'
import { usePermission } from '@/composables/usePermission'
import UserFormDrawer from './components/UserFormDrawer.vue'
import DeptTreeDrawer from './components/DeptTreeDrawer.vue'
import AssignRoleDialog from './components/AssignRoleDialog.vue'
import AssignWarehouseDialog from './components/AssignWarehouseDialog.vue'

defineOptions({ name: 'SystemUser' })

const { hasPerm } = usePermission()
const canManageDept = computed(() => hasPerm(['sys:dept:add', 'sys:dept:edit', 'sys:dept:delete']))

/* ---------------- 左侧：部门树筛选 ---------------- */
const deptTree = ref<DeptNode[]>([])
const treeLoading = ref(false)
const selectedDeptId = ref<number | null>(null)
const treeRef = ref()

interface DeptTreeRow {
  key: string
  id: number | null
  label: string
  status?: number
  isAll?: boolean
  children?: DeptTreeRow[]
}

function wrapDept(n: DeptNode): DeptTreeRow {
  return {
    key: `dept-${n.id}`,
    id: n.id,
    label: n.deptName,
    status: n.status,
    children: n.children?.length ? n.children.map(wrapDept) : undefined,
  }
}

const deptDisplayTree = computed<DeptTreeRow[]>(() => [
  { key: 'dept-all', id: null, label: '全部部门', isAll: true, children: deptTree.value.map(wrapDept) },
])

async function loadDepts() {
  treeLoading.value = true
  try {
    deptTree.value = (await getDeptTree()) ?? []
    // 已选部门被删除/失效时自动回到全部部门
    if (selectedDeptId.value != null && !findDept(selectedDeptId.value)) {
      selectedDeptId.value = null
      searchUsers()
    }
    const key = selectedDeptId.value != null ? `dept-${selectedDeptId.value}` : 'dept-all'
    treeRef.value?.setCurrentKey(key)
  } finally {
    treeLoading.value = false
  }
}

function findDept(id: number, list: DeptNode[] = deptTree.value): DeptNode | null {
  for (const n of list) {
    if (n.id === id) return n
    const found = n.children?.length ? findDept(id, n.children) : null
    if (found) return found
  }
  return null
}

function onDeptClick(data: DeptTreeRow) {
  const next = data.isAll ? null : data.id
  if (next === selectedDeptId.value) return
  selectedDeptId.value = next
  searchUsers()
}

/** 部门增删改后：同步组织树与列表（部门名称变化会反映到列表）。 */
function onDeptChanged() {
  loadDepts()
  loadUsers()
}

const selectedDeptName = computed(() => {
  const id = selectedDeptId.value
  if (id == null) return ''
  return findDept(id)?.deptName ?? '已选部门'
})

const deptDrawerVisible = ref(false)

/* ---------------- 右侧：查询与列表 ---------------- */
const searchFields: SearchField[] = [
  { key: 'keyword', type: 'input', placeholder: '用户名 / 姓名 / 手机号' },
  {
    key: 'status',
    type: 'select',
    placeholder: '账号状态',
    options: ENABLE_STATUS.map((s) => ({ label: s.label, value: s.value })),
  },
]
const query = reactive<{ keyword?: string; status?: number | undefined }>({})

const loading = ref(false)
const list = ref<SysUser[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const columns: TableColumn[] = [
  { prop: 'username', label: '用户名', width: 130 },
  { prop: 'realName', label: '姓名', width: 100 },
  { prop: 'phone', label: '手机号', width: 130 },
  { prop: 'email', label: '邮箱', minWidth: 170, showOverflowTooltip: true },
  { prop: 'deptName', label: '部门', width: 120, showOverflowTooltip: true },
  { prop: 'status', label: '状态', width: 84, align: 'center' },
  { prop: 'loginDate', label: '最近登录', width: 165 },
  { prop: 'op', label: '操作', width: 196, align: 'right', fixed: 'right' },
]

function onQueryChange(value: Record<string, unknown>) {
  Object.assign(query, value)
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await getUsers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(query.keyword ? { keyword: query.keyword } : {}),
      ...(typeof query.status === 'number' ? { status: query.status } : {}),
      ...(selectedDeptId.value != null ? { deptId: selectedDeptId.value } : {}),
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
    if (!list.value.length && pageNum.value > 1) {
      pageNum.value -= 1
      await loadUsers()
    }
  } finally {
    loading.value = false
  }
}

function searchUsers() {
  pageNum.value = 1
  loadUsers()
}

function onPageChange({ pageNum: p, pageSize: s }: { pageNum: number; pageSize: number }) {
  pageNum.value = p
  pageSize.value = s
  loadUsers()
}

/* ---------------- 新增 / 编辑（用户表单抽屉） ---------------- */
const formVisible = ref(false)
const editUserId = ref<number | null>(null)

function openCreate() {
  editUserId.value = null
  formVisible.value = true
}

function openEdit(row: SysUser) {
  editUserId.value = row.id
  formVisible.value = true
}

/* ---------------- 删除 ---------------- */
async function onDelete(row: SysUser) {
  const ok = await confirmDialog({
    title: '删除用户',
    message: `确定删除用户「${row.realName}（${row.username}）」吗？\n删除会同时清理其角色与数据仓库授权（级联）；内置 admin 与当前登录账号不可删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch {
    /* 后端拦截（内置账号 / 引用）由 request 统一提示 */
  }
}

/* ---------------- 启用 / 停用 ---------------- */
async function onToggleStatus(row: SysUser) {
  const target: 0 | 1 = row.status === 1 ? 0 : 1
  const ok = await confirmDialog({
    title: target === 0 ? '停用用户' : '启用用户',
    message:
      target === 0
        ? `确定停用用户「${row.username}」吗？\n停用后该用户将立即无法登录。`
        : `确定启用用户「${row.username}」吗？\n启用后该用户可正常登录并按其角色权限操作。`,
    confirmText: target === 0 ? '停用' : '启用',
    type: target === 0 ? 'danger' : 'warning',
  })
  if (!ok) return
  try {
    await updateUserStatus(row.id, target)
    row.status = target
    ElMessage.success(target === 0 ? '已停用' : '已启用')
  } catch {
    /* admin / 当前账号保护由后端拦截提示 */
  }
}

/* ---------------- 重置密码（二次确认 + 新密码录入） ---------------- */
const resetVisible = ref(false)
const resetUserId = ref<number | null>(null)
const resetUsername = ref('')
const resetSubmitting = ref(false)
const resetFormRef = ref<FormInstance>()
const resetForm = reactive({ password: '' })

function validateResetPassword(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '')
  if (!v) {
    cb(new Error('请输入新密码'))
  } else if (v.length < 8 || v.length > 20 || !/[A-Za-z]/.test(v) || !/\d/.test(v)) {
    cb(new Error('密码需 8-20 位，且包含字母和数字'))
  } else {
    cb()
  }
}

const resetRules: FormRules = {
  password: [{ required: true, validator: validateResetPassword, trigger: 'blur' }],
}

async function onResetPassword(row: SysUser) {
  const ok = await confirmDialog({
    title: '重置密码',
    message: `确定重置用户「${row.realName}（${row.username}）」的登录密码吗？\n重置后原密码立即失效，该用户需使用新密码重新登录。`,
    confirmText: '继续重置',
    type: 'danger',
  })
  if (!ok) return
  resetUserId.value = row.id
  resetUsername.value = row.username
  resetForm.password = ''
  resetVisible.value = true
  resetFormRef.value?.clearValidate()
}

async function submitReset() {
  const valid = await resetFormRef.value?.validate().catch(() => false)
  if (!valid || resetUserId.value == null) return
  resetSubmitting.value = true
  try {
    await resetUserPassword(resetUserId.value, resetForm.password)
    ElMessage.success('密码重置成功')
    resetVisible.value = false
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    resetSubmitting.value = false
  }
}

/* ---------------- 分配角色 / 分配数据仓库 ---------------- */
const roleDialogVisible = ref(false)
const warehouseDialogVisible = ref(false)
const assignRow = ref<SysUser | null>(null)

function openAssignRole(row: SysUser) {
  assignRow.value = row
  roleDialogVisible.value = true
}

function openAssignWarehouse(row: SysUser) {
  assignRow.value = row
  warehouseDialogVisible.value = true
}

/* ---------------- 详情（含最近登录时间） ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SysUser | null>(null)
const detailRoleNames = ref('')
const detailWarehouseNames = ref('')

async function openDetail(row: SysUser) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  detailRoleNames.value = ''
  detailWarehouseNames.value = ''
  try {
    const [info, rolePage, whInfo] = await Promise.all([
      getUserDetail(row.id),
      getRoles({ pageNum: 1, pageSize: 100 }),
      getUserWarehouses(row.id).catch(() => null),
    ])
    detail.value = info
    const roleIds = info.roleIds ?? []
    const names = (rolePage.list ?? [])
      .filter((r: RoleOption) => roleIds.includes(r.id))
      .map((r: RoleOption) => r.roleName)
    detailRoleNames.value = names.length ? names.join('、') : '—'
    detailWarehouseNames.value = whInfo?.warehouses?.length
      ? whInfo.warehouses.map((w) => w.warehouseName).join('、')
      : '—'
  } catch {
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

const detailFields = computed<DetailField[]>(() => {
  const u = detail.value
  if (!u) return []
  return [
    { label: '用户名', value: u.username },
    { label: '真实姓名', value: u.realName },
    { label: '手机号', value: u.phone ?? '—' },
    { label: '邮箱', value: u.email ?? '—' },
    { label: '所属部门', value: u.deptName ?? '—' },
    { label: '角色', value: detailRoleNames.value },
    { label: '数据仓库', value: detailWarehouseNames.value },
    { label: '最近登录', value: u.loginDate ?? '—' },
    { label: '创建时间', value: u.createTime ?? '—' },
    { label: '创建人', value: u.createBy ?? '—' },
    { label: '更新时间', value: u.updateTime ?? '—' },
    { label: '更新人', value: u.updateBy ?? '—' },
  ]
})

/* ---------------- 更多操作菜单 ---------------- */
function onCommand(cmd: unknown, row: SysUser) {
  if (cmd === 'role') openAssignRole(row)
  else if (cmd === 'warehouse') openAssignWarehouse(row)
  else if (cmd === 'reset') onResetPassword(row)
  else if (cmd === 'status') onToggleStatus(row)
  else if (cmd === 'delete') onDelete(row)
}

onMounted(() => {
  loadDepts()
  loadUsers()
})
</script>

<template>
  <div class="page">
    <PageHeader title="用户管理" desc="账号分页查询、部门归属、角色分配与数据仓库数据权限的统一维护">
      <template #actions>
        <el-button v-permission="'sys:user:add'" type="primary" :icon="Plus" @click="openCreate">
          新增用户
        </el-button>
      </template>
    </PageHeader>

    <div class="split split--tree u-layout">
      <!-- 左侧：部门树（点节点筛选） -->
      <aside class="panel u-dept">
        <div class="panel__header">
          <span class="panel__title">
            部门
            <span class="panel__desc">点击筛选</span>
          </span>
          <div class="u-dept__ops">
            <el-button size="small" :icon="Refresh" :loading="treeLoading" @click="loadDepts">
              刷新
            </el-button>
            <el-button
              v-if="canManageDept"
              size="small"
              type="primary"
              plain
              @click="deptDrawerVisible = true"
            >
              组织维护
            </el-button>
          </div>
        </div>

        <div v-loading="treeLoading" class="u-dept__body">
          <el-tree
            ref="treeRef"
            :data="deptDisplayTree"
            node-key="key"
            highlight-current-row
            default-expand-all
            :expand-on-click-node="false"
            :props="{ label: 'label', children: 'children' }"
            empty-text="暂无部门数据"
            @node-click="onDeptClick"
          >
            <template #default="{ data }">
              <span class="tree-node" :class="{ 'is-inactive': data.status === 0 }">
                <span class="tree-node__label" :title="data.label">{{ data.label }}</span>
                <span v-if="data.status === 0" class="tree-node__flag">停用</span>
              </span>
            </template>
          </el-tree>
        </div>
      </aside>

      <!-- 右侧：查询 + 用户表格 -->
      <section class="u-main">
        <SearchForm
          :model-value="query"
          :fields="searchFields"
          :loading="loading"
          :collapsible="false"
          @update:model-value="onQueryChange"
          @search="searchUsers"
        />

        <div v-if="selectedDeptId != null" class="u-filter t-sm">
          <span class="t-3">当前部门筛选：</span>
          <el-tag size="small" closable @close="onDeptClick({ key: 'dept-all', id: null, label: '全部部门', isAll: true })">
            {{ selectedDeptName }}
          </el-tag>
        </div>

        <AppTable
          :columns="columns"
          :data="list"
          :loading="loading"
          :pagination="{ total, pageNum, pageSize }"
          row-key="id"
          stripe
          @row-click="(row) => openDetail(row as never)"
          @page-change="onPageChange"
        >
          <template #col-deptName="{ row }">
            <span :class="{ 't-3': !row.deptName }">{{ row.deptName || '未分配部门' }}</span>
          </template>
          <template #col-status="{ row }">
            <StatusTag :status="enableTagStatus(row.status)" :label="enableStatusInfo(row.status).label" />
          </template>
          <template #col-loginDate="{ row }">
            <span class="num t-sm" :class="{ 't-3': !row.loginDate }">{{ row.loginDate || '从未登录' }}</span>
          </template>
          <template #col-op="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button
              v-permission="'sys:user:edit'"
              link
              type="primary"
              size="small"
              @click.stop="openEdit(row)"
            >
              编辑
            </el-button>
            <span class="u-more" @click.stop>
              <el-dropdown trigger="click" @command="(cmd: unknown) => onCommand(cmd, row)">
                <el-button link type="primary" size="small">
                  更多
                  <el-icon class="u-more__arrow"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="hasPerm('sys:user:role')" command="role">
                      分配角色
                    </el-dropdown-item>
                    <el-dropdown-item v-if="hasPerm('sys:user:assignWarehouse')" command="warehouse">
                      分配数据仓库
                    </el-dropdown-item>
                    <el-dropdown-item v-if="hasPerm('sys:user:reset')" command="reset">
                      重置密码
                    </el-dropdown-item>
                    <el-dropdown-item v-if="hasPerm('sys:user:status')" command="status">
                      {{ row.status === 1 ? '停用账号' : '启用账号' }}
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="hasPerm('sys:user:delete')"
                      command="delete"
                      divided
                      class="is-danger-item"
                    >
                      删除用户
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </span>
          </template>
        </AppTable>
      </section>
    </div>

    <!-- 用户表单抽屉 -->
    <UserFormDrawer
      v-model:visible="formVisible"
      :user-id="editUserId"
      :dept-tree="deptTree"
      @saved="loadUsers"
    />

    <!-- 部门组织树维护抽屉 -->
    <DeptTreeDrawer v-model:visible="deptDrawerVisible" @changed="onDeptChanged" />

    <!-- 分配角色 -->
    <AssignRoleDialog
      v-model:visible="roleDialogVisible"
      :user-id="assignRow?.id ?? null"
      :username="assignRow?.username ?? ''"
      @success="loadUsers"
    />

    <!-- 分配数据仓库 -->
    <AssignWarehouseDialog
      v-model:visible="warehouseDialogVisible"
      :user-id="assignRow?.id ?? null"
      :username="assignRow?.username ?? ''"
      @success="loadUsers"
    />

    <!-- 重置密码 -->
    <el-dialog
      v-model="resetVisible"
      title="重置密码"
      width="440px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="重置后原密码立即失效，该用户需使用新密码重新登录。"
        class="u-reset-alert"
      />
      <p class="t-sm t-2 u-reset-user">
        重置对象：<strong>{{ resetUsername }}</strong>
      </p>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="88px" @submit.prevent>
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="resetForm.password"
            type="password"
            show-password
            placeholder="8-20 位，且包含字母和数字"
            maxlength="20"
            @keyup.enter="submitReset"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="danger" :loading="resetSubmitting" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 用户详情 -->
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      title="用户详情"
      :fields="detailFields"
      :loading="detailLoading"
    >
      <template #head-extra>
        <StatusTag
          v-if="detail"
          :status="enableTagStatus(detail.status)"
          :label="enableStatusInfo(detail.status).label"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="hasPerm('sys:user:edit')"
          type="primary"
          :loading="detailLoading"
          @click="detail && openEdit(detail)"
        >
          编辑该用户
        </el-button>
      </template>
    </OrderDetailDrawer>
  </div>
</template>

<style scoped lang="scss">
.u-layout {
  min-height: 480px;
}

.u-dept {
  display: flex;
  flex-direction: column;
  min-height: 0;

  &__ops {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__body {
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding: var(--sp-2);
  }
}

.u-main {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-width: 0;
}

.u-filter {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.u-more {
  display: inline-flex;
  align-items: center;

  &__arrow {
    margin-left: 2px;
  }
}

.u-reset-alert {
  margin-bottom: var(--sp-3);
}

.u-reset-user {
  margin: 0 0 var(--sp-3);
}

/* 分类树节点 */
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

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--c-primary-soft);
  color: var(--c-primary);
}

:deep(.is-danger-item) {
  color: var(--c-danger);
}
</style>
