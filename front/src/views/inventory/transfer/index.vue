<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { AppTable, PageHeader, StatusTag, WarehouseSelect, confirmDialog } from '@/components'
import type { TableColumn } from '@/components'
import {
  deleteTransfer,
  getTransfer,
  getTransfers,
  auditTransfer,
  cancelTransfer,
  postTransfer,
  rejectTransfer,
  submitTransfer,
  withdrawTransfer,
} from '@/api/transfer'
import type { TransferDetail, TransferOrder } from '@/types/entities/inventory'
import type { TransferQuery } from '@/api/transfer'
import { ORDER_STATUS, TRANSFER_CONFIRM_STATUS } from '@/utils/dict'
import { formatQty } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'
import OrderFormDrawer from './OrderFormDrawer.vue'
import OrderDetailDrawer from './OrderDetailDrawer.vue'
import type { TransferAction } from './OrderDetailDrawer.vue'

defineOptions({ name: 'InventoryTransfer' })

const { hasPerm } = usePermission()

/* ---------------- 状态分段 + 筛选 ---------------- */
type SegValue = number | undefined

const segments: { label: string; value: SegValue }[] = [
  { label: '全部', value: undefined },
  ...ORDER_STATUS.map((s) => ({ label: s.label, value: Number(s.value) as number })),
]
const statusSeg = ref<SegValue>(undefined)

const filters = reactive({
  transferNo: '',
  srcWarehouseId: null as number | null,
  destWarehouseId: null as number | null,
})
const dateRange = ref<string[]>([])
const pageNum = ref(1)
const pageSize = ref(20)

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<TransferOrder[]>([])
const total = ref(0)

function buildParams(): TransferQuery {
  const p: Record<string, unknown> = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    status: statusSeg.value,
    transferNo: filters.transferNo.trim() || undefined,
    srcWarehouseId: filters.srcWarehouseId ?? undefined,
    destWarehouseId: filters.destWarehouseId ?? undefined,
    startDate: dateRange.value[0] || undefined,
    endDate: dateRange.value[1] || undefined,
  }
  for (const k of Object.keys(p)) {
    const v = p[k]
    if (v === null || v === undefined || v === '') delete p[k]
  }
  return p as TransferQuery
}

