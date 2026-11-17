<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { LocationSelect, ProductSelect, WarehouseSelect, confirmDialog, ItemEditTable } from '@/components'
import type { TableColumn } from '@/components'
import { createOutbound, getUserOptions, submitOutbound, updateOutbound } from '@/api/outbound'
import type { UserOption } from '@/api/outbound'
import { getCustomerOptions } from '@/api/customer'
import { getProductDetail } from '@/api/product'
import type { OutboundDetail, OutboundForm } from '@/types/entities/order'
import { OUTBOUND_TYPE } from '@/utils/dict'
import { usePermission } from '@/composables/usePermission'

/**
 * 出库单新建/编辑大抽屉：主信息表单 + ItemEditTable 明细 + 合计 + 底部操作。
 * 仅草稿（0）与已驳回（4）单据可进入编辑；批次/货位可空，过账时按 FIFO 分配。
 */
defineOptions({ name: 'OutboundOrderFormDrawer' })

const props = defineProps<{
  visible: boolean
  /** 编辑态数据（null = 新增） */
  order: OutboundDetail | null
}>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const { hasPerm } = usePermission()

/* ---------------- 明细行（含行内联动 UI 态） ---------------- */
type ItemRow = {
  lineNo?: number
  productId?: number | null
  batchNo?: string
  locationId?: number | null
  planQty?: number | null
  unit?: string
  remark?: string
}

function emptyRow(): ItemRow {
  return { planQty: undefined }
}

/** 行号由前端统一维护（lineNo 为 ItemEditTable row-key）。 */
function renumber(rows: ItemRow[]): ItemRow[] {
  return rows.map((r, i) => ({ ...r, lineNo: i + 1 }))
}

/* ---------------- 表单状态 ---------------- */
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  orderType: '销售出库',
  warehouseId: null as number | null,
  customerId: null as number | null,
  sourceOrderNo: '',
  expectDate: '',
  pickUserId: null as number | null,
  remark: '',
})
const items = ref<ItemRow[]>([emptyRow()])

