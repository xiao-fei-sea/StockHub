<script setup lang="ts">
import { computed, onActivated, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  AppTable,
  OrderDetailDrawer,
  PageHeader,
  SearchForm,
  StatusTag,
  WarehouseSelect,
  confirmDialog,
} from '@/components'
import type { DetailField, SearchField, TableColumn } from '@/components'
import {
  auditOutbound,
  cancelOutbound,
  deleteOutbound,
  getOutbound,
  getOutbounds,
  postOutbound,
  rejectOutbound,
  submitOutbound,
  withdrawOutbound,
} from '@/api/outbound'
import type { OutboundQuery } from '@/api/outbound'
import type { OutboundDetail, OutboundOrder } from '@/types/entities/order'
import { OrderStatusMap } from '@/types/enum'
import { usePermission } from '@/composables/usePermission'
import { formatQty } from '@/utils/format'
import OrderFormDrawer from './OrderFormDrawer.vue'

defineOptions({ name: 'OutboundOrder' })

const route = useRoute()
const router = useRouter()
const { hasPerm } = usePermission()

const PERM = 'sys:outbound'

/* ---------------- 状态分段 Tab + 查询条件 ---------------- */
const STATUS_TABS = [
  { value: -1, label: '全部' },
  ...([0, 1, 2, 3, 4, 5] as const).map((s) => ({ value: s as number, label: OrderStatusMap[s] })),
]

const activeTab = ref(-1)

const query = ref<{
  pageNum: number
  pageSize: number
  outboundNo?: string
  warehouseId?: number
  range: string[]
}>({ pageNum: 1, pageSize: 20, outboundNo: '', warehouseId: undefined, range: [] })

const fields: SearchField[] = [
  { key: 'outboundNo', label: '单据号', type: 'input', placeholder: 'CK 单号模糊匹配', width: 200 },
  { key: 'warehouseId', label: '仓库', type: 'select' },
  { key: 'range', label: '创建日期', type: 'daterange' },
]

const hasFilter = computed(
  () =>
    activeTab.value >= 0 ||
    Boolean(query.value.outboundNo) ||
    Boolean(query.value.warehouseId) ||
    query.value.range.length > 0,
)

function onQueryPatch(v: Record<string, unknown>) {
  query.value = v as unknown as typeof query.value
}

function buildParams(): OutboundQuery {
  const q = query.value
  const p: OutboundQuery = { pageNum: q.pageNum, pageSize: q.pageSize }
  if (activeTab.value >= 0) p.status = activeTab.value
  if (q.outboundNo && q.outboundNo.trim()) p.outboundNo = q.outboundNo.trim()
  if (q.warehouseId) p.warehouseId = q.warehouseId
  if (q.range[0]) p.startDate = q.range[0]
  if (q.range[1]) p.endDate = q.range[1]
  return p
}

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<OutboundOrder[]>([])
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const res = await getOutbounds(buildParams())
    list.value = res.list ?? []
    total.value = res.total
  } catch {
    // 请求层已统一提示，保留上次数据
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.value = { ...query.value, pageNum: 1 }
  void loadList()
}

function onTabChange() {
  query.value = { ...query.value, pageNum: 1 }
  void loadList()
}

function onPageChange({ pageNum, pageSize }: { pageNum: number; pageSize: number }) {
  query.value = { ...query.value, pageNum, pageSize }
  void loadList()
}

/* ---------------- 看板下钻（/outbound/order?status=1&startDate=today） ---------------- */
function applyRouteQuery() {
  const q = route.query
  const status = Number(q.status)
  if (q.status !== undefined && Number.isInteger(status) && status >= 0 && status <= 5) {
    activeTab.value = status
  }
  if (typeof q.warehouseId === 'string' && q.warehouseId) {
    query.value.warehouseId = Number(q.warehouseId)
  }
  if (typeof q.startDate === 'string' && q.startDate) {
    query.value.range = [q.startDate]
  }
  if (typeof q.keyword === 'string' && q.keyword) {
    query.value.outboundNo = q.keyword
  }
  query.value.pageNum = 1
  void loadList()
  if (Object.keys(q).length) {
    // 消费一次即清空，避免返回本页时重复带参（结构设计 §5.3）
    void router.replace({ path: route.path, query: {} })
  }
}

