<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { WarehouseSelect, confirmDialog } from '@/components'
import { createStocktake, getAreaOptions } from '@/api/stocktake'
import type { StocktakeForm } from '@/types/entities/inventory'
import type { AreaOption } from '@/types/entity'

/**
 * 盘点单创建抽屉：仓库 + 盘点范围（按库区/按仓库）+ 计划日期。
 * 明细由服务端按范围自动生成账面数快照，前端不录明细（接口文档 12 API-STK-002）。
 */
defineOptions({ name: 'InventoryStocktakeFormDrawer' })

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const areas = ref<AreaOption[]>([])
const areasLoading = ref(false)

const form = reactive({
  warehouseId: null as number | null,
  scope: '按仓库',
  areaId: null as number | null,
  planDate: '',
  includeEmptyLocation: false,
  remark: '',
})

const isAreaScope = computed(() => form.scope === '按库区')

const rules = computed(() => ({
  warehouseId: [{ required: true, message: '请选择盘点仓库', trigger: 'change' }],
  scope: [{ required: true, message: '请选择盘点范围', trigger: 'change' }],
  areaId: [
    {
      required: isAreaScope.value,
      message: '按库区盘点时请选择库区',
      trigger: 'change',
    },
  ],
}))

async function loadAreas() {
  if (!form.warehouseId) {
    areas.value = []
    return
  }
  areasLoading.value = true
  try {
    areas.value = (await getAreaOptions({ warehouseId: form.warehouseId })) ?? []
  } catch {
    areas.value = []
  } finally {
    areasLoading.value = false
  }
}

function onWarehouseChange(value: number | null) {
  form.warehouseId = value
  form.areaId = null
  areas.value = []
  if (value && isAreaScope.value) void loadAreas()
}

function onScopeChange() {
  form.areaId = null
  if (isAreaScope.value) void loadAreas()
}

/* ---------------- 打开/重置 ---------------- */
let snapshot = ''

function serialize(): string {
  return JSON.stringify({ ...form })
}

function reset() {
  form.warehouseId = null
  form.scope = '按仓库'
  form.areaId = null
  form.planDate = ''
  form.includeEmptyLocation = false
  form.remark = ''
  areas.value = []
}

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    reset()
    snapshot = serialize()
    await nextTick()
    formRef.value?.clearValidate()
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

/* ---------------- 创建 ---------------- */
function buildPayload(): StocktakeForm {
  return {
    warehouseId: form.warehouseId as number,
    scope: form.scope,
    areaId: isAreaScope.value ? form.areaId : null,
    planDate: form.planDate || undefined,
    includeEmptyLocation: form.includeEmptyLocation,
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
  saving.value = true
  try {
    const res = await createStocktake(buildPayload())
    const count = res.itemCount != null ? `，生成 ${res.itemCount} 条盘点明细` : ''
    ElMessage.success(`盘点单 ${res.stocktakeNo} 创建成功${count}`)
    snapshot = serialize()
    emit('saved')
    emit('update:visible', false)
  } catch {
    // 业务错误已由 request 层统一提示，保留抽屉内容供修正后重试
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    size="560px"
    title="新建盘点单"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @update:model-value="onDrawerToggle"
  >
    <div v-loading="saving" class="form-body">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="盘点仓库" prop="warehouseId">
          <WarehouseSelect
            :model-value="form.warehouseId"
            style="width: 100%"
            @update:model-value="onWarehouseChange"
          />
        </el-form-item>

        <el-form-item label="盘点范围" prop="scope">
          <el-radio-group v-model="form.scope" @change="onScopeChange">
            <el-radio value="按仓库">按仓库（全仓）</el-radio>
            <el-radio value="按库区">按库区</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="isAreaScope" label="盘点库区" prop="areaId">
          <el-select
            v-model="form.areaId"
            placeholder="选择库区"
            :loading="areasLoading"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="a in areas"
              :key="a.id"
              :label="`${a.areaCode} ${a.areaName}`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="计划日期">
          <el-date-picker
            v-model="form.planDate"
            type="date"
            placeholder="盘点计划执行日期"
            value-format="YYYY-MM-DD"
            clearable
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="含空货位">
          <el-switch v-model="form.includeEmptyLocation" active-text="扫描空货位" />
          <span class="t-xs t-3 form-tip">开启后空货位也生成盘点行</span>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="备注（选填）"
          />
        </el-form-item>
      </el-form>

      <p class="t-xs t-3 tip">
        创建后服务端按盘点范围自动生成账面明细（账面数快照），随后在详情中录入实盘数并提交审核。
      </p>
    </div>

    <template #footer>
      <el-button @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">创建盘点单</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.form-body {
  padding-bottom: var(--sp-4);
}

.form-tip {
  margin-left: var(--sp-2);
}

.tip {
  margin: var(--sp-4) 0 0;
  line-height: var(--lh-base);
}
</style>
