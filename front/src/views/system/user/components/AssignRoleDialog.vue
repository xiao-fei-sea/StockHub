<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { RoleOption } from '@/types/entity'
import { assignUserRoles, getUserDetail } from '@/api/user'
import { getRoles } from '@/api/role'

/** 分配角色弹窗：勾选角色提交 roleIds（整体替换 sys_user_role）。 */
defineOptions({ name: 'AssignRoleDialog' })

const props = withDefaults(
  defineProps<{
    visible: boolean
    userId: number | null
    /** 用于标题与提示展示 */
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
const roles = ref<RoleOption[]>([])
const checked = ref<number[]>([])
const error = ref('')

async function load() {
  if (props.userId == null) return
  loading.value = true
  error.value = ''
  try {
    const [rolePage, detail] = await Promise.all([
      getRoles({ pageNum: 1, pageSize: 100 }),
      getUserDetail(props.userId),
    ])
    roles.value = rolePage.list ?? []
    checked.value = detail.roleIds ?? []
  } catch {
    error.value = '角色数据加载失败，请关闭后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      checked.value = []
      roles.value = []
      load()
    }
  },
)

async function submit() {
  if (props.userId == null) return
  if (!checked.value.length) {
    error.value = '请至少分配一个角色'
    return
  }
  submitting.value = true
  try {
    await assignUserRoles(props.userId, [...checked.value])
    ElMessage.success('角色分配成功')
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
    title="分配角色"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="role-assign">
      <p class="role-assign__tip t-sm t-2">
        为用户「<strong>{{ username || '—' }}</strong>」分配角色，保存后整体替换其已有角色。
      </p>

      <el-checkbox-group
        v-if="roles.length"
        v-model="checked"
        @change="error = ''"
      >
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" :disabled="r.status === 0">
          <span class="role-assign__item">
            {{ r.roleName }}
            <span class="mono t-xs t-3">{{ r.roleKey }}</span>
            <span v-if="r.status === 0" class="t-xs t-3">（已停用）</span>
          </span>
        </el-checkbox>
      </el-checkbox-group>
      <p v-else-if="!loading" class="t-sm t-3">暂无可分配角色</p>

      <p v-if="error" class="role-assign__error t-sm">{{ error }}</p>
      <p class="t-xs t-3">提示：不可为普通用户分配 ROLE_ADMIN 超管角色，冲突时以后端校验为准。</p>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.role-assign {
  &__tip {
    margin: 0 0 var(--sp-3);
    line-height: 1.6;
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__error {
    margin: var(--sp-2) 0 0;
    color: var(--c-danger);
  }

  :deep(.el-checkbox) {
    width: 100%;
    padding: var(--sp-2) 0;
    margin-right: 0;
    border-bottom: 1px dashed var(--c-border);

    &:last-of-type {
      border-bottom: none;
    }
  }
}
</style>