applyRouteQuery()

let firstActivate = true
onActivated(() => {
  if (firstActivate) {
    firstActivate = false
    return
  }
  if (Object.keys(route.query).length) applyRouteQuery()
})

/* ---------------- 按钮 × 状态矩阵（交互设计 §3.2）+ 权限 ---------------- */
type Action = 'edit' | 'delete' | 'submit' | 'audit' | 'reject' | 'post' | 'cancel' | 'withdraw'

const ROW_ACTIONS: Action[] = ['edit', 'delete', 'submit', 'audit', 'reject', 'post', 'cancel', 'withdraw']

const ACTION_LABEL: Record<Action, string> = {
  edit: '编辑',
  delete: '删除',
  submit: '提交',
  audit: '审核',
  reject: '驳回',
  post: '过账',
  cancel: '取消',
  withdraw: '撤回',
}

const ACTION_TYPE: Partial<Record<Action, 'primary' | 'success' | 'warning' | 'danger' | 'info'>> = {
  edit: 'primary',
  delete: 'danger',
  submit: 'primary',
  audit: 'success',
  reject: 'danger',
  post: 'warning',
  cancel: 'danger',
}

/** 各动作可见的状态集合（可见 = 权限 ∧ 状态；不满足即隐藏）。 */
const SHOW_STATUS: Record<Action, number[]> = {
  edit: [0, 1, 4],
  delete: [0],
  submit: [0, 4],
  audit: [1],
  reject: [1],
  post: [1, 2],
  cancel: [0, 1, 4],
  withdraw: [1, 4],
}

/** 可见但禁用（disabled + tooltip，仅状态 1 的编辑与过账）。 */
const LOCK_STATUS: Partial<Record<Action, number[]>> = { edit: [1], post: [1] }
const LOCK_TIP: Partial<Record<Action, string>> = {
  edit: '待审核单据已锁定，可撤回后编辑',
  post: '未审核，不可过账',
}

function canAct(action: Action, status: number): boolean {
  if (!hasPerm(`${PERM}:${action}`)) return false
  if (LOCK_STATUS[action]?.includes(status)) return false
  return SHOW_STATUS[action].includes(status)
}

function lockTip(action: Action, status: number): string | null {
  if (!hasPerm(`${PERM}:${action}`)) return null
  if (LOCK_STATUS[action]?.includes(status)) return LOCK_TIP[action] ?? '当前状态不可操作'
  return null
}

