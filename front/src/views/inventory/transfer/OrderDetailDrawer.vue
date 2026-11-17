<script setup lang="ts">
import { computed } from 'vue'
import { OrderDetailDrawer } from '@/components'
import type { DetailField, TableColumn } from '@/components'
import type { TransferDetail } from '@/types/entities/inventory'
import { TRANSFER_CONFIRM_STATUS } from '@/utils/dict'
import { formatQty } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'

/** 详情抽屉可发起的单据动作（父页面统一执行并刷新）。 */
export type TransferAction = 'edit' | 'submit' | 'audit' | 'reject' | 'post' | 'cancel' | 'withdraw' | 'delete'

/**
 * 调拨单详情抽屉：公共 OrderDetailDrawer 封装 + 字段/明细列 + 按状态与权限的底部操作。
 */
defineOptions({ name: 'InventoryTransferDetailDrawer' })

const props = defineProps<{
  visible: boolean
  detail: TransferDetail | null
  loading?: boolean
}>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  action: [type: TransferAction]
}>()

const { hasPerm } = usePermission()

const title = computed(() => (props.detail ? `调拨单 ${props.detail.transferNo}` : '调拨单详情'))

function confirmType(value?: string | null): { label: string; type: 'success' | 'info' } {
  const hit = TRANSFER_CONFIRM_STATUS.find((o) => o.value === value)
  return { label: value ?? '未确认', type: hit?.type === 'success' ? 'success' : 'info' }
}

const fields = computed<DetailField[]>(() => {
  const d = props.detail
  if (!d) return []
  return [
    { label: '源仓库', value: d.srcWarehouseName },
    { label: '目标仓库', value: d.destWarehouseName },
    { label: '目标仓确认', value: d.confirmStatus ?? '未确认' },
    { label: '计划数量', value: formatQty(d.totalQty) },
    { label: '实际数量', value: formatQty(d.actualQty) },
    { label: '创建人', value: d.createBy ?? '—' },
    { label: '创建时间', value: d.createTime ?? '—' },
    { label: '审核人', value: d.auditorId != null ? String(d.auditorId) : '—' },
    { label: '审核时间', value: d.auditTime ?? '—' },
    { label: '过账人', value: d.posterId != null ? String(d.posterId) : '—' },
    { label: '过账时间', value: d.postTime ?? '—' },
    { label: '驳回原因', value: d.rejectReason ?? '—' },
    { label: '备注', value: d.remark ?? '—' },
  ]
})

const itemColumns: TableColumn[] = [
  { prop: 'lineNo', label: '#', width: 48, align: 'center' },
  { prop: 'productCode', label: '商品编码', width: 118, showOverflowTooltip: true },
  { prop: 'productName', label: '商品名称', minWidth: 168, showOverflowTooltip: true },
  { prop: 'batchNo', label: '批次', width: 112, showOverflowTooltip: true },
  { prop: 'srcLocationCode', label: '源货位', width: 132, showOverflowTooltip: true },
  { prop: 'destLocationCode', label: '目标货位', width: 132, showOverflowTooltip: true },
  { prop: 'planQty', label: '计划数量', width: 100, align: 'right' },
  { prop: 'actualQty', label: '实际数量', width: 100, align: 'right' },
  { prop: 'unit', label: '单位', width: 70, align: 'center' },
  { prop: 'remark', label: '行备注', minWidth: 110, showOverflowTooltip: true },
]

const items = computed(() => (props.detail?.items ?? []) as unknown as Record<string, unknown>[])

const status = computed(() => props.detail?.status ?? null)

function act(type: TransferAction) {
  emit('action', type)
}
</script>

<template>
  <OrderDetailDrawer
    :visible="visible"
    :title="title"
    :status="status"
    :fields="fields"
    :items="items"
    :item-columns="itemColumns"
    :loading="loading ?? false"
    @update:visible="emit('update:visible', $event)"
  >
    <template #field="{ field }">
      <el-tag
        v-if="field.label === '目标仓确认'"
        size="small"
        effect="light"
        :type="confirmType(String(field.value ?? '')).type"
      >
        {{ confirmType(String(field.value ?? '')).label }}
      </el-tag>
      <span v-else-if="field.label === '驳回原因' && field.value && field.value !== '—'" class="t-danger">
        {{ field.value }}
      </span>
      <span v-else class="text-ellipsis">{{ field.value ?? '—' }}</span>
    </template>

    <template #extra>
      <p v-if="detail?.status === 3" class="t-sm t-success">
        该单已过账：源仓库存已减少、目标仓库存已增加，并已生成调拨出/调拨入流水。
      </p>
      <p v-else-if="detail?.status === 5" class="t-sm t-3">该单已取消，为终态不可再操作。</p>
    </template>

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>

      <!-- 草稿 -->
      <template v-if="detail?.status === 0">
        <el-button
          v-if="hasPerm('sys:transfer:edit')"
          type="primary"
          plain
          @click="act('edit')"
        >
          编辑
        </el-button>
        <el-button v-if="hasPerm('sys:transfer:submit')" type="primary" @click="act('submit')">
          提交审核
        </el-button>
        <el-button v-if="hasPerm('sys:transfer:cancel')" @click="act('cancel')">取消单据</el-button>
        <el-button v-if="hasPerm('sys:transfer:delete')" type="danger" plain @click="act('delete')">
          删除
        </el-button>
      </template>

      <!-- 待审核 -->
      <template v-else-if="detail?.status === 1">
        <el-button v-if="hasPerm('sys:transfer:audit')" type="primary" @click="act('audit')">
          审核通过
        </el-button>
        <el-button v-if="hasPerm('sys:transfer:reject')" type="danger" plain @click="act('reject')">
          驳回
        </el-button>
        <el-button v-if="hasPerm('sys:transfer:withdraw')" @click="act('withdraw')">撤回</el-button>
        <el-button v-if="hasPerm('sys:transfer:cancel')" @click="act('cancel')">取消单据</el-button>
      </template>

      <!-- 已审核 -->
      <template v-else-if="detail?.status === 2">
        <el-button v-if="hasPerm('sys:transfer:post')" type="primary" @click="act('post')">
          过账
        </el-button>
      </template>

      <!-- 已驳回 -->
      <template v-else-if="detail?.status === 4">
        <el-button
          v-if="hasPerm('sys:transfer:edit')"
          type="primary"
          plain
          @click="act('edit')"
        >
          编辑
        </el-button>
        <el-button v-if="hasPerm('sys:transfer:withdraw')" type="primary" @click="act('withdraw')">
          撤回至草稿
        </el-button>
        <el-button v-if="hasPerm('sys:transfer:cancel')" @click="act('cancel')">取消单据</el-button>
      </template>
    </template>
  </OrderDetailDrawer>
</template>
