<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { ProductSelect, WarehouseSelect } from '@/components'
import { getCategoryTree } from '@/api/inventory'
import type { AlertRule, AlertRuleForm, CategoryNode } from '@/types/entities/inventory'
import { ENABLE_STATUS } from '@/utils/dict'
import { ALERT_TYPE_OPTIONS } from './constants'

/**
 * 预警规则新建/编辑弹窗：按 alertType 动态阈值
 * LOW_STOCK→minQty 必填；HIGH_STOCK→maxQty 必填；NEAR_EXPIRY→nearDays≥1；EXPIRED 无阈值。
 */
defineOptions({ name: 'InventoryAlertRuleFormDialog' })

const props = defineProps<{
  visible: boolean
  rule: AlertRule | null
}>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [payload: AlertRuleForm]
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)

const categoryTree = ref<CategoryNode[]>([])
const categoryLoading = ref(false)

const form = reactive({
  ruleName: '',
  warehouseId: null as number | null,
  categoryId: null as number | null,
  productId: null as number | null,
  alertType: 'LOW_STOCK',
  minQty: null as number | null,
  maxQty: null as number | null,
  nearDays: null as number | null,
  status: 1,
  remark: '',
})

/* ---------------- 动态校验（随 alertType 切换） ---------------- */
const rules = computed(() => ({
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  alertType: [{ required: true, message: '请选择预警类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择启用状态', trigger: 'change' }],
  minQty:
    form.alertType === 'LOW_STOCK'
      ? [
          { required: true, type: 'number' as const, message: '请输入下限数量', trigger: 'change' },
          { type: 'number' as const, min: 0, message: '下限数量不能小于 0', trigger: 'change' },
        ]
      : [],
  maxQty:
    form.alertType === 'HIGH_STOCK'
      ? [
          { required: true, type: 'number' as const, message: '请输入上限数量', trigger: 'change' },
          { type: 'number' as const, min: 0, message: '上限数量不能小于 0', trigger: 'change' },
        ]
      : [],
  nearDays:
    form.alertType === 'NEAR_EXPIRY'
      ? [
          {
            required: true,
            type: 'integer' as const,
            min: 1,
            message: '提前天数须为不小于 1 的整数',
            trigger: 'change',
          },
        ]
      : [],
}))

function onAlertTypeChange() {
  // 阈值字段互斥：切换类型即清空，避免残留无效值
  form.minQty = null
  form.maxQty = null
  form.nearDays = null
}

const thresholdHint = computed(() => {
  if (form.alertType === 'LOW_STOCK') return '库存低于该下限时触发预警'
  if (form.alertType === 'HIGH_STOCK') return '库存高于该上限时触发预警'
  if (form.alertType === 'NEAR_EXPIRY') return '距保质期到期不足该天数时预警（≥1 天）'
  return '已过期类型无阈值，按批次到期时间直接判定'
})

/* ---------------- 打开 / 回填 ---------------- */
function reset() {
  form.ruleName = ''
  form.warehouseId = null
  form.categoryId = null
  form.productId = null
  form.alertType = 'LOW_STOCK'
  form.minQty = null
  form.maxQty = null
  form.nearDays = null
  form.status = 1
  form.remark = ''
}

function fill(rule: AlertRule) {
  form.ruleName = rule.ruleName
  form.warehouseId = rule.warehouseId ?? null
  form.categoryId = rule.categoryId ?? null
  form.productId = rule.productId ?? null
  form.alertType = rule.alertType
  form.minQty = rule.minQty ?? null
  form.maxQty = rule.maxQty ?? null
  form.nearDays = rule.nearDays ?? null
  form.status = rule.status ?? 1
  form.remark = rule.remark ?? ''
}

async function loadCategories() {
  categoryLoading.value = true
  try {
    categoryTree.value = (await getCategoryTree()) ?? []
  } catch {
    categoryTree.value = []
  } finally {
    categoryLoading.value = false
  }
}

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    if (props.rule) fill(props.rule)
    else reset()
    if (!categoryTree.value.length) void loadCategories()
    await nextTick()
    formRef.value?.clearValidate()
  },
)

