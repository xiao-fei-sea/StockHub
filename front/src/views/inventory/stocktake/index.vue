<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { AppTable, PageHeader, StatusTag, WarehouseSelect, confirmDialog } from '@/components'
import type { TableColumn } from '@/components'
import {
  auditStocktake,
  cancelStocktake,
  deleteStocktake,
  getStocktake,
  getStocktakes,
  postStocktake,
  rejectStocktake,
  submitStocktake,
  updateStocktake,
  withdrawStocktake,
} from '@/api/stocktake'
import type {
  StocktakeDetail,
  StocktakeInputForm,
  StocktakeOrder,
} from '@/types/entities/inventory'
import type { StocktakeQuery } from '@/api/stocktake'
import { ORDER_STATUS } from '@/utils/dict'
import { formatQty } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'
import OrderFormDrawer from './OrderFormDrawer.vue'
import OrderDetailDrawer from './OrderDetailDrawer.vue'
import type { StocktakeAction } from './OrderDetailDrawer.vue'

defineOptions({ name: 'InventoryStocktake' })

const { hasPerm } = usePermission()

/* ---------------- 状态分段 + 筛选 ---------------- */
type SegValue = number | undefined

const segments: { label: string; value: SegValue }[] = [
  { label: '全部', value: undefined },
  ...ORDER_STATUS.map((s) => ({ label: s.label, value: Number(s.value) as number })),
]
const statusSeg = ref<SegValue>(undefined)

const filters = reactive({
  stocktakeNo: '',
  keyword: '',
  warehouseId: null as number | null,
  diffStatus: undefined as string | undefined,
})
const dateRange = ref<string[]>([])
const pageNum = ref(1)
const pageSize = ref(20)

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<StocktakeOrder[]>([])
const total = ref(0)

function buildParams(): StocktakeQuery {
  const p: Record<string, unknown> = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    status: statusSeg.value,
    stocktakeNo: filters.stocktakeNo.trim() || undefined,
    keyword: filters.keyword.trim() || undefined,
    warehouseId: filters.warehouseId ?? undefined,
    diffStatus: filters.diffStatus,
    startDate: dateRange.value[0] || undefined,
    endDate: dateRange.value[1] || undefined,
  }
  for (const k of Object.keys(p)) {
    const v = p[k]
    if (v === null || v === undefined || v === '') delete p[k]
  }
  return p as StocktakeQuery
}