/* ---------------- 详情抽屉 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OutboundDetail | null>(null)

async function reloadDetail(id: number) {
  detailLoading.value = true
  try {
    detail.value = await getOutbound(id)
  } catch {
    // 40401 单据不存在等：请求层已提示，关闭抽屉
    detailVisible.value = false
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

function openDetail(row: OutboundOrder) {
  detailVisible.value = true
  detail.value = null
  void reloadDetail(row.id)
}

const detailFields = computed<DetailField[]>(() => {
  const d = detail.value
  if (!d) return []
  const fieldsList: DetailField[] = [
    { label: '出库单号', value: d.outboundNo },
    { label: '单据类型', value: d.orderType },
    { label: '所属仓库', value: d.warehouseName },
    { label: '客户', value: d.customerName ?? '—' },
    { label: '来源单号', value: d.sourceOrderNo ?? '—' },
    { label: '预计出库日期', value: d.expectDate ?? '—' },
    { label: '计划总数量', value: formatQty(d.totalQty) },
    { label: '实际出库数量', value: formatQty(d.actualQty) },
    { label: '拣货人', value: d.pickUserName ?? (d.pickUserId != null ? String(d.pickUserId) : '—') },
    { label: '创建人 / 时间', value: `${d.createBy ?? '—'} / ${d.createTime ?? '—'}` },
    { label: '审核人', value: d.auditorName ?? (d.auditorId != null ? String(d.auditorId) : '—') },
    { label: '审核时间', value: d.auditTime ?? '—' },
    { label: '过账人', value: d.posterName ?? (d.posterId != null ? String(d.posterId) : '—') },
    { label: '过账时间', value: d.postTime ?? '—' },
    { label: '备注', value: d.remark ?? '—' },
  ]
  if (d.status === 4) fieldsList.push({ label: '驳回原因', value: d.rejectReason ?? '—' })
  return fieldsList
})

const detailItemColumns: TableColumn[] = [
  { prop: 'lineNo', label: '#', width: 50, align: 'center' },
  { prop: 'productCode', label: '商品编码', width: 116, showOverflowTooltip: true },
  { prop: 'productName', label: '商品名称', minWidth: 170, showOverflowTooltip: true },
  { prop: 'batchNo', label: '批次号', width: 120, showOverflowTooltip: true },
  { prop: 'locationCode', label: '出库货位', width: 150, showOverflowTooltip: true },
  { prop: 'planQty', label: '计划数量', width: 96, align: 'right', formatter: (_, _c, v) => formatQty(v as number) },
  { prop: 'actualQty', label: '实发数量', width: 96, align: 'right', formatter: (_, _c, v) => formatQty(v as number) },
  { prop: 'unit', label: '单位', width: 64, align: 'center' },
  { prop: 'remark', label: '行备注', minWidth: 100, showOverflowTooltip: true },
]

/* ---------------- 状态动作 ---------------- */
const acting = ref(false)

/** 审核备注（选填）/ 驳回原因（必填）输入弹窗，取消返回 null。 */
function promptTextarea(title: string, placeholder: string, required: boolean): Promise<string | null> {
  return ElMessageBox.prompt(placeholder, title, {
    inputType: 'textarea',
    inputPlaceholder: placeholder,
    confirmButtonText: required ? '确认' : '确定',
    cancelButtonText: '取消',
    inputValidator: (value: string) => {
      const v = value.trim()
      if (required && !v) return '请填写驳回原因（1-200 字）'
      if (v.length > 200) return '最多填写 200 字'
      return true
    },
  })
    .then((res) => res.value.trim())
    .catch(() => null)
}

function refreshAfterAction(row: OutboundOrder) {
  void loadList()
  if (detailVisible.value && detail.value?.id === row.id) void reloadDetail(row.id)
}

async function onAction(row: OutboundOrder, action: Action) {
  if (acting.value || !canAct(action, row.status)) return
  const no = row.outboundNo
  try {
    switch (action) {
      case 'edit': {
        void openEdit(row)
        return
      }
      case 'delete': {
        const ok = await confirmDialog({
          message: `删除【${no}】后不可恢复，是否继续？`,
          confirmText: '删除',
          type: 'danger',
        })
        if (!ok) return
        acting.value = true
        await deleteOutbound(row.id, row.version)
        ElMessage.success('删除成功')
        break
      }
      case 'submit': {
        const ok = await confirmDialog({
          message: '提交后单据将进入待审核且不可编辑（提交时校验可用库存），是否提交？',
          confirmText: '提交',
        })
        if (!ok) return
        acting.value = true
        await submitOutbound(row.id, { version: row.version })
        ElMessage.success('提交成功')
        break
      }
      case 'audit': {
        const remark = await promptTextarea('审核通过', '审核备注（选填，最多 200 字）', false)
        if (remark === null) return
        acting.value = true
        await auditOutbound(row.id, { remark: remark || undefined, version: row.version })
        ElMessage.success('审核通过')
        break
      }
      case 'reject': {
        const reason = await promptTextarea('驳回单据', '请填写驳回原因（必填，1-200 字）', true)
        if (reason === null) return
        acting.value = true
        await rejectOutbound(row.id, { reason, version: row.version })
        ElMessage.success('已驳回')
        break
      }
      case 'post': {
        const ok = await confirmDialog({
          title: '过账确认',
          message: `过账将按批次 FIFO 扣减库存并生成出库流水，单据进入已完成且不可再修改。\n单据【${no}】计划总数量 ${formatQty(row.totalQty)}，是否确认过账？\n（库存不足时将提示，单据自动回退为已审核）`,
          confirmText: '确认过账',
          type: 'danger',
        })
        if (!ok) return
        acting.value = true
        await postOutbound(row.id, { version: row.version })
        ElMessage.success('过账成功，库存已扣减')
        break
      }
      case 'cancel': {
        const ok = await confirmDialog({
          message: '取消后单据进入已取消状态且不可恢复，是否继续？',
          confirmText: '取消单据',
          type: 'danger',
        })
        if (!ok) return
        acting.value = true
        await cancelOutbound(row.id, { version: row.version })
        ElMessage.success('已取消')
        break
      }
      case 'withdraw': {
        const ok = await confirmDialog({
          message: '撤回后单据将回到草稿状态，可重新编辑提交，是否撤回？',
          confirmText: '撤回',
        })
        if (!ok) return
        acting.value = true
        await withdrawOutbound(row.id, { version: row.version })
        ElMessage.success('已撤回至草稿')
        break
      }
    }
    refreshAfterAction(row)
  } catch {
    // 40901 状态机错误、40902 库存不足均由请求层统一弹错；刷新列表纠正状态滞后
    refreshAfterAction(row)
  } finally {
    acting.value = false
  }
}

