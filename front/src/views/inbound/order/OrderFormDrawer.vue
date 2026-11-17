<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  LocationSelect,
  ProductSelect,
  WarehouseSelect,
  confirmDialog,
  ItemEditTable,
} from '@/components'
import type { TableColumn } from '@/components'
import { createInbound, submitInbound, updateInbound } from '@/api/inbound'
import { getSupplierOptions } from '@/api/supplier'
import { getProductDetail } from '@/api/product'
import type { InboundDetail, InboundForm } from '@/types/entities/order'
import type { Product } from '@/types/entity'
import { INBOUND_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'

/**
 * 入库单新建/编辑大抽屉：主信息表单 + ItemEditTable 明细 + 合计 + 底部操作。
 * 仅草稿（0）与已驳回（4）单据可进入编辑；已驳回单保存后服务端重置为草稿。
 */
defineOptions({ name: 'InboundOrderFormDrawer' })

const props = defineProps<{
  visible: boolean
  /** 编辑态数据（null = 新增） */
  order: InboundDetail | null
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
  locationId?: number | null
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  planQty?: number | null
  actualQty?: number | null
  unit?: string
  remark?: string
  /** 商品 batchFlag：0 时批次号置灰，服务端生成 DEFAULT */
  batchFlag?: number
  /** 商品 shelfLifeFlag：0 时生产/失效日期置灰 */
  shelfLifeFlag?: number
  /** 用户手动改过实收数量后不再跟随计划数量 */
  actualEdited?: boolean
}

function emptyRow(): ItemRow {
  return { planQty: undefined, actualQty: undefined }
}

/** 行号由前端统一维护（lineNo 为 ItemEditTable row-key）。 */
function renumber(rows: ItemRow[]): ItemRow[] {
  return rows.map((r, i) => ({ ...r, lineNo: i + 1 }))
}

/* ---------------- 表单状态 ---------------- */
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  orderType: '采购入库',
  warehouseId: null as number | null,
  supplierId: null as number | null,
  sourceOrderNo: '',
  expectDate: '',
  remark: '',
})
const items = ref<ItemRow[]>([emptyRow()])

