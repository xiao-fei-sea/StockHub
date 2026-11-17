<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { DeptNode, SysUserForm } from '@/types/entity'
import { createUser, getUserDetail, updateUser } from '@/api/user'
import { useUserStore } from '@/store/user'

/** 内置管理员登录名（与后端 UserServiceImpl.BUILTIN_ADMIN 同口径） */
const BUILTIN_ADMIN = 'admin'

/** 用户表单抽屉（新增 / 编辑），校验规则见《系统表单校验规则》§1。 */
defineOptions({ name: 'UserFormDrawer' })

const props = withDefaults(
  defineProps<{
    visible: boolean
    /** null = 新增，否则按 id 拉详情回显 */
    userId: number | null
    /** 部门树数据源（父页面已加载，避免重复请求） */
    deptTree: DeptNode[]
  }>(),
  { userId: null },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const isEdit = computed(() => props.userId != null)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const userStore = useUserStore()

/** 编辑目标用户名（新增为空），用于内置 admin 判定 */
const editingUsername = ref('')

/** 状态保护：内置 admin 或当前登录账号不可在此表单中改为停用（后端同口径拦截）。 */
const statusLocked = computed(() => {
  if (!isEdit.value) return false
  if (editingUsername.value === BUILTIN_ADMIN) return true
  const me = userStore.userInfo
  return me != null && props.userId != null && me.id === props.userId
})
const statusLockTip = computed(() =>
  statusLocked.value && editingUsername.value !== BUILTIN_ADMIN ? '不可停用当前登录账号自身' : '',
)

const emptyForm = (): SysUserForm => ({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  deptId: null,
  status: 1,
})
const form = reactive<SysUserForm>(emptyForm())

/** 密码：新增必填，8-20 位且包含字母和数字（后端口径最少 6 位，前端按校验规则 §1 收紧）。 */
function validatePassword(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  if (isEdit.value) {
    cb()
    return
  }
  const v = String(value ?? '')
  if (!v) {
    cb(new Error('请输入密码'))
  } else if (v.length < 8 || v.length > 20 || !/[A-Za-z]/.test(v) || !/\d/.test(v)) {
    cb(new Error('密码需 8-20 位，且包含字母和数字'))
  } else {
    cb()
  }
}

const rules: FormRules<SysUserForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{2,20}$/, message: '用户名需 2-20 位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [{ required: true, validator: validatePassword, trigger: 'blur' }],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { max: 20, message: '姓名长度不能超过 20 字', trigger: 'blur' },
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  email: [
    { max: 50, message: '邮箱不能超过 50 字', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    formRef.value?.clearValidate()
    Object.assign(form, emptyForm())
    if (props.userId == null) {
      editingUsername.value = ''
      return
    }
    loading.value = true
    try {
      const detail = await getUserDetail(props.userId)
      editingUsername.value = detail.username
      Object.assign(form, {
        username: detail.username,
        password: '',
        realName: detail.realName,
        phone: detail.phone ?? '',
        email: detail.email ?? '',
        deptId: detail.deptId ?? null,
        status: detail.status,
      })
    } finally {
      loading.value = false
    }
  },
)

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && props.userId != null) {
      await updateUser(props.userId, {
        username: form.username,
        realName: form.realName,
        phone: form.phone,
        email: form.email,
        deptId: form.deptId,
        status: form.status,
      })
    } else {
      await createUser({ ...form })
    }
    ElMessage.success('保存成功')
    emit('update:visible', false)
    emit('saved')
  } catch {
    /* 唯一性冲突等业务错误由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="isEdit ? '编辑用户' : '新增用户'"
    size="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="udrawer-body">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="2-20 位字母、数字或下划线"
            maxlength="20"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="初始密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="8-20 位，且包含字母和数字"
            maxlength="20"
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填，11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" maxlength="50" />
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            placeholder="请选择所属部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status" :disabled="statusLocked">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
          <el-text v-if="statusLockTip" tag="div" type="info" size="small">
            {{ statusLockTip }}
          </el-text>
        </el-form-item>
        <p v-if="isEdit" class="udrawer-tip t-xs t-3">
          修改不会影响该用户的密码、已分配角色与数据仓库授权，如需调整请使用列表中的对应操作。
        </p>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.udrawer-body {
  padding-right: var(--sp-2);
}

.udrawer-tip {
  margin: calc(var(--sp-2) * -1) 0 0;
  line-height: 1.6;
}
</style>