/* ---------------- 新增 / 编辑抽屉 ---------------- */
const formVisible = ref(false)
const editing = ref<OutboundDetail | null>(null)

function openCreate() {
  if (!hasPerm(`${PERM}:add`)) return
  editing.value = null
  formVisible.value = true
}

async function openEdit(row: OutboundOrder) {
  if (!hasPerm(`${PERM}:edit`)) return
  try {
    editing.value = await getOutbound(row.id)
    formVisible.value = true
  } catch {
    // 请求层已提示
  }
}

function onFormSaved() {
  void loadList()
  if (detailVisible.value && detail.value) void reloadDetail(detail.value.id)
}

/* ---------------- 表格 ---------------- */
const columns: TableColumn[] = [
  { prop: 'outboundNo', label: '出库单号', width: 158 },
  { prop: 'orderType', label: '单据类型', width: 104 },
  { prop: 'warehouseName', label: '仓库', width: 118, showOverflowTooltip: true },
  { prop: 'customerName', label: '客户', minWidth: 150, showOverflowTooltip: true },
  { prop: 'pickUserName', label: '拣货人', width: 96, showOverflowTooltip: true },
  { prop: 'sourceOrderNo', label: '来源单号', width: 138, showOverflowTooltip: true },
  { prop: 'expectDate', label: '预计出库', width: 112 },
  { prop: 'totalQty', label: '计划数量', width: 100, align: 'right' },
  { prop: 'actualQty', label: '实发数量', width: 100, align: 'right' },
  { prop: 'status', label: '状态', width: 96, align: 'center' },
  { prop: 'createTime', label: '创建时间', width: 164 },
  { prop: 'actions', label: '操作', width: 320, fixed: 'right', align: 'center' },
]
</script>

