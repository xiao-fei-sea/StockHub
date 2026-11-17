<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { AppTable, OrderDetailDrawer } from '@/components'
import type { DetailField, TableColumn } from '@/components'
import type { StocktakeDetail, StocktakeItem, StocktakeInputForm } from '@/types/entities/inventory'
import { formatQty } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'

/** 详情抽屉可发起的单据动作（父页面统一执行并刷新）。 */
export type StocktakeAction = 'submit' | 'audit' | 'reject' | 'post' | 'cancel' | 'withdraw' | 'delete'

/**
 * 盘点单详情抽屉：公共 OrderDetailDrawer 封装 + 盘点明细（账面/实盘/差异标红）
 * + 草稿/已驳回状态下的实盘录入模式 + 按状态与权限的底部操作。
 */
defineOptions({ name: 'InventoryStocktakeDetailDrawer' })

const props = defineProps<{
  visible: boolean
  detail: StocktakeDetail | null
  loading?: boolean
}>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  action: [type: StocktakeAction]
  /** 录入实盘保存（父页面调用 PUT /stocktakes/{id}） */
  'save-input': [body: StocktakeInputForm]
}>()

const { hasPerm } = usePermission()
const inputSaving = ref(false)

const title = computed(() => (props.detail ? `盘点单 ${props.detail.stocktakeNo}` : '盘点单详情'))

/* ---------------- 基本信息字段 ---------------- */
const fields = computed<DetailField[]>(() => {
  const d = props.detail
  if (!d) return []
  return [
    { label: '盘点仓库', value: d.warehouseName },
    { label: '库区', value: d.areaName ?? '—' },
    { label: '盘点范围', value: d.scope },
    { label: '差异状态', value: d.diffStatus ?? '未处理' },
    { label: '总差异数', value: formatQty(d.totalDifferQty) },
    { label: '计划日期', value: d.planDate ?? '—' },
    { label: '创建人', value: d.createBy ?? '—' },
    { label: '创建时间', value: d.createTime ?? '—' },
    { label: '审核时间', value: d.auditTime ?? '—' },
    { label: '过账时间', value: d.postTime ?? '—' },
    { label: '完成时间', value: d.finishTime ?? '—' },
    { label: '驳回原因', value: d.rejectReason ?? '—' },
    { label: '备注', value: d.remark ?? '—' },
  ]
})

/* ---------------- 实盘录入态 ---------------- */
const inputMode = ref(false)
const editRows = ref<StocktakeItem[]>([])

const canInput = computed(() => {
  const d = props.detail
  if (!d || !hasPerm('sys:stocktake:edit')) return false
  return d.status === 0 || d.status === 4
})

watch(
  () => props.detail,
  (d) => {
    if (inputMode.value) return
    editRows.value = (d?.items ?? []).map((it) => ({ ...it }))
  },
  { immediate: true },
)

watch(
  () => props.visible,
  (v) => {
    if (!v) inputMode.value = false
  },
)

function enterInput() {
  editRows.value = (props.detail?.items ?? []).map((it) => ({ ...it }))
  inputMode.value = true
}

function cancelInput() {
  inputMode.value = false
}

const rows = computed<StocktakeItem[]>(() =>
  inputMode.value ? editRows.value : (props.detail?.items ?? []),
)

const tableRows = computed(() => rows.value as unknown as Record<string, unknown>[])

const filledCount = computed(() => rows.value.filter((r) => r.actualQty != null).length)
const diffCount = computed(
  () => rows.value.filter((r) => r.actualQty != null && Number(r.actualQty) !== Number(r.bookQty)).length,
)

function diffOf(row: StocktakeItem): number | null {
  if (row.actualQty == null) return null
  return Number((Number(row.actualQty) - Number(row.bookQty)).toFixed(3))
}

function saveInput() {
  const d = props.detail
  if (!d) return
  const items = rows.value
    .filter((r) => r.actualQty != null)
    .map((r) => ({
      id: r.id,
      actualQty: Number(r.actualQty),
      diffReason: r.diffReason?.trim() || undefined,
    }))
  if (!items.length) {
    ElMessage.warning('请至少录入 1 行实盘数')
    return
  }
  const missing = rows.value.length - items.length
  if (missing > 0) {
    ElMessage.info(`还有 ${missing} 行未录入实盘数，已保存已录入部分；提交前需全部录完`)
  }
  inputSaving.value = true
  try {
    emit('save-input', { version: d.version, items })
  } finally {
    inputSaving.value = false
  }
}

/* ---------------- 明细列 ---------------- */
const itemColumns: TableColumn[] = [
  { prop: 'lineNo', label: '#', width: 48, align: 'center' },
  { prop: 'locationCode', label: '货位', width: 148, showOverflowTooltip: true },
  { prop: 'productCode', label: '商品编码', width: 118, showOverflowTooltip: true },
  { prop: 'productName', label: '商品名称', minWidth: 168, showOverflowTooltip: true },
  { prop: 'batchNo', label: '批次', width: 112, showOverflowTooltip: true },
  { prop: 'bookQty', label: '账面数', width: 96, align: 'right' },
  { prop: 'actualQty', label: '实盘数', width: 128, align: 'right' },
  { prop: 'differQty', label: '差异数', width: 104, align: 'right' },
  { prop: 'diffReason', label: '差异原因', minWidth: 140 },
  { prop: 'unit', label: '单位', width: 70, align: 'center' },
]

/* ---------------- 底部动作 ---------------- */
const status = computed(() => props.detail?.status ?? null)

function act(type: StocktakeAction) {
  emit('action', type)
}
</script>