async function loadList() {
  loading.value = true
  try {
    const res = await getStocktakes(buildParams())
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
  filters.stocktakeNo = ''
  filters.keyword = ''
  filters.warehouseId = null
  filters.diffStatus = undefined
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

/* ---------------- 新建抽屉 ---------------- */
const formVisible = ref(false)

function openCreate() {
  formVisible.value = true
}

/* ---------------- 详情抽屉 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<StocktakeDetail | null>(null)

async function openDetail(row: StocktakeOrder) {
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await getStocktake(row.id)
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
    detail.value = await getStocktake(detail.value.id)
  } catch {
    // 保留原详情，错误已由 request 层提示
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 实盘录入保存（API-STK-004） ---------------- */
async function onSaveInput(body: StocktakeInputForm) {
  if (!detail.value) return
  detailLoading.value = true
  try {
    await updateStocktake(detail.value.id, body)
    ElMessage.success('实盘数已保存')
    await reloadDetail()
    await loadList()
  } catch {
    // 业务错误已由 request 层统一提示，保留录入内容供重试
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 状态操作 ---------------- */
type ActionType = 'submit' | 'audit' | 'reject' | 'post' | 'cancel' | 'withdraw'

const ACTION_META: Record<
  Exclude<ActionType, 'reject'>,
  { title: string; confirm: string; success: string; danger?: boolean }
> = {
  submit: { title: '提交审核', confirm: '确认提交', success: '提交成功，单据已进入待审核' },
  audit: { title: '审核通过', confirm: '审核通过', success: '审核通过，可执行过账' },
  post: {
    title: '盘点过账',
    confirm: '确认过账',
    success: '过账成功，库存已按差异调整',
    danger: true,
  },
  cancel: { title: '取消单据', confirm: '确认取消', success: '单据已取消', danger: true },
  withdraw: { title: '撤回至草稿', confirm: '确认撤回', success: '已撤回至草稿，可继续录入' },
}

async function runAction(type: ActionType, row: StocktakeOrder) {
  const no = row.stocktakeNo
  try {
    if (type === 'reject') {
      const { value } = await ElMessageBox.prompt(
        '驳回后单据退回录入人修改，可撤回至草稿重新录入提交。请填写驳回原因（必填）。',
        `驳回盘点单 ${no}`,
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
      await rejectStocktake(row.id, { reason: value.trim(), version: row.version })
      ElMessage.success('已驳回')
    } else {
      const meta = ACTION_META[type]
      const message =
        type === 'post'
          ? `过账将按明细差异调整库存（盘盈增加、盘亏减少）并写入库存流水，单据转为已完成且不可再修改。请逐字输入盘点单号 ${no} 确认。`
          : type === 'submit'
            ? `提交要求全部明细已录入实盘数。提交后盘点单 ${no} 将进入待审核。确认提交？`
            : type === 'audit'
              ? `确认审核通过盘点单 ${no}？审核后可执行过账。`
              : type === 'cancel'
                ? `确认取消盘点单 ${no}？取消后单据进入已取消状态，不可恢复。`
                : `确认将盘点单 ${no} 撤回至草稿？撤回后可继续录入实盘数。`
      const ok = await confirmDialog({
        title: meta.title,
        message,
        confirmText: meta.confirm,
        type: meta.danger ? 'danger' : 'warning',
        ...(type === 'post' ? { requireInput: { text: no, placeholder: `请输入盘点单号 ${no}` } } : {}),
      })
      if (!ok) return
      const body = { version: row.version }
      if (type === 'submit') await submitStocktake(row.id, body)
      else if (type === 'audit') await auditStocktake(row.id, body)
      else if (type === 'post') await postStocktake(row.id, body)
      else if (type === 'cancel') await cancelStocktake(row.id, body)
      else await withdrawStocktake(row.id, body)
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
async function onDelete(row: StocktakeOrder) {
  const ok = await confirmDialog({
    title: '删除盘点单',
    message: `确定删除盘点单 ${row.stocktakeNo} 吗？仅草稿可删除，删除后不可恢复。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteStocktake(row.id, row.version)
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

function onDetailAction(type: StocktakeAction) {
  const current = detail.value
  if (!current) return
  if (type === 'delete') {
    void onDelete(current)
    return
  }
  void runAction(type, current)
}

/* ---------------- 表格 ---------------- */
const columns: TableColumn[] = [
  { prop: 'stocktakeNo', label: '盘点单号', width: 158 },
  { prop: 'status', label: '状态', width: 96, align: 'center' },
  { prop: 'warehouseName', label: '盘点仓库', width: 124, showOverflowTooltip: true },
  { prop: 'areaName', label: '库区', width: 116, showOverflowTooltip: true },
  { prop: 'scope', label: '盘点范围', width: 96, align: 'center' },
  { prop: 'diffStatus', label: '差异状态', width: 96, align: 'center' },
  { prop: 'totalDifferQty', label: '总差异数', width: 104, align: 'right' },
  { prop: 'planDate', label: '计划日期', width: 112 },
  { prop: 'remark', label: '备注', minWidth: 130, showOverflowTooltip: true },
  { prop: 'createTime', label: '创建时间', width: 164 },
  { prop: 'actions', label: '操作', width: 240, fixed: 'right', align: 'center' },
]

const diffOptions = [
  { label: '未处理', value: '未处理' },
  { label: '已处理', value: '已处理' },
]

onMounted(loadList)
</script>

<template>
  <div class="page">
    <PageHeader title="库存盘点" desc="按仓库/库区创建盘点：系统生成账面明细 → 录入实盘 → 差异审核 → 过账调整库存">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadList">刷新</el-button>
        <el-button v-if="hasPerm('sys:stocktake:add')" type="primary" :icon="Plus" @click="openCreate">
          新建盘点单
        </el-button>
      </template>
    </PageHeader>

    <!-- 状态分段 + 条件筛选 -->
    <section class="panel st-ctl">
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

      <div class="st-filters">
        <div class="fitem">
          <span class="fitem__label">盘点单号</span>
          <el-input
            v-model="filters.stocktakeNo"
            placeholder="模糊匹配"
            clearable
            style="width: 168px"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="fitem">
          <span class="fitem__label">仓库</span>
          <WarehouseSelect v-model="filters.warehouseId" placeholder="全部" style="width: 156px" />
        </div>
        <div class="fitem">
          <span class="fitem__label">关键字</span>
          <el-input
            v-model="filters.keyword"
            placeholder="单号 / 仓库名"
            clearable
            style="width: 160px"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="fitem">
          <span class="fitem__label">差异状态</span>
          <el-select
            v-model="filters.diffStatus"
            placeholder="全部"
            clearable
            style="width: 124px"
          >
            <el-option v-for="o in diffOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
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
        <div class="st-filters__actions">
          <el-button type="primary" :icon="Search" :loading="loading" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
      </div>
    </section>

    <!-- 盘点单列表 -->
    <AppTable
      :columns="columns"
      :data="(list as unknown as Record<string, unknown>[])"
      :loading="loading"
      :serial="true"
      stripe
      :pagination="{ total, pageNum, pageSize }"
      :empty-text="statusSeg !== undefined || filters.stocktakeNo || filters.keyword || filters.warehouseId || filters.diffStatus || dateRange.length ? '未查询到符合条件的盘点单' : '暂无盘点单，点击右上角新建'"
      @page-change="onPageChange"
    >
      <template #col-stocktakeNo="{ row }">
        <span class="num">{{ (row as StocktakeOrder).stocktakeNo }}</span>
      </template>
      <template #col-status="{ row }">
        <StatusTag :status="(row as StocktakeOrder).status" />
      </template>
      <template #col-areaName="{ row }">
        {{ (row as StocktakeOrder).areaName ?? '—' }}
      </template>
      <template #col-diffStatus="{ row }">
        <el-tag
          size="small"
          effect="light"
          :type="(row as StocktakeOrder).diffStatus === '已处理' ? 'success' : 'warning'"
        >
          {{ (row as StocktakeOrder).diffStatus ?? '未处理' }}
        </el-tag>
      </template>
      <template #col-totalDifferQty="{ row }">
        <span
          class="num"
          :class="Number((row as StocktakeOrder).totalDifferQty) ? 't-danger' : 't-3'"
        >
          {{ formatQty((row as StocktakeOrder).totalDifferQty) }}
        </span>
      </template>
      <template #col-actions="{ row }">
        <el-button
          v-if="hasPerm('sys:stocktake:view')"
          link
          type="primary"
          @click.stop="openDetail(row as StocktakeOrder)"
        >
          详情
        </el-button>
        <template v-if="(row as StocktakeOrder).status === 0">
          <el-button
            v-if="hasPerm('sys:stocktake:submit')"
            link
            type="primary"
            @click.stop="runAction('submit', row as StocktakeOrder)"
          >
            提交
          </el-button>
          <el-button
            v-if="hasPerm('sys:stocktake:delete')"
            link
            type="danger"
            @click.stop="onDelete(row as StocktakeOrder)"
          >
            删除
          </el-button>
        </template>
        <template v-else-if="(row as StocktakeOrder).status === 1">
          <el-button
            v-if="hasPerm('sys:stocktake:audit')"
            link
            type="primary"
            @click.stop="runAction('audit', row as StocktakeOrder)"
          >
            审核
          </el-button>
          <el-button
            v-if="hasPerm('sys:stocktake:reject')"
            link
            type="danger"
            @click.stop="runAction('reject', row as StocktakeOrder)"
          >
            驳回
          </el-button>
        </template>
        <template v-else-if="(row as StocktakeOrder).status === 2">
          <el-button
            v-if="hasPerm('sys:stocktake:post')"
            link
            type="primary"
            @click.stop="runAction('post', row as StocktakeOrder)"
          >
            过账
          </el-button>
        </template>
        <template v-else-if="(row as StocktakeOrder).status === 4">
          <el-button
            v-if="hasPerm('sys:stocktake:withdraw')"
            link
            type="primary"
            @click.stop="runAction('withdraw', row as StocktakeOrder)"
          >
            撤回
          </el-button>
        </template>
      </template>
    </AppTable>

    <!-- 新建 -->
    <OrderFormDrawer v-model:visible="formVisible" @saved="loadList" />

    <!-- 详情 / 实盘录入 -->
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      :detail="detail"
      :loading="detailLoading"
      @action="onDetailAction"
      @save-input="onSaveInput"
    />
  </div>
</template>

<style scoped lang="scss">
.st-ctl {
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

.st-filters {
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
  .st-filters__actions {
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
