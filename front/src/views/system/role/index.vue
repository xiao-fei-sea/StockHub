<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Plus } from '@element-plus/icons-vue'
import {
  AppTable,
  OrderDetailDrawer,
  PageHeader,
  SearchForm,
  StatusTag,
  confirmDialog,
} from '@/components'
import type { DetailField, SearchField, TableColumn } from '@/components'
import { deleteRole, getRoleDetail, getRoles, updateRole } from '@/api/role'
import type { SysRole } from '@/types/entity'
import { ENABLE_STATUS, enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '@/views/base/components/status'
import { usePermission } from '@/composables/usePermission'
import RoleFormDialog from './components/RoleFormDialog.vue'
import MenuAssignDialog from './components/MenuAssignDialog.vue'
import MenuTreeDrawer from './components/MenuTreeDrawer.vue'

defineOptions({ name: 'SystemRole' })

const { hasPerm } = usePermission()
const canManageMenu = computed(() =>
  hasPerm(['sys:menu:add', 'sys:menu:edit', 'sys:menu:delete']),
)

/* ---------------- 查询与列表 ---------------- */
const searchFields: SearchField[] = [
  { key: 'keyword', type: 'input', placeholder: '角色名称 / 角色标识' },
  {
    key: 'status',
    type: 'select',
    placeholder: '启用状态',
    options: ENABLE_STATUS.map((s) => ({ label: s.label, value: s.value })),
  },
]
const query = reactive<{ keyword?: string; status?: number | undefined }>({})

const loading = ref(false)
const list = ref<SysRole[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const columns: TableColumn[] = [
  { prop: 'roleName', label: '角色名称', width: 160 },
  { prop: 'roleKey', label: '角色标识', width: 170 },
  { prop: 'roleSort', label: '排序', width: 80, align: 'center' },
  { prop: 'status', label: '状态', width: 88, align: 'center' },
  { prop: 'remark', label: '备注', minWidth: 180, showOverflowTooltip: true },
  { prop: 'createTime', label: '创建时间', width: 165 },
  { prop: 'op', label: '操作', width: 218, align: 'right', fixed: 'right' },
]

function onQueryChange(value: Record<string, unknown>) {
  Object.assign(query, value)
}

async function loadRoles() {
  loading.value = true
  try {
    const res = await getRoles({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(query.keyword ? { keyword: query.keyword } : {}),
      ...(typeof query.status === 'number' ? { status: query.status } : {}),
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
    if (!list.value.length && pageNum.value > 1) {
      pageNum.value -= 1
      await loadRoles()
    }
  } finally {
    loading.value = false
  }
}

function searchRoles() {
  pageNum.value = 1
  loadRoles()
}

function onPageChange({ pageNum: p, pageSize: s }: { pageNum: number; pageSize: number }) {
  pageNum.value = p
  pageSize.value = s
  loadRoles()
}

/* ---------------- 新增 / 编辑 ---------------- */
const formVisible = ref(false)
const editRoleId = ref<number | null>(null)

function openCreate() {
  editRoleId.value = null
  formVisible.value = true
}

function openEdit(row: SysRole) {
  editRoleId.value = row.id
  formVisible.value = true
}

/* ---------------- 分配菜单权限 ---------------- */
const menuAssignVisible = ref(false)
const assignRow = ref<SysRole | null>(null)

function openMenuAssign(row: SysRole) {
  assignRow.value = row
  menuAssignVisible.value = true
}

/* ---------------- 菜单权限树维护 ---------------- */
const menuDrawerVisible = ref(false)

/* ---------------- 启用 / 停用 ---------------- */
async function onToggleStatus(row: SysRole) {
  const target: 0 | 1 = row.status === 1 ? 0 : 1
  const ok = await confirmDialog({
    title: target === 0 ? '停用角色' : '启用角色',
    message:
      target === 0
        ? `确定停用角色「${row.roleName}」吗？\n停用后拥有该角色的用户将立即失去对应的菜单权限。`
        : `确定启用角色「${row.roleName}」吗？\n启用后该角色的菜单权限将恢复生效。`,
    confirmText: target === 0 ? '停用' : '启用',
    type: target === 0 ? 'danger' : 'warning',
  })
  if (!ok) return
  try {
    await updateRole(row.id, {
      roleName: row.roleName,
      roleKey: row.roleKey,
      roleSort: row.roleSort ?? 0,
      status: target,
      remark: row.remark ?? '',
    })
    row.status = target
    ElMessage.success(target === 0 ? '已停用' : '已启用')
  } catch {
    /* 内置角色保护由后端拦截提示 */
  }
}

/* ---------------- 删除 ---------------- */
async function onDelete(row: SysRole) {
  const ok = await confirmDialog({
    title: '删除角色',
    message: `确定删除角色「${row.roleName}（${row.roleKey}）」吗？\n删除将同时清除其全部菜单权限关联（级联）；若该角色已关联用户或属于内置角色，后端将拒绝删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    loadRoles()
  } catch {
    /* 引用校验拦截由 request 统一提示 */
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SysRole | null>(null)
const detailMenuCount = ref(0)

async function openDetail(row: SysRole) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  detailMenuCount.value = 0
  try {
    const d = await getRoleDetail(row.id)
    detail.value = d
    detailMenuCount.value = d.menuIds?.length ?? 0
  } catch {
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

const detailFields = computed<DetailField[]>(() => {
  const r = detail.value
  if (!r) return []
  return [
    { label: '角色名称', value: r.roleName },
    { label: '角色标识', value: r.roleKey },
    { label: '排序号', value: r.roleSort ?? 0 },
    { label: '权限节点', value: `${detailMenuCount.value} 项` },
    { label: '备注', value: r.remark || '—' },
    { label: '创建时间', value: r.createTime ?? '—' },
    { label: '创建人', value: r.createBy ?? '—' },
    { label: '更新时间', value: r.updateTime ?? '—' },
  ]
})

/* ---------------- 更多操作 ---------------- */
function onCommand(cmd: unknown, row: SysRole) {
  if (cmd === 'status') onToggleStatus(row)
  else if (cmd === 'delete') onDelete(row)
}

onMounted(() => {
  loadRoles()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="角色权限"
      desc="角色分页维护、菜单权限分配与菜单权限树（目录/菜单/按钮）统一管理"
    >
      <template #actions>
        <el-button
          v-if="canManageMenu"
          @click="menuDrawerVisible = true"
        >
          菜单权限树
        </el-button>
        <el-button v-permission="'sys:role:add'" type="primary" :icon="Plus" @click="openCreate">
          新增角色
        </el-button>
      </template>
    </PageHeader>

    <div class="r-page">
      <SearchForm
        :model-value="query"
        :fields="searchFields"
        :loading="loading"
        :collapsible="false"
        @update:model-value="onQueryChange"
        @search="searchRoles"
      />

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
        <template #col-roleKey="{ row }">
          <span class="mono">{{ row.roleKey }}</span>
        </template>
        <template #col-roleSort="{ row }">
          <span class="num">{{ row.roleSort ?? 0 }}</span>
        </template>
        <template #col-status="{ row }">
          <StatusTag :status="enableTagStatus(row.status)" :label="enableStatusInfo(row.status).label" />
        </template>
        <template #col-remark="{ row }">
          <span :class="{ 't-3': !row.remark }">{{ row.remark || '—' }}</span>
        </template>
        <template #col-op="{ row }">
          <el-button link type="primary" size="small" @click.stop="openDetail(row)">
            详情
          </el-button>
          <el-button
            v-permission="'sys:role:edit'"
            link
            type="primary"
            size="small"
            @click.stop="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'sys:role:assign'"
            link
            type="primary"
            size="small"
            @click.stop="openMenuAssign(row)"
          >
            分配权限
          </el-button>
          <span class="r-more" @click.stop>
            <el-dropdown trigger="click" @command="(cmd: unknown) => onCommand(cmd, row)">
              <el-button link type="primary" size="small">
                更多
                <el-icon class="r-more__arrow"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="hasPerm('sys:role:status')" command="status">
                    {{ row.status === 1 ? '停用角色' : '启用角色' }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="hasPerm('sys:role:delete')"
                    command="delete"
                    divided
                    class="is-danger-item"
                  >
                    删除角色
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </template>
      </AppTable>
    </div>

    <!-- 角色表单 -->
    <RoleFormDialog v-model:visible="formVisible" :role-id="editRoleId" @saved="loadRoles" />

    <!-- 分配菜单权限 -->
    <MenuAssignDialog
      v-model:visible="menuAssignVisible"
      :role-id="assignRow?.id ?? null"
      :role-name="assignRow?.roleName ?? ''"
      @success="loadRoles"
    />

    <!-- 菜单权限树维护 -->
    <MenuTreeDrawer v-model:visible="menuDrawerVisible" />

    <!-- 角色详情 -->
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      title="角色详情"
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
          v-if="hasPerm('sys:role:edit')"
          type="primary"
          :loading="detailLoading"
          @click="detail && openEdit(detail)"
        >
          编辑该角色
        </el-button>
      </template>
    </OrderDetailDrawer>
  </div>
</template>

<style scoped lang="scss">
.r-page {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.r-more {
  display: inline-flex;
  align-items: center;

  &__arrow {
    margin-left: 2px;
  }
}

:deep(.is-danger-item) {
  color: var(--c-danger);
}
</style>
