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
import { createTransfer, getTransfer, submitTransfer, updateTransfer } from '@/api/transfer'
import { getProductDetail } from '@/api/product'
import type { Product } from '@/types/entity'
import type { TransferDetail, TransferForm } from '@/types/entities/inventory'
import { usePermission } from '@/composables/usePermission'

/**
 * 调拨单新建/编辑大抽屉：源仓/目标仓（不可相同）+ ItemEditTable 明细 + 底部保存/提交。
 * 仅草稿（0）与已驳回（4）可编辑；已驳回单保存后由服务端重置为草稿。
 * 批次由服务端在过账时按库存分配（接口文档 13 未提供批次下拉数据源），表单不录批次。
 */
defineOptions({ name: 'InventoryTransferFormDrawer' })

const props = defineProps<{
  visible: boolean
  /** 编辑态数据（null = 新增）；传入即为已加载的完整详情 */
  order: TransferDetail | null
}>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const { hasPerm } = usePermission()

/* ---------------- 明细行 ---------------- */
type ItemRow = {
  batchId?: number | null
  productId?: number | null
  srcLocationId?: number | null
  destLocationId?: number | null
  planQty?: number | null
  unit?: string
  remark?: string
}

function emptyRow(): ItemRow {
  return { planQty: undefined }
}

/* ---------------- 表单状态 ---------------- */
const formRef = ref<FormInstance>()
const saving = ref(false)
const loading = ref(false)

const form = reactive({
  srcWarehouseId: null as number | null,
  destWarehouseId: null as number | null,
  remark: '',
})
const items = ref<ItemRow[]>([emptyRow()])

const rules = computed(() => ({
  srcWarehouseId: [{ required: true, message: '请选择源仓库', trigger: 'change' }],
  destWarehouseId: [
    { required: true, message: '请选择目标仓库', trigger: 'change' },
    {
      validator: (_rule: unknown, value: unknown, cb: (e?: Error) => void) => {
        if (value && value === form.srcWarehouseId) cb(new Error('目标仓库不能与源仓库相同'))
        else cb()
      },
      trigger: 'change',
    },
  ],
}))

const itemRows = computed({
  get: () => items.value as unknown as Record<string, unknown>[],
  set: (v: Record<string, unknown>[]) => {
    items.value = v as unknown as ItemRow[]
  },
})

const totalPlan = computed(() => items.value.reduce((s, r) => s + (Number(r.planQty) || 0), 0))

const isEdit = computed(() => Boolean(props.order))
const title = computed(() => (props.order ? `编辑调拨单 ${props.order.transferNo}` : '新增调拨单'))

const drawerSize = computed(() => {
  if (typeof window === 'undefined') return '960px'
  if (window.innerWidth <= 768) return '100%'
  if (window.innerWidth <= 1280) return '760px'
  return '960px'
})

/* ---------------- 打开/重置 ---------------- */
let snapshot = ''

function serialize(): string {
  return JSON.stringify({ form: { ...form }, items: items.value })
}

function reset() {
  form.srcWarehouseId = null
  form.destWarehouseId = null
  form.remark = ''
  items.value = [emptyRow()]
}

