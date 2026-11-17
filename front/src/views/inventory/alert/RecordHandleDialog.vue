<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { FormInstance } from 'element-plus'
import type { AlertRecord } from '@/api/alert'
import { ALERT_LEVEL } from '@/utils/dict'
import { formatQty } from '@/utils/format'

/** 处理库存预警弹窗：查看预警摘要并填写处理备注（handleRemark）。 */
defineOptions({ name: 'InventoryAlertHandleDialog' })

const props = defineProps<{
  visible: boolean
  record: AlertRecord | null
  /** 父页面处理接口进行中（控制确认按钮 loading） */
  pending?: boolean
}>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: [remark: string]
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({ handleRemark: '' })

const rules = {
  handleRemark: [
    { required: true, message: '请填写处理措施/备注', trigger: 'blur' },
    { min: 2, max: 200, message: '备注长度 2-200 字', trigger: 'blur' },
  ],
}

const levelType = computed<'info' | 'warning' | 'danger'>(() => {
  const hit = ALERT_LEVEL.find((l) => l.value === props.record?.level)
  return hit?.type === 'warning' || hit?.type === 'danger' ? hit.type : 'info'
})

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    form.handleRemark = ''
    await nextTick()
    formRef.value?.clearValidate()
  },
)

async function submit() {
  if (submitting.value || props.pending || !props.record) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  emit('confirm', form.handleRemark.trim())
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="处理预警"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="record" class="summary">
      <div class="summary__row">
        <span class="t-3">预警类型</span>
        <span>{{ record.alertTypeDesc ?? '—' }}</span>
      </div>
      <div class="summary__row">
        <span class="t-3">预警级别</span>
        <el-tag size="small" effect="light" :type="levelType">{{ record.level }}</el-tag>
      </div>
      <div class="summary__row">
        <span class="t-3">商品</span>
        <span class="text-ellipsis">{{ record.productName }}（{{ record.productCode }}）</span>
      </div>
      <div class="summary__row">
        <span class="t-3">仓库</span>
        <span>{{ record.warehouseName ?? '—' }}</span>
      </div>
      <div class="summary__row">
        <span class="t-3">当前值 / 阈值</span>
        <span class="num t-danger">{{ formatQty(record.currentValue) }}</span>
        <span class="t-3">/</span>
        <span class="num">{{ formatQty(record.thresholdValue) }}</span>
      </div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
      <el-form-item label="处理备注" prop="handleRemark">
        <el-input
          v-model="form.handleRemark"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请填写处理措施（必填），如：已下补货单 / 调整安全库存并观察"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="pending" @click="submit">确认处理</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.summary {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  padding: var(--sp-3);
  margin-bottom: var(--sp-4);
  background-color: var(--c-bg-muted);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-md);
  font-size: var(--fs-sm);

  &__row {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    min-width: 0;

    > span:first-child {
      width: 96px;
      flex-shrink: 0;
    }
  }
}
</style>