async function loadList() {
  loading.value = true
  try {
    const res = await getTransfers(buildParams())
    list.value = res.list ?? []
    total.value = res.total ?? 0
    if (!list.value.length && pageNum.value > 1) {
      pageNum.value -= 1
      await loadList()
    }
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadList()
}

function onReset() {
  filters.transferNo = ''
  filters.srcWarehouseId = null
  filters.destWarehouseId = null
  dateRange.value = []
  onSearch()
}

function switchSeg(value: SegValue) {
  if (statusSeg.value === value) return
  statusSeg.value = value
  onSearch()
}

function onPageChange({ pageNum: p, pageSize: s }: { pageNum: number; pageSize: number }) {
  pageNum.value = p
  pageSize.value = s
  loadList()
}

/* ---------------- 新建 / 编辑抽屉 ---------------- */
const formVisible = ref(false)
const formOrder = ref<TransferDetail | null>(null)

function openCreate() {
  formOrder.value = null
  formVisible.value = true
}

async function fetchDetail(id: number): Promise<TransferDetail | null> {
  try {
    return await getTransfer(id)
  } catch {
    return null
  }
}

async function openEdit(row: TransferOrder) {
  const detail = await fetchDetail(row.id)
  if (!detail) return
  formOrder.value = detail
  formVisible.value = true
}

/* ---------------- 详情抽屉 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<TransferDetail | null>(null)

async function openDetail(row: TransferOrder) {
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await getTransfer(row.id)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function reloadDetail(id?: number) {
  if (!detailVisible.value || !detail.value) return
  if (id !== undefined && detail.value.id !== id) return
  detailLoading.value = true
  try {
    detail.value = await getTransfer(detail.value.id)
  } catch {
    // 保留原详情，错误已由 request 层提示
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 状态操作（submit/audit/reject/post/cancel/withdraw） ---------------- */
type ActionType = 'submit' | 'audit' | 'reject' | 'post' | 'cancel' | 'withdraw'

const ACTION_TEXT: Record<Exclude<ActionType, 'reject'>, { title: string; message: string; confirm: string; success: string; danger?: boolean }> = {
  submit: {
    title: '提交审核',
    message: '',
    confirm: '确认提交',
    success: '提交成功，单据已进入待审核',
  },
  audit: {
    title: '审核通过',
    message: '',
    confirm: '审核通过',
    success: '审核通过，可执行过账',
  },
  post: {
    title: '调拨过账',
    message: '',
    confirm: '确认过账',
    success: '过账成功，调拨库存已生效',
    danger: true,
  },
  cancel: {
    title: '取消单据',
    message: '',
    confirm: '确认取消',
    success: '单据已取消',
    danger: true,
  },
  withdraw: {
    title: '撤回至草稿',
    message: '',
    confirm: '确认撤回',
    success: '已撤回至草稿，可重新编辑',
  },
}

async function runAction(type: ActionType, row: TransferOrder) {
  const no = row.transferNo
  try {
    if (type === 'reject') {
      const { value } = await ElMessageBox.prompt(
        '驳回后单据退回创建人修改，可撤回至草稿重新编辑。请填写驳回原因（必填）。',
        `驳回调拨单 ${no}`,
        {
          confirmButtonText: '确认驳回',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请输入驳回原因',
          inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空'),
          autofocus: false,
          customStyle: { borderRadius: '8px' },
        },
      )
      await rejectTransfer(row.id, { reason: value.trim(), version: row.version })
      ElMessage.success('已驳回')
    } else {
      const meta = ACTION_TEXT[type]
      const message =
        type === 'post'
          ? `过账将同时执行源仓库存减少、目标仓库存增加并写入调拨流水，单据转为已完成且不可再修改。请逐字输入调拨单号 ${no} 确认。`
          : type === 'submit'
            ? `提交后调拨单 ${no} 将进入待审核，需审核通过后方可过账。确认提交？`
            : type === 'audit'
              ? `确认审核通过调拨单 ${no}？审核通过后可执行过账。`
              : type === 'cancel'
                ? `确认取消调拨单 ${no}？取消后单据进入已取消状态，不可恢复。`
                : `确认将调拨单 ${no} 撤回至草稿？撤回后可重新编辑并再次提交。`
      const ok = await confirmDialog({
        title: meta.title,
        message,
        confirmText: meta.confirm,
        type: meta.danger ? 'danger' : 'warning',
        ...(type === 'post' ? { requireInput: { text: no, placeholder: `请输入调拨单号 ${no}` } } : {}),
      })
      if (!ok) return
      const body = { version: row.version }
      if (type === 'submit') await submitTransfer(row.id, body)
      else if (type === 'audit') await auditTransfer(row.id, body)
      else if (type === 'post') await postTransfer(row.id, body)
      else if (type === 'cancel') await cancelTransfer(row.id, body)
      else await withdrawTransfer(row.id, body)
      ElMessage.success(meta.success)
    }
  } catch {
    // 取消操作或业务错误（request 统一提示）
    return
  }
  await loadList()
  await reloadDetail(row.id)
}

/* ---------------- 删除 ---------------- */
async function onDelete(row: TransferOrder) {
  const ok = await confirmDialog({
    title: '删除调拨单',
    message: `确定删除调拨单 ${row.transferNo} 吗？仅草稿可删除，删除后不可恢复。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteTransfer(row.id, row.version)
    ElMessage.success('删除成功')
  } catch {
    return
  }
  if (detailVisible.value && detail.value?.id === row.id) {
    detailVisible.value = false
    detail.value = null
  }
  await loadList()
}

/* ---------------- 详情抽屉动作分发 ---------------- */
function onDetailAction(type: TransferAction) {
  const current = detail.value
  if (!current) return
  if (type === 'edit') {
    detailVisible.value = false
    formOrder.value = current
    formVisible.value = true
    return
  }
  if (type === 'delete') {
    void onDelete(current)
    return
  }
  void runAction(type, current)
}

/* ---------------- 表格 ---------------- */
const columns: TableColumn[] = [
  { prop: 'transferNo', label: '调拨单号', width: 158 },
  { prop: 'status', label: '状态', width: 96, align: 'center' },
  { prop: 'srcWarehouseName', label: '源仓库', width: 120, showOverflowTooltip: true },
  { prop: 'destWarehouseName', label: '目标仓库', width: 120, showOverflowTooltip: true },
  { prop: 'confirmStatus', label: '目标仓确认', width: 104, align: 'center' },
  { prop: 'totalQty', label: '计划数量', width: 104, align: 'right' },
  { prop: 'actualQty', label: '实际数量', width: 104, align: 'right' },
  { prop: 'remark', label: '备注', minWidth: 140, showOverflowTooltip: true },
  { prop: 'createBy', label: '创建人', width: 96 },
  { prop: 'createTime', label: '创建时间', width: 164 },
  { prop: 'actions', label: '操作', width: 262, fixed: 'right', align: 'center' },
]

function confirmTagType(value?: string | null): 'success' | 'info' {
  return TRANSFER_CONFIRM_STATUS.find((o) => o.value === value)?.type === 'success' ? 'success' : 'info'
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <PageHeader title="库存调拨" desc="跨仓库调拨单据全流程：草稿录入 → 审核 → 过账（源仓减、目标仓增）">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadList">刷新</el-button>
        <el-button v-if="hasPerm('sys:transfer:add')" type="primary" :icon="Plus" @click="openCreate">
          新建调拨单
        </el-button>
      </template>
    </PageHeader>

    <!-- 状态分段 + 条件筛选 -->
    <section class="panel tf-ctl">
      <div class="seg" role="tablist" aria-label="按状态筛选">
        <button
          v-for="s in segments"
          :key="String(s.value)"
          type="button"
          class="seg__item"
          :class="{ 'is-active': statusSeg === s.value }"
          @click="switchSeg(s.value)"
        >
          {{ s.label }}
        </button>
      </div>

      <div class="tf-filters">
        <div class="fitem">
          <span class="fitem__label">调拨单号</span>
          <el-input
            v-model="filters.transferNo"
            placeholder="模糊匹配"
            clearable
            style="width: 168px"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="fitem">
          <span class="fitem__label">源仓库</span>
          <WarehouseSelect
            v-model="filters.srcWarehouseId"
            placeholder="全部"
            style="width: 160px"
          />
        </div>
        <div class="fitem">
          <span class="fitem__label">目标仓库</span>
          <WarehouseSelect
            v-model="filters.destWarehouseId"
            placeholder="全部"
            style="width: 160px"
          />
        </div>
        <div class="fitem">
          <span class="fitem__label">创建日期</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width: 236px"
          />
        </div>
        <div class="tf-filters__actions">
          <el-button type="primary" :icon="Search" :loading="loading" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
      </div>
    </section>

    <!-- 调拨单列表 -->
    <AppTable
      :columns="columns"
      :data="(list as unknown as Record<string, unknown>[])"
      :loading="loading"
      :serial="true"
      stripe
      :pagination="{ total, pageNum, pageSize }"
      :empty-text="statusSeg !== undefined || filters.transferNo || filters.srcWarehouseId || filters.destWarehouseId || dateRange.length ? '未查询到符合条件的调拨单' : '暂无调拨单，点击右上角新建'"
      @page-change="onPageChange"
    >
      <template #col-transferNo="{ row }">
        <span class="num">{{ (row as TransferOrder).transferNo }}</span>
      </template>
      <template #col-status="{ row }">
        <StatusTag :status="(row as TransferOrder).status" />
      </template>
      <template #col-confirmStatus="{ row }">
        <el-tag
          size="small"
          effect="light"
          :type="confirmTagType((row as TransferOrder).confirmStatus)"
        >
          {{ (row as TransferOrder).confirmStatus ?? '未确认' }}
        </el-tag>
      </template>
      <template #col-totalQty="{ row }">
        <span class="num">{{ formatQty((row as TransferOrder).totalQty) }}</span>
      </template>
      <template #col-actualQty="{ row }">
        <span class="num t-3">{{ formatQty((row as TransferOrder).actualQty) }}</span>
      </template>
      <template #col-actions="{ row }">
        <el-button
          v-if="hasPerm('sys:transfer:view')"
          link
          type="primary"
          @click.stop="openDetail(row as TransferOrder)"
        >
          详情
        </el-button>
        <template v-if="(row as TransferOrder).status === 0">
          <el-button
            v-if="hasPerm('sys:transfer:edit')"
            link
            type="primary"
            @click.stop="openEdit(row as TransferOrder)"
          >
            编辑
          </el-button>
          <el-button
            v-if="hasPerm('sys:transfer:submit')"
            link
            type="primary"
            @click.stop="runAction('submit', row as TransferOrder)"
          >
            提交
          </el-button>
          <el-button
            v-if="hasPerm('sys:transfer:delete')"
            link
            type="danger"
            @click.stop="onDelete(row as TransferOrder)"
          >
            删除
          </el-button>
        </template>
        <template v-else-if="(row as TransferOrder).status === 1">
          <el-button
            v-if="hasPerm('sys:transfer:audit')"
            link
            type="primary"
            @click.stop="runAction('audit', row as TransferOrder)"
          >
            审核
          </el-button>
          <el-button
            v-if="hasPerm('sys:transfer:reject')"
            link
            type="danger"
            @click.stop="runAction('reject', row as TransferOrder)"
          >
            驳回
          </el-button>
        </template>
        <template v-else-if="(row as TransferOrder).status === 2">
          <el-button
            v-if="hasPerm('sys:transfer:post')"
            link
            type="primary"
            @click.stop="runAction('post', row as TransferOrder)"
          >
            过账
          </el-button>
        </template>
        <template v-else-if="(row as TransferOrder).status === 4">
          <el-button
            v-if="hasPerm('sys:transfer:edit')"
            link
            type="primary"
            @click.stop="openEdit(row as TransferOrder)"
          >
            编辑
          </el-button>
          <el-button
            v-if="hasPerm('sys:transfer:withdraw')"
            link
            type="primary"
            @click.stop="runAction('withdraw', row as TransferOrder)"
          >
            撤回
          </el-button>
        </template>
      </template>
    </AppTable>

    <!-- 新建 / 编辑 -->
    <OrderFormDrawer
      v-model:visible="formVisible"
      :order="formOrder"
      @saved="loadList"
    />

    <!-- 详情 -->
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      :detail="detail"
      :loading="detailLoading"
      @action="onDetailAction"
    />
  </div>
</template>

<style scoped lang="scss">
.tf-ctl {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
}

/* 状态分段控件（纯色，无渐变） */
.seg {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 3px;
  background-color: var(--c-bg-muted);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-md);
  align-self: flex-start;

  &__item {
    border: none;
    background: transparent;
    padding: 5px 14px;
    font-family: inherit;
    font-size: var(--fs-sm);
    color: var(--c-text-2);
    border-radius: var(--radius-sm);
    cursor: pointer;
    transition:
      background-color var(--dur-fast) var(--ease-out),
      color var(--dur-fast) var(--ease-out);

    &:hover {
      color: var(--c-text);
    }

    &.is-active {
      background-color: var(--c-bg-surface);
      color: var(--c-primary);
      font-weight: var(--fw-medium);
      box-shadow: var(--shadow-xs);
    }
  }
}

.tf-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-3) var(--sp-4);

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-left: auto;
  }
}

.fitem {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  &__label {
    font-size: var(--fs-sm);
    color: var(--c-text-2);
    flex-shrink: 0;
  }
}

@media (max-width: 768px) {
  .tf-filters__actions {
    margin-left: 0;
  }

  .fitem {
    width: 100%;

    :deep(.el-input),
    :deep(.el-select),
    :deep(.el-date-editor) {
      flex: 1;
      width: auto !important;
    }
  }
}
</style>