async function fill(order: TransferDetail) {
  form.srcWarehouseId = order.srcWarehouseId
  form.destWarehouseId = order.destWarehouseId
  form.remark = order.remark ?? ''
  items.value = (order.items ?? []).map((it) => ({
    batchId: it.batchId ?? null,
    productId: it.productId,
    srcLocationId: it.srcLocationId ?? null,
    destLocationId: it.destLocationId ?? null,
    planQty: it.planQty,
    unit: it.unit ?? '',
    remark: it.remark ?? '',
  }))
}

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    loading.value = true
    try {
      if (props.order) {
        // 重新拉取详情，保证 items/version 最新
        let detail = props.order
        try {
          detail = await getTransfer(props.order.id)
        } catch {
          detail = props.order
        }
        await fill(detail)
      } else {
        reset()
      }
      snapshot = serialize()
      await nextTick()
      formRef.value?.clearValidate()
    } finally {
      loading.value = false
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

/* ---------------- 仓库 / 明细联动 ---------------- */
function onSrcWarehouseChange(value: number | null) {
  form.srcWarehouseId = value
  // 源货位属于源仓，切换后清空；同时避免与目标仓相同
  for (const row of items.value) row.srcLocationId = null
  if (form.destWarehouseId === value) form.destWarehouseId = null
}

function onDestWarehouseChange(value: number | null) {
  if (value && value === form.srcWarehouseId) {
    ElMessage.warning('目标仓库不能与源仓库相同')
    form.destWarehouseId = null
    return
  }
  form.destWarehouseId = value
  for (const row of items.value) row.destLocationId = null
}

/* 商品切换后回填单位（详情按 id 缓存） */
const productCache = new Map<number, Product>()

async function onProductChange(row: ItemRow) {
  const id = row.productId ?? null
  if (!id) {
    row.unit = ''
    return
  }
  let cached = productCache.get(id)
  if (!cached) {
    try {
      const p = await getProductDetail(id)
      productCache.set(id, p)
      cached = p
    } catch {
      return
    }
  }
  row.unit = cached.unit ?? ''
}

function addRow() {
  items.value = [...items.value, emptyRow()]
}

function removeRow(index: number) {
  if (items.value.length <= 1) {
    ElMessage.warning('至少保留 1 行明细')
    return
  }
  items.value = items.value.filter((_, i) => i !== index)
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

function buildPayload(): TransferForm {
  return {
    srcWarehouseId: form.srcWarehouseId as number,
    destWarehouseId: form.destWarehouseId as number,
    remark: form.remark || undefined,
    items: items.value.map((row) => ({
      productId: row.productId as number,
      batchId: row.batchId ?? null,
      srcLocationId: row.srcLocationId ?? null,
      destLocationId: row.destLocationId ?? null,
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
      await updateTransfer(id, { ...payload, id, version: props.order.version })
    } else {
      id = (await createTransfer(payload)).id
    }

    if (withSubmit && hasPerm('sys:transfer:submit')) {
      await submitTransfer(id)
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
  { prop: 'srcLocationId', label: '源货位', width: 184 },
  { prop: 'destLocationId', label: '目标货位', width: 184 },
  { prop: 'planQty', label: '计划数量', width: 136, align: 'right' },
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
    class="transfer-form-drawer"
    @update:model-value="onDrawerToggle"
  >
    <div v-loading="saving || loading" class="form-body">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <div class="form-grid">
          <el-form-item v-if="order" label="调拨单号">
            <span class="num">{{ order.transferNo }}</span>
            <span class="t-xs t-3 form-tip">保存后单号不变</span>
          </el-form-item>
          <el-form-item label="源仓库" prop="srcWarehouseId">
            <WarehouseSelect
              :model-value="form.srcWarehouseId"
              placeholder="选择源仓库"
              style="width: 100%"
              @update:model-value="onSrcWarehouseChange"
            />
          </el-form-item>
          <el-form-item label="目标仓库" prop="destWarehouseId">
            <WarehouseSelect
              :model-value="form.destWarehouseId"
              placeholder="选择目标仓库（不可与源仓相同）"
              style="width: 100%"
              @update:model-value="onDestWarehouseChange"
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
      <h4 class="block-title">调拨明细</h4>
      <ItemEditTable
        v-model="itemRows"
        :columns="itemColumns"
        add-text="添加明细行"
        empty-text="暂无明细，点击下方按钮添加"
        @add="addRow"
        @remove="removeRow"
      >
        <template #col-productId="{ row }">
          <ProductSelect
            :model-value="row.productId ?? null"
            style="width: 100%"
            @update:model-value="row.productId = $event"
            @change="onProductChange(row)"
          />
        </template>
        <template #col-srcLocationId="{ row }">
          <LocationSelect
            :model-value="row.srcLocationId ?? null"
            :warehouse-id="form.srcWarehouseId"
            :disabled="!form.srcWarehouseId"
            placeholder="源货位"
            style="width: 100%"
            @update:model-value="row.srcLocationId = $event"
          />
        </template>
        <template #col-destLocationId="{ row }">
          <LocationSelect
            :model-value="row.destLocationId ?? null"
            :warehouse-id="form.destWarehouseId"
            :disabled="!form.destWarehouseId"
            placeholder="目标货位"
            style="width: 100%"
            @update:model-value="row.destLocationId = $event"
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
            style="width: 120px"
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
        </template>
      </ItemEditTable>
    </div>

    <template #footer>
      <el-button @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save(false)">
        {{ isEdit ? '保存修改' : '保存草稿' }}
      </el-button>
      <el-button
        v-if="hasPerm('sys:transfer:submit')"
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