const rules = computed(() => ({
  orderType: [{ required: true, message: '请选择单据类型', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择所属仓库', trigger: 'change' }],
  customerId: [
    {
      required: form.orderType === '销售出库',
      message: '销售出库必须选择客户',
      trigger: 'change',
    },
  ],
}))

const itemRows = computed({
  get: () => items.value as unknown as Record<string, unknown>[],
  set: (v: Record<string, unknown>[]) => {
    items.value = renumber(v as unknown as ItemRow[])
  },
})

const totalPlan = computed(() => items.value.reduce((s, r) => s + (Number(r.planQty) || 0), 0))

const isEdit = computed(() => Boolean(props.order))
const title = computed(() => (props.order ? `编辑出库单 ${props.order.outboundNo}` : '新增出库单'))

const drawerSize = computed(() => {
  if (typeof window === 'undefined') return '960px'
  if (window.innerWidth <= 768) return '100%'
  if (window.innerWidth <= 1280) return '760px'
  return '960px'
})

/* ---------------- 客户 / 拣货人下拉 ---------------- */
type CustomerOpt = { id: number; customerCode: string; customerName: string }
const customers = ref<CustomerOpt[]>([])
const customersLoading = ref(false)

async function loadCustomers() {
  if (customersLoading.value || customers.value.length) return
  customersLoading.value = true
  try {
    customers.value = (await getCustomerOptions()) ?? []
  } catch {
    customers.value = []
  } finally {
    customersLoading.value = false
  }
}

const pickUsers = ref<UserOption[]>([])
const pickUsersLoading = ref(false)

async function loadPickUsers() {
  if (pickUsersLoading.value || pickUsers.value.length) return
  pickUsersLoading.value = true
  try {
    pickUsers.value = (await getUserOptions()) ?? []
  } catch {
    pickUsers.value = []
  } finally {
    pickUsersLoading.value = false
  }
}

function pickUserLabel(u: UserOption): string {
  return u.realName || u.username
}

/* ---------------- 打开/重置 ---------------- */
let snapshot = ''

function serialize(): string {
  return JSON.stringify({ form: { ...form }, items: items.value })
}

function reset() {
  form.orderType = '销售出库'
  form.warehouseId = null
  form.customerId = null
  form.sourceOrderNo = ''
  form.expectDate = ''
  form.pickUserId = null
  form.remark = ''
  items.value = renumber([emptyRow()])
}

function fill(order: OutboundDetail) {
  form.orderType = order.orderType
  form.warehouseId = order.warehouseId
  form.customerId = order.customerId ?? null
  form.sourceOrderNo = order.sourceOrderNo ?? ''
  form.expectDate = order.expectDate ?? ''
  form.pickUserId = order.pickUserId ?? null
  form.remark = order.remark ?? ''
  items.value = renumber(
    (order.items ?? []).map((it) => ({
      productId: it.productId,
      batchNo: it.batchNo ?? '',
      locationId: it.locationId ?? null,
      planQty: it.planQty,
      unit: it.unit ?? '',
      remark: it.remark ?? '',
    })),
  )
}

watch(
  () => props.visible,
  async (v) => {
    if (v) {
      if (props.order) fill(props.order)
      else reset()
      if (form.orderType === '销售出库') loadCustomers()
      loadPickUsers()
      snapshot = serialize()
      await nextTick()
      formRef.value?.clearValidate()
    }
  },
)

async function requestClose() {
  if (serialize() !== snapshot) {
    const ok = await confirmDialog({
      message: '表单内容未保存，关闭将丢失修改，是否关闭？',
      confirmText: '关闭',
      type: 'warning',
    })
    if (!ok) return
  }
  emit('update:visible', false)
}

function onDrawerToggle(v: boolean) {
  if (v) emit('update:visible', true)
  else void requestClose()
}

/* ---------------- 行内联动 ---------------- */
function onWarehouseChange(value: number | null) {
  form.warehouseId = value
  // 已指定货位属于原仓库，切换仓库后清空
  for (const row of items.value) row.locationId = null
}

function onOrderTypeChange() {
  if (form.orderType !== '销售出库') form.customerId = null
  else loadCustomers()
}

/* 商品详情缓存（单位联动） */
const productCache = new Map<number, { unit: string }>()

async function onProductChange(row: ItemRow) {
  const id = row.productId ?? null
  if (!id) {
    row.unit = ''
    return
  }
  if (productCache.has(id)) {
    row.unit = productCache.get(id)!.unit
    return
  }
  try {
    const product = await getProductDetail(id)
    productCache.set(id, { unit: product.unit })
    row.unit = product.unit
  } catch {
    // 详情接口异常不阻断编辑
  }
}

function onPlanQtyChange(row: ItemRow) {
  // 草稿阶段实际出库数量由服务端过账时按 FIFO 计算，此处仅校验
  if (row.planQty != null && row.planQty <= 0) row.planQty = undefined
}

function addRow() {
  items.value = renumber([...items.value, emptyRow()])
}

function onRemoveRow() {
  // ItemEditTable 先经 v-model 移除、再派发 remove；仅剩 1 行时回填空行
  if (items.value.length === 0) {
    items.value = renumber([emptyRow()])
    ElMessage.warning('至少保留 1 行明细')
  }
}

/* ---------------- 保存 ---------------- */
function validateItems(): string {
  if (!items.value.length) return '请至少添加 1 行明细'
  for (let i = 0; i < items.value.length; i++) {
    const row = items.value[i]
    if (!row.productId) return `第 ${i + 1} 行：请选择商品`
    if (!row.planQty || row.planQty <= 0) return `第 ${i + 1} 行：计划数量须大于 0`
  }
  return ''
}

function buildPayload(): OutboundForm {
  return {
    orderType: form.orderType,
    warehouseId: form.warehouseId as number,
    customerId: form.customerId,
    sourceOrderNo: form.sourceOrderNo || undefined,
    expectDate: form.expectDate || undefined,
    pickUserId: form.pickUserId,
    remark: form.remark || undefined,
    items: items.value.map((row) => ({
      productId: row.productId as number,
      batchNo: row.batchNo || undefined,
      locationId: row.locationId ?? undefined,
      planQty: Number(row.planQty),
      unit: row.unit || undefined,
      remark: row.remark || undefined,
    })),
  }
}

async function save(withSubmit: boolean) {
  if (saving.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const itemError = validateItems()
  if (itemError) {
    ElMessage.warning(itemError)
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    let id: number
    if (props.order) {
      id = props.order.id
      await updateOutbound(id, { ...payload, id, version: props.order.version })
    } else {
      id = (await createOutbound(payload)).id
    }

    if (withSubmit && hasPerm('sys:outbound:submit')) {
      await submitOutbound(id)
      ElMessage.success('提交成功，单据已进入待审核')
    } else {
      ElMessage.success('保存成功')
    }
    snapshot = serialize()
    emit('saved')
    emit('update:visible', false)
  } catch {
    // 业务错误已由 request 层统一提示（含提交时 40902 库存不足），保留抽屉供修正后重试
  } finally {
    saving.value = false
  }
}

/* ---------------- 明细列 ---------------- */
const itemColumns: TableColumn[] = [
  { prop: 'productId', label: '商品', minWidth: 240 },
  { prop: 'batchNo', label: '批次号（选填）', width: 160 },
  { prop: 'locationId', label: '指定货位（选填）', width: 200 },
  { prop: 'planQty', label: '计划数量', width: 132, align: 'right' },
  { prop: 'unit', label: '单位', width: 84, align: 'center' },
  { prop: 'remark', label: '行备注', minWidth: 120 },
]
</script>

<template>
  <el-drawer
    :model-value="visible"
    :size="drawerSize"
    :title="title"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    class="order-form-drawer"
    @update:model-value="onDrawerToggle"
  >
    <div v-loading="saving" class="form-body">
      <!-- 主信息 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <div class="form-grid">
          <el-form-item v-if="order" label="出库单号">
            <span class="num">{{ order.outboundNo }}</span>
            <span class="t-xs t-3 form-tip">保存后单号不变</span>
          </el-form-item>
          <el-form-item label="单据类型" prop="orderType">
            <el-select
              v-model="form.orderType"
              placeholder="请选择单据类型"
              style="width: 100%"
              @change="onOrderTypeChange"
            >
              <el-option
                v-for="t in OUTBOUND_TYPE"
                :key="String(t.value)"
                :label="t.label"
                :value="String(t.value)"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="所属仓库" prop="warehouseId">
            <WarehouseSelect
              :model-value="form.warehouseId"
              style="width: 100%"
              @update:model-value="onWarehouseChange"
            />
          </el-form-item>
          <el-form-item v-if="form.orderType === '销售出库'" label="客户" prop="customerId">
            <el-select
              v-model="form.customerId"
              placeholder="选择客户"
              :loading="customersLoading"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="c in customers"
                :key="c.id"
                :label="`${c.customerCode} ${c.customerName}`"
                :value="c.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="拣货人">
            <el-select
              v-model="form.pickUserId"
              placeholder="选择拣货人（选填）"
              :loading="pickUsersLoading"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option v-for="u in pickUsers" :key="u.id" :label="pickUserLabel(u)" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="预计出库">
            <el-date-picker
              v-model="form.expectDate"
              type="date"
              placeholder="预计出库日期"
              value-format="YYYY-MM-DD"
              clearable
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="来源单号">
            <el-input v-model="form.sourceOrderNo" placeholder="如销售订单号，可空" clearable />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="备注（选填）"
            />
          </el-form-item>
        </div>
      </el-form>

      <!-- 单据明细 -->
      <h4 class="block-title">单据明细</h4>
      <ItemEditTable
        v-model="itemRows"
        :columns="itemColumns"
        add-text="添加明细行"
        empty-text="暂无明细，点击下方按钮添加"
        @add="addRow"
        @remove="onRemoveRow"
      >
        <template #col-productId="{ row }">
          <ProductSelect
            :model-value="row.productId ?? null"
            style="width: 100%"
            @update:model-value="row.productId = $event"
            @change="onProductChange(row)"
          />
        </template>
        <template #col-batchNo="{ row }">
          <el-input
            v-model="row.batchNo"
            placeholder="留空过账按 FIFO 分配"
            size="small"
            clearable
          />
        </template>
        <template #col-locationId="{ row }">
          <LocationSelect
            :model-value="row.locationId ?? null"
            :warehouse-id="form.warehouseId"
            :disabled="!form.warehouseId"
            placeholder="留空自动分配"
            size="small"
            style="width: 100%"
            @update:model-value="row.locationId = $event"
          />
        </template>
        <template #col-planQty="{ row }">
          <el-input-number
            v-model="row.planQty"
            :min="0.001"
            :precision="3"
            :step="1"
            controls-position="right"
            placeholder="计划"
            size="small"
            style="width: 116px"
            @change="onPlanQtyChange(row)"
          />
        </template>
        <template #col-unit="{ row }">
          <el-input v-model="row.unit" placeholder="自动" size="small" clearable />
        </template>
        <template #col-remark="{ row }">
          <el-input v-model="row.remark" placeholder="行备注" size="small" clearable />
        </template>

        <template #footer>
          <span>共 {{ items.length }} 行</span>
          <span>计划合计 <b class="num t-success">{{ totalPlan.toFixed(3) }}</b></span>
          <span class="t-xs t-3">实发数量由过账按 FIFO 计算</span>
        </template>
      </ItemEditTable>
    </div>

    <template #footer>
      <el-button @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save(false)">
        {{ isEdit ? '保存修改' : '保存草稿' }}
      </el-button>
      <el-button
        v-if="hasPerm('sys:outbound:submit')"
        :loading="saving"
        plain
        type="primary"
        @click="save(true)"
      >
        保存并提交
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.form-body {
  padding-bottom: var(--sp-4);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--sp-6);

  @media (max-width: 768px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.form-tip {
  margin-left: var(--sp-2);
}

.block-title {
  margin-top: var(--sp-5);
}
</style>