const rules = computed(() => ({
  orderType: [{ required: true, message: '请选择单据类型', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择所属仓库', trigger: 'change' }],
  supplierId: [
    {
      required: form.orderType === '采购入库',
      message: '采购入库必须选择供应商',
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
const totalActual = computed(() => items.value.reduce((s, r) => s + (Number(r.actualQty) || 0), 0))

const isEdit = computed(() => Boolean(props.order))
const title = computed(() => (props.order ? `编辑入库单 ${props.order.inboundNo}` : '新增入库单'))

const drawerSize = computed(() => {
  if (typeof window === 'undefined') return '960px'
  if (window.innerWidth <= 768) return '100%'
  if (window.innerWidth <= 1280) return '760px'
  return '960px'
})

/* ---------------- 供应商下拉 ---------------- */
type SupplierOpt = { id: number; supplierCode: string; supplierName: string }
const suppliers = ref<SupplierOpt[]>([])
const suppliersLoading = ref(false)

async function loadSuppliers() {
  if (suppliersLoading.value || suppliers.value.length) return
  suppliersLoading.value = true
  try {
    suppliers.value = (await getSupplierOptions()) ?? []
  } catch {
    suppliers.value = []
  } finally {
    suppliersLoading.value = false
  }
}

/* ---------------- 打开/重置 ---------------- */
let snapshot = ''

function serialize(): string {
  return JSON.stringify({ form: { ...form }, items: items.value })
}

function reset() {
  form.orderType = '采购入库'
  form.warehouseId = null
  form.supplierId = null
  form.sourceOrderNo = ''
  form.expectDate = ''
  form.remark = ''
  items.value = renumber([emptyRow()])
}

function fill(order: InboundDetail) {
  form.orderType = order.orderType
  form.warehouseId = order.warehouseId
  form.supplierId = order.supplierId ?? null
  form.sourceOrderNo = order.sourceOrderNo ?? ''
  form.expectDate = order.expectDate ?? ''
  form.remark = order.remark ?? ''
  items.value = renumber(
    (order.items ?? []).map((it) => ({
      productId: it.productId,
      locationId: it.locationId,
      batchNo: it.batchNo ?? '',
      productionDate: it.productionDate ?? '',
      expiryDate: it.expiryDate ?? '',
      planQty: it.planQty,
      actualQty: it.actualQty ?? it.planQty,
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
      if (form.orderType === '采购入库') loadSuppliers()
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
  // 货位属于具体仓库，切换仓库后清空已选货位
  for (const row of items.value) row.locationId = null
}

function onOrderTypeChange() {
  if (form.orderType !== '采购入库') form.supplierId = null
  else loadSuppliers()
}

/* 商品详情缓存（单位/批次/保质期联动 + 保质期天数自动算失效日期） */
const productCache = new Map<number, Product>()

async function getProduct(id: number): Promise<Product | null> {
  const cached = productCache.get(id)
  if (cached) return cached
  try {
    const p = await getProductDetail(id)
    productCache.set(id, p)
    return p
  } catch {
    return null
  }
}

async function onProductChange(row: ItemRow) {
  const id = row.productId ?? null
  if (!id) {
    row.unit = ''
    row.batchFlag = undefined
    row.shelfLifeFlag = undefined
    row.batchNo = ''
    row.productionDate = ''
    row.expiryDate = ''
    return
  }
  const product = await getProduct(id)
  if (!product) return
  row.unit = product.unit
  row.batchFlag = product.batchFlag
  row.shelfLifeFlag = product.shelfLifeFlag
  if (product.batchFlag === 0) row.batchNo = 'DEFAULT'
  if (product.shelfLifeFlag === 0) {
    row.productionDate = ''
    row.expiryDate = ''
  }
}

function onPlanQtyChange(row: ItemRow) {
  if (!row.actualEdited) row.actualQty = row.planQty ?? undefined
}

function onActualQtyChange(row: ItemRow) {
  row.actualEdited = true
}

/* 保质期商品：选择生产日期后自动计算失效日期（可手动覆盖） */
function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr.replace(/-/g, '/'))
  d.setDate(d.getDate() + days)
  return formatDate(d)
}

async function onProductionDateChange(row: ItemRow) {
  if (row.shelfLifeFlag !== 1 || !row.productionDate || !row.productId) return
  const product = await getProduct(row.productId)
  const days = product?.shelfLifeDays ?? 0
  if (!days) return
  row.expiryDate = addDays(row.productionDate, days)
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
    if (!row.locationId) return `第 ${i + 1} 行：请选择目标货位`
    if (!row.planQty || row.planQty <= 0) return `第 ${i + 1} 行：计划数量须大于 0`
    if (row.actualQty != null && row.actualQty <= 0) return `第 ${i + 1} 行：实收数量须大于 0`
  }
  return ''
}

function buildPayload(): InboundForm {
  return {
    orderType: form.orderType,
    warehouseId: form.warehouseId as number,
    supplierId: form.supplierId,
    sourceOrderNo: form.sourceOrderNo || undefined,
    expectDate: form.expectDate || undefined,
    remark: form.remark || undefined,
    items: items.value.map((row) => ({
      productId: row.productId as number,
      locationId: row.locationId as number,
      batchNo: row.batchFlag === 0 ? undefined : row.batchNo || undefined,
      productionDate: row.productionDate || undefined,
      expiryDate: row.expiryDate || undefined,
      planQty: Number(row.planQty),
      actualQty: row.actualQty != null ? Number(row.actualQty) : undefined,
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
      await updateInbound(id, { ...payload, id, version: props.order.version })
    } else {
      id = (await createInbound(payload)).id
    }

    if (withSubmit && hasPerm('sys:inbound:submit')) {
      await submitInbound(id)
      ElMessage.success('提交成功，单据已进入待审核')
    } else {
      ElMessage.success('保存成功')
    }
    snapshot = serialize()
    emit('saved')
    emit('update:visible', false)
  } catch {
    // 业务错误已由 request 层统一提示，保留抽屉内容供修正后重试
  } finally {
    saving.value = false
  }
}

/* ---------------- 明细列 ---------------- */
const itemColumns: TableColumn[] = [
  { prop: 'productId', label: '商品', minWidth: 220 },
  { prop: 'locationId', label: '目标货位', width: 200 },
  { prop: 'batchNo', label: '批次号', width: 132 },
  { prop: 'productionDate', label: '生产日期', width: 140 },
  { prop: 'expiryDate', label: '失效日期', width: 140 },
  { prop: 'planQty', label: '计划数量', width: 132, align: 'right' },
  { prop: 'actualQty', label: '实收数量', width: 132, align: 'right' },
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
          <el-form-item v-if="order" label="入库单号">
            <span class="num">{{ order.inboundNo }}</span>
            <span class="t-xs t-3 form-tip">保存后单号不变</span>
          </el-form-item>
          <el-form-item label="单据类型" prop="orderType">
            <el-select
              v-model="form.orderType"
              placeholder="请选择单据类型"
              style="width: 100%"
              @change="onOrderTypeChange"
            >
              <el-option v-for="t in INBOUND_TYPE" :key="String(t.value)" :label="t.label" :value="String(t.value)" />
            </el-select>
          </el-form-item>

          <el-form-item label="所属仓库" prop="warehouseId">
            <WarehouseSelect
              :model-value="form.warehouseId"
              style="width: 100%"
              @update:model-value="onWarehouseChange"
            />
          </el-form-item>
          <el-form-item v-if="form.orderType === '采购入库'" label="供应商" prop="supplierId">
            <el-select
              v-model="form.supplierId"
              placeholder="选择供应商"
              :loading="suppliersLoading"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="s in suppliers"
                :key="s.id"
                :label="`${s.supplierCode} ${s.supplierName}`"
                :value="s.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="来源单号">
            <el-input v-model="form.sourceOrderNo" placeholder="如采购订单号，可空" clearable />
          </el-form-item>
          <el-form-item label="预计到货">
            <el-date-picker
              v-model="form.expectDate"
              type="date"
              placeholder="预计到货日期"
              value-format="YYYY-MM-DD"
              clearable
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="备注" class="form-grid__full">
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
        <template #col-locationId="{ row }">
          <LocationSelect
            :model-value="row.locationId ?? null"
            :warehouse-id="form.warehouseId"
            :disabled="!form.warehouseId"
            placeholder="选择货位"
            size="small"
            style="width: 100%"
            @update:model-value="row.locationId = $event"
          />
        </template>
        <template #col-batchNo="{ row }">
          <el-input
            v-model="row.batchNo"
            :disabled="!row.productId || row.batchFlag === 0"
            :placeholder="row.batchFlag === 0 ? '系统生成 DEFAULT' : '可空'"
            size="small"
            clearable
          />
        </template>
        <template #col-productionDate="{ row }">
          <el-date-picker
            v-model="row.productionDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="!row.productId || row.shelfLifeFlag === 0"
            placeholder="选日期"
            clearable
            size="small"
            style="width: 128px"
            @change="onProductionDateChange(row)"
          />
        </template>
        <template #col-expiryDate="{ row }">
          <el-date-picker
            v-model="row.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="!row.productId || row.shelfLifeFlag === 0"
            placeholder="选日期"
            clearable
            size="small"
            style="width: 128px"
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
        <template #col-actualQty="{ row }">
          <el-input-number
            v-model="row.actualQty"
            :min="0.001"
            :precision="3"
            :step="1"
            controls-position="right"
            placeholder="实收"
            size="small"
            style="width: 116px"
            @change="onActualQtyChange(row)"
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
          <span>实收合计 <b class="num">{{ totalActual.toFixed(3) }}</b></span>
        </template>
      </ItemEditTable>
    </div>

    <template #footer>
      <el-button @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save(false)">
        {{ isEdit ? '保存修改' : '保存草稿' }}
      </el-button>
      <el-button
        v-if="hasPerm('sys:inbound:submit')"
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

  &__full {
    grid-column: 1 / -1;
  }

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