/* ---------------- 保存 ---------------- */
function buildPayload(): AlertRuleForm {
  const isLow = form.alertType === 'LOW_STOCK'
  const isHigh = form.alertType === 'HIGH_STOCK'
  const isNear = form.alertType === 'NEAR_EXPIRY'
  return {
    ruleName: form.ruleName.trim(),
    warehouseId: form.warehouseId,
    categoryId: form.categoryId,
    productId: form.productId,
    alertType: form.alertType,
    minQty: isLow ? form.minQty : undefined,
    maxQty: isHigh ? form.maxQty : undefined,
    nearDays: isNear ? form.nearDays : undefined,
    status: form.status,
    remark: form.remark || undefined,
  }
}

async function save() {
  if (saving.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (form.alertType === 'LOW_STOCK' && (form.minQty == null || form.minQty < 0)) {
    ElMessage.warning('请输入不小于 0 的下限数量')
    return
  }
  if (form.alertType === 'HIGH_STOCK' && (form.maxQty == null || form.maxQty < 0)) {
    ElMessage.warning('请输入不小于 0 的上限数量')
    return
  }
  if (form.alertType === 'NEAR_EXPIRY' && (!Number.isInteger(form.nearDays) || (form.nearDays ?? 0) < 1)) {
    ElMessage.warning('提前天数须为不小于 1 的整数')
    return
  }
  saving.value = true
  try {
    emit('save', buildPayload())
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="rule ? '编辑预警规则' : '新增预警规则'"
    width="600px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
      <el-form-item label="规则名称" prop="ruleName">
        <el-input
          v-model="form.ruleName"
          maxlength="60"
          show-word-limit
          placeholder="如：奶粉低于 10 预警"
          clearable
        />
      </el-form-item>

      <el-form-item label="预警类型" prop="alertType">
        <el-select
          v-model="form.alertType"
          style="width: 100%"
          @change="onAlertTypeChange"
        >
          <el-option
            v-for="o in ALERT_TYPE_OPTIONS"
            :key="o.value"
            :label="o.label"
            :value="o.value"
          />
        </el-select>
        <span class="t-xs t-3">{{ thresholdHint }}</span>
      </el-form-item>

      <!-- 动态阈值 -->
      <el-form-item v-if="form.alertType === 'LOW_STOCK'" label="库存下限" prop="minQty">
        <el-input-number
          v-model="form.minQty"
          :min="0"
          :precision="3"
          :step="1"
          controls-position="right"
          placeholder="低于该值触发"
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item v-else-if="form.alertType === 'HIGH_STOCK'" label="库存上限" prop="maxQty">
        <el-input-number
          v-model="form.maxQty"
          :min="0"
          :precision="3"
          :step="1"
          controls-position="right"
          placeholder="高于该值触发"
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item v-else-if="form.alertType === 'NEAR_EXPIRY'" label="提前天数" prop="nearDays">
        <el-input-number
          v-model="form.nearDays"
          :min="1"
          :precision="0"
          :step="1"
          controls-position="right"
          placeholder="到期前 N 天预警"
          style="width: 200px"
        />
        <span class="t-xs t-3">单位：天（≥1）</span>
      </el-form-item>

      <el-form-item label="适用仓库">
        <WarehouseSelect
          v-model="form.warehouseId"
          placeholder="全部仓库（不限）"
          clearable
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="适用分类">
        <el-tree-select
          v-model="form.categoryId"
          :data="categoryTree"
          :props="{ label: 'categoryName', children: 'children' }"
          node-key="id"
          placeholder="全部分类（不限）"
          clearable
          filterable
          check-strictly
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="适用商品">
        <ProductSelect
          v-model="form.productId"
          placeholder="全部商品（不限）"
          clearable
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="启停用" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio
            v-for="o in ENABLE_STATUS"
            :key="Number(o.value)"
            :value="Number(o.value)"
          >
            {{ o.label }}
          </el-radio>
        </el-radio-group>
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
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        {{ rule ? '保存修改' : '创建规则' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.t-xs {
  display: block;
  margin-top: var(--sp-1);
  line-height: var(--lh-base);
}
</style>