<template>
  <OrderDetailDrawer
    :visible="visible"
    :title="title"
    :status="status"
    :fields="fields"
    :items="[]"
    :item-columns="[]"
    :loading="(loading ?? false) || inputSaving"
    @update:visible="emit('update:visible', $event)"
  >
    <template #field="{ field }">
      <el-tag
        v-if="field.label === '差异状态'"
        size="small"
        effect="light"
        :type="field.value === '已处理' ? 'success' : 'warning'"
      >
        {{ field.value }}
      </el-tag>
      <span
        v-else-if="field.label === '总差异数' && field.value !== '—' && Number(field.value) !== 0"
        class="num t-danger"
      >
        {{ field.value }}
      </span>
      <span v-else-if="field.label === '驳回原因' && field.value && field.value !== '—'" class="t-danger">
        {{ field.value }}
      </span>
      <span v-else class="text-ellipsis">{{ field.value ?? '—' }}</span>
    </template>

    <template #extra>
      <h4 class="block-title">盘点明细</h4>
      <div class="item-toolbar">
        <span class="t-sm t-3">
          共 {{ rows.length }} 行 · 已录 {{ filledCount }} 行 · 存在差异 {{ diffCount }} 行
        </span>
        <div class="item-toolbar__actions">
          <el-button
            v-if="!inputMode && canInput"
            size="small"
            type="primary"
            plain
            @click="enterInput"
          >
            录入实盘
          </el-button>
          <span v-else-if="inputMode" class="t-xs t-danger">实盘录入中：差异数自动计算，负数为盘亏</span>
        </div>
      </div>

      <AppTable
        :columns="itemColumns"
        :data="tableRows"
        :pagination="false"
        size="small"
        row-key="id"
        :empty-text="detail ? '该盘点单暂无明细' : ''"
      >
        <template #col-bookQty="{ row }">
          <span class="num t-3">{{ formatQty((row as StocktakeItem).bookQty) }}</span>
        </template>
        <template #col-actualQty="{ row }">
          <el-input-number
            v-if="inputMode"
            v-model="(row as StocktakeItem).actualQty"
            :min="0"
            :precision="3"
            :step="1"
            controls-position="right"
            placeholder="实盘"
            size="small"
            style="width: 116px"
          />
          <span v-else class="num">{{ formatQty((row as StocktakeItem).actualQty) }}</span>
        </template>
        <template #col-differQty="{ row }">
          <span
            class="num"
            :class="
              diffOf(row as StocktakeItem) === null
                ? 't-3'
                : diffOf(row as StocktakeItem) === 0
                  ? 't-3'
                  : 't-danger'
            "
          >
            {{
              diffOf(row as StocktakeItem) === null
                ? '—'
                : `${diffOf(row as StocktakeItem)! > 0 ? '+' : ''}${formatQty(diffOf(row as StocktakeItem))}`
            }}
          </span>
        </template>
        <template #col-diffReason="{ row }">
          <el-input
            v-if="inputMode"
            v-model="(row as StocktakeItem).diffReason"
            placeholder="差异原因（选填）"
            size="small"
            clearable
          />
          <span v-else>{{ (row as StocktakeItem).diffReason ?? '—' }}</span>
        </template>
        <template #col-unit="{ row }">
          {{ (row as StocktakeItem).unit ?? '—' }}
        </template>
      </AppTable>
    </template>

    <template #footer>
      <!-- 实盘录入模式 -->
      <template v-if="inputMode">
        <el-button @click="cancelInput">取消录入</el-button>
        <el-button type="primary" :loading="inputSaving" @click="saveInput">保存实盘数</el-button>
      </template>

      <!-- 常规状态操作 -->
      <template v-else>
        <el-button @click="emit('update:visible', false)">关闭</el-button>

        <template v-if="detail?.status === 0">
          <el-button v-if="hasPerm('sys:stocktake:submit')" type="primary" @click="act('submit')">
            提交审核
          </el-button>
          <el-button v-if="hasPerm('sys:stocktake:cancel')" @click="act('cancel')">取消单据</el-button>
          <el-button v-if="hasPerm('sys:stocktake:delete')" type="danger" plain @click="act('delete')">
            删除
          </el-button>
        </template>

        <template v-else-if="detail?.status === 1">
          <el-button v-if="hasPerm('sys:stocktake:audit')" type="primary" @click="act('audit')">
            审核通过
          </el-button>
          <el-button v-if="hasPerm('sys:stocktake:reject')" type="danger" plain @click="act('reject')">
            驳回
          </el-button>
          <el-button v-if="hasPerm('sys:stocktake:withdraw')" @click="act('withdraw')">撤回</el-button>
          <el-button v-if="hasPerm('sys:stocktake:cancel')" @click="act('cancel')">取消单据</el-button>
        </template>

        <template v-else-if="detail?.status === 2">
          <el-button v-if="hasPerm('sys:stocktake:post')" type="primary" @click="act('post')">
            过账（按差异调整库存）
          </el-button>
        </template>

        <template v-else-if="detail?.status === 4">
          <el-button
            v-if="hasPerm('sys:stocktake:withdraw')"
            type="primary"
            @click="act('withdraw')"
          >
            撤回至草稿
          </el-button>
          <el-button v-if="hasPerm('sys:stocktake:cancel')" @click="act('cancel')">取消单据</el-button>
        </template>
      </template>
    </template>
  </OrderDetailDrawer>
</template>

<style scoped lang="scss">
.item-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
  margin-bottom: var(--sp-3);

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }
}
</style>
