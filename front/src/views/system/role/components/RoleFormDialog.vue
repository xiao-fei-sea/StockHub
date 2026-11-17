<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { RoleForm } from '@/types/entity'
import { createRole, getRoleDetail, updateRole } from '@/api/role'

/** 角色表单弹窗（新增 / 编辑），校验规则见《系统表单校验规则》§2。 */
defineOptions({ name: 'RoleFormDialog' })

const props = withDefaults(
  defineProps<{
    visible: boolean
    /** null = 新增 */
    roleId: number | null
  }>(),
  { roleId: null },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const isEdit = computed(() => props.roleId != null)
const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)

const emptyForm = (): RoleForm => ({
  roleName: '',
  roleKey: '',
  roleSort: 0,
  status: 1,
  remark: '',
})
const form = reactive<RoleForm>(emptyForm())

/**
 * 角色标识规则（§2）：字母开头，仅含字母、数字、下划线，2-31 位。
 * 文档正则为小写开头，但内置角色为 ROLE_* 形态，故放开大小写以保证编辑回显可通过。
 */
const ROLE_KEY_RE = /^[A-Za-z][A-Za-z0-9_]{1,30}$/

const rules: FormRules<RoleForm> = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 30, message: '角色名称不能超过 30 字', trigger: 'blur' },
  ],
  roleKey: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    { pattern: ROLE_KEY_RE, message: '角色标识需字母开头，仅含字母、数字、下划线', trigger: 'blur' },
  ],
  roleSort: [{ type: 'integer', min: 0, message: '排序号需为不小于 0 的整数', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  remark: [{ max: 200, message: '备注不能超过 200 字', trigger: 'blur' }],
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    formRef.value?.clearValidate()
    Object.assign(form, emptyForm())
    if (props.roleId == null) return
    loading.value = true
    try {
      const detail = await getRoleDetail(props.roleId)
      Object.assign(form, {
        roleName: detail.roleName,
        roleKey: detail.roleKey,
        roleSort: detail.roleSort ?? 0,
        status: detail.status,
        remark: detail.remark ?? '',
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
    if (isEdit.value && props.roleId != null) {
      await updateRole(props.roleId, { ...form })
    } else {
      await createRole({ ...form })
    }
    ElMessage.success('保存成功')
    emit('update:visible', false)
    emit('saved')
  } catch {
    /* 角色标识唯一性冲突等由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑角色' : '新增角色'"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如：仓库管理员" maxlength="30" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input
            v-model="form.roleKey"
            placeholder="如：ROLE_MANAGER，字母开头，仅含字母、数字、下划线"
            maxlength="31"
            class="mono"
          />
        </el-form-item>
        <el-form-item label="排序号" prop="roleSort">
          <el-input-number v-model="form.roleSort" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="选填"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <p v-if="!isEdit" class="t-xs t-3 role-form__tip">
          新增后可在「分配权限」中配置菜单权限；创建人将自动获得所配置的菜单权限。
        </p>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.role-form__tip {
  margin: calc(var(--sp-2) * -1) 0 0;
  line-height: 1.6;
}
</style>