<template>
  <div class="page">
    <PageHeader title="出库单" desc="销售/调拨/退货/报损出库的全生命周期管理，过账后库存按 FIFO 扣减">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadList">刷新</el-button>
        <el-button v-permission="`${PERM}:add`" type="primary" :icon="Plus" @click="openCreate">
          新增出库单
        </el-button>
      </template>
    </PageHeader>

    <!-- 状态分段 Tab -->
    <div class="status-tabs">
      <el-radio-group v-model="activeTab" size="default" @change="onTabChange">
        <el-radio-button v-for="t in STATUS_TABS" :key="t.value" :value="t.value">
          {{ t.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 筛选 -->
    <SearchForm
      :fields="fields"
      :model-value="query"
      :loading="loading"
      @update:model-value="onQueryPatch"
      @search="onSearch"
    >
      <template #field-warehouseId>
        <WarehouseSelect
          :model-value="query.warehouseId ?? null"
          style="width: 180px"
          @update:model-value="query.warehouseId = $event ?? undefined"
        />
      </template>
    </SearchForm>

    <!-- 列表 -->
    <AppTable
      :columns="columns"
      :data="(list as unknown as Record<string, unknown>[])"
      :loading="loading"
      :serial="true"
      stripe
      :pagination="{ total, pageNum: query.pageNum, pageSize: query.pageSize }"
      :empty-text="hasFilter ? '未查询到符合条件的数据' : '暂无出库单'"
      @page-change="onPageChange"
    >
      <template #col-outboundNo="{ row }">
        <el-button link type="primary" class="num" @click.stop="openDetail(row as OutboundOrder)">
          {{ (row as OutboundOrder).outboundNo }}
        </el-button>
      </template>
      <template #col-totalQty="{ row }">
        <span class="num">{{ formatQty((row as OutboundOrder).totalQty) }}</span>
      </template>
      <template #col-actualQty="{ row }">
        <span class="num t-3">{{ formatQty((row as OutboundOrder).actualQty) }}</span>
      </template>
      <template #col-status="{ row }">
        <StatusTag :status="(row as OutboundOrder).status" />
      </template>
      <template #col-actions="{ row }">
        <div class="row-actions">
          <el-button
            link
            type="primary"
            size="small"
            v-permission="`${PERM}:view`"
            @click.stop="openDetail(row as OutboundOrder)"
          >
            详情
          </el-button>
          <template v-for="a in ROW_ACTIONS" :key="a">
            <el-tooltip
              v-if="lockTip(a, (row as OutboundOrder).status)"
              :content="lockTip(a, (row as OutboundOrder).status) ?? ''"
              placement="top"
            >
              <span class="tip-wrap">
                <el-button size="small" disabled>{{ ACTION_LABEL[a] }}</el-button>
              </span>
            </el-tooltip>
            <el-button
              v-else-if="canAct(a, (row as OutboundOrder).status)"
              link
              size="small"
              :type="ACTION_TYPE[a]"
              @click.stop="onAction(row as OutboundOrder, a)"
            >
              {{ ACTION_LABEL[a] }}
            </el-button>
          </template>
        </div>
      </template>
    </AppTable>

    <!-- 详情抽屉（footer 按状态矩阵放操作按钮） -->
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      title="出库单详情"
      :status="detail?.status ?? null"
      :fields="detailFields"
      :items="(detail?.items ?? []) as unknown as Record<string, unknown>[]"
      :item-columns="detailItemColumns"
      :loading="detailLoading"
    >
      <template #footer>
        <template v-for="a in ROW_ACTIONS" :key="a">
          <el-tooltip
            v-if="detail && lockTip(a, detail.status)"
            :content="lockTip(a, detail.status) ?? ''"
            placement="top"
          >
            <span class="tip-wrap">
              <el-button disabled>{{ ACTION_LABEL[a] }}</el-button>
            </span>
          </el-tooltip>
          <el-button
            v-else-if="detail && canAct(a, detail.status)"
            :type="ACTION_TYPE[a]"
            :disabled="acting"
            @click="onAction(detail, a)"
          >
            {{ ACTION_LABEL[a] }}
          </el-button>
        </template>
      </template>
    </OrderDetailDrawer>

    <!-- 新建 / 编辑大抽屉 -->
    <OrderFormDrawer v-model:visible="formVisible" :order="editing" @saved="onFormSaved" />
  </div>
</template>

<style scoped lang="scss">
.status-tabs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background-color: var(--c-bg-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);

  :deep(.el-radio-group) {
    flex-wrap: wrap;
    row-gap: var(--sp-2);
  }
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 2px 6px;
}

.tip-wrap {
  display: inline-flex;
  align-items: center;
}

@media (max-width: 1280px) {
  .row-actions {
    justify-content: flex-start;
  }
}
</style>
