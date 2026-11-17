<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/components'
import type { WarehouseOption } from '@/types/entity'
import { getWarehouseOptions } from '@/api/warehouse'
import { getRoles } from '@/api/role'
import { assignUserWarehouses, getUserDetail, getUserWarehouses } from '@/api/user'

/** 分配数据仓库弹窗：多选仓库提交 warehouseIds（整体替换 sys_user_warehouse）。 */
defineOptions({ name: 'AssignWarehouseDialog' })

const props = withDefaults(
  defineProps<{
    visible: boolean
    userId: number | null
    username?: string
  }>(),
  { username: '' },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const loading = ref(false)
const submitting = ref(false)
const options = ref<WarehouseOption[]>([])
const checked = ref<number[]>([])
const origin = ref<number[]>([])
const error = ref('')

/** ADMIN / VIEWER 数据权限为全部仓库，无需分配（表单校验规则 §1）。 */
const unlimited = ref(false)

async function load() {
  if (props.userId == null) return
  loading.value = true
  error.value = ''
  try {
    const [opts, info, detail, rolePage] = await Promise.all([
      getWarehouseOptions(),
      getUserWarehouses(props.userId),
      getUserDetail(props.userId),
      getRoles({ pageNum: 1, pageSize: 100 }),
    ])
    options.value = opts ?? []
    checked.value = [...(info.warehouseIds ?? [])]
    origin.value = [...checked.value]
    // 按角色标识判断 ADMIN / VIEWER（其数据权限为全部仓库，无需分配）
    const roleIds = detail.roleIds ?? []
    unlimited.value = (rolePage.list ?? [])
      .filter((r) => roleIds.includes(r.id))
      .some((r) => r.roleKey === 'ROLE_ADMIN' || r.roleKey === 'ROLE_VIEWER')
  } catch {
    error.value = '数据加载失败，请关闭后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      checked.value = []
      origin.value = []
      unlimited.value = false
      options.value = []
      load()
    }
  },
)

const hint = computed(() => {
  if (unlimited.value) {
    return '该用户角色为 ROLE_ADMIN / ROLE_VIEWER，数据权限覆盖全部仓库，无需分配。'
  }
  if (!options.value.length && !loading.value) {
    return '暂无可选仓库'
  }
  return '仅勾选的仓库对该用户可见且可操作，保存后整体替换原有授权。'
})

async function submit() {
  if (props.userId == null) return
  if (!unlimited.value && !checked.value.length) {
    if (origin.value.length) {
      const ok = await confirmDialog({
        title: '清空数据仓库授权',
        message: `确定清空用户「${props.username || '—'}」的全部数据仓库授权吗？\n清空后该用户将无法查看与操作任何仓库数据。`,
        confirmText: '确认清空',
        type: 'danger',
      })
      if (!ok) return
    } else {
      error.value = '请至少选择一个数据仓库'
      return
    }
  }
  submitting.value = true
  try {
    await assignUserWarehouses(props.userId, [...checked.value])
    ElMessage.success('数据仓库分配成功')
    emit('update:visible', false)
    emit('success')
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="分配数据仓库"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="wh-assign">
      <p class="wh-assign__tip t-sm t-2">
        为用户「<strong>{{ username || '—' }}</strong>」设置数据权限范围：
      </p>

      <el-select
        v-model="checked"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        :max-collapse-tags="4"
        :disabled="unlimited"
        placeholder="请选择数据仓库（可多选）"
        style="width: 100%"
      >
        <el-option
          v-for="w in options"
          :key="w.id"
          :label="`${w.warehouseName}（${w.warehouseCode}）`"
          :value="w.id"
        />
      </el-select>

      <p class="wh-assign__hint t-xs t-3">{{ hint }}</p>
      <p v-if="error" class="wh-assign__error t-sm">{{ error }}</p>
      <p v-if="checked.length && !unlimited" class="t-xs t-3">
        已选 {{ checked.length }} 个仓库
      </p>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.wh-assign {
  &__tip {
    margin: 0 0 var(--sp-3);
    line-height: 1.6;
  }

  &__hint {
    margin: var(--sp-2) 0 0;
    line-height: 1.6;
  }

  &__error {
    margin: var(--sp-2) 0 0;
    color: var(--c-danger);
  }
}
</style>
