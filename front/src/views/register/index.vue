<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Check, Lock, Phone, User, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { register } from '@/api/auth'
import AuthShell from '@/components/AuthShell.vue'

defineOptions({ name: 'RegisterPage' })

const router = useRouter()

/* ============================ 状态 ============================ */

type Status = 'idle' | 'loading' | 'success' | 'error'

const formRef = ref<FormInstance>()
/** el-button 是组件，模板 ref 拿到组件实例，需取其根节点 DOM */
const btnRef = ref<{ $el?: HTMLElement } | null>(null)
const shellRef = ref<InstanceType<typeof AuthShell> | null>(null)

const status = ref<Status>('idle')
const errMsg = ref('')

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  realName: '',
  phone: '',
})

const reduced =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/* ============================ 校验（与后端 RegisterDTO 规则一致） ============================ */

function validateAccount(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '').trim()
  if (!v) return cb(new Error('请输入账号'))
  if (v.length < 2 || v.length > 50) return cb(new Error('账号长度为 2 - 50 个字符'))
  cb()
}

/** 后端规则：^(?=.*[A-Za-z])(?=.*\d).{8,}$ —— 至少 8 位，同时包含字母与数字 */
function validatePassword(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '')
  if (!v) return cb(new Error('请输入密码'))
  if (v.length < 8 || !/[A-Za-z]/.test(v) || !/\d/.test(v)) {
    return cb(new Error('至少 8 位，且同时包含字母和数字'))
  }
  cb()
}

function validateConfirm(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '')
  if (!v) return cb(new Error('请再次输入密码'))
  if (v !== form.password) return cb(new Error('两次输入的密码不一致'))
  cb()
}

function validateRealName(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '').trim()
  if (!v) return cb(new Error('请输入姓名'))
  if (v.length > 50) return cb(new Error('姓名不能超过 50 个字符'))
  cb()
}

function validatePhone(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '').trim()
  if (v && !/^1\d{10}$/.test(v)) return cb(new Error('请输入 11 位手机号'))
  cb()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { validator: validateAccount, trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { validator: validateRealName, trigger: 'blur' },
  ],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
}

/* ============================ 提交 ============================ */

/** 从拦截器的各类 rejection（业务 response / AxiosError）中提取可读文案。 */
function pickError(err: unknown): string {
  const e = err as { data?: { message?: string }; response?: { data?: { message?: string } } } | null
  const payload = e?.response?.data ?? e?.data
  if (payload?.message) return payload.message
  if (payload) return '注册失败，请稍后重试'
  return '网络连接异常，请稍后重试'
}

/** 错误抖动：走 WAAPI，避免与入场动画的 CSS animation 相互重放。 */
function triggerShake() {
  const card = shellRef.value?.cardRef
  if (reduced || !card) return
  card.animate(
    [
      { transform: 'translateX(0)' },
      { transform: 'translateX(-8px)', offset: 0.2 },
      { transform: 'translateX(7px)', offset: 0.4 },
      { transform: 'translateX(-5px)', offset: 0.6 },
      { transform: 'translateX(3px)', offset: 0.8 },
      { transform: 'translateX(0)' },
    ],
    { duration: 460, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' },
  )
}

function clearError() {
  if (status.value === 'error') {
    status.value = 'idle'
    errMsg.value = ''
  }
}

watch(
  () => [form.username, form.password, form.confirm, form.realName, form.phone],
  clearError,
)

async function handleSubmit() {
  if (status.value === 'loading' || status.value === 'success') return
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  status.value = 'loading'
  errMsg.value = ''
  try {
    await register(
      {
        username: form.username.trim(),
        password: form.password,
        realName: form.realName.trim(),
        phone: form.phone.trim() || undefined,
      },
      true,
    )
    status.value = 'success'
    await new Promise((r) => window.setTimeout(r, 700))
    ElMessage.success('注册成功，请登录')
    // 携带账号回登录页预填
    await router.replace({ path: '/login', query: { username: form.username.trim() } })
  } catch (err) {
    status.value = 'error'
    errMsg.value = pickError(err)
    triggerShake()
  } finally {
    if (status.value === 'loading') status.value = 'idle'
  }
}

/* ============================ 次要入口 ============================ */

function onBackToLogin() {
  if (status.value === 'loading' || status.value === 'success') return
  router.push('/login')
}

/* ============================ 指针：按钮磁吸 ============================ */

const finePointer =
  typeof window !== 'undefined' && window.matchMedia('(hover: hover) and (pointer: fine)').matches

function btnEl(): HTMLElement | null {
  const v = btnRef.value
  if (!v) return null
  return v instanceof HTMLElement ? v : (v.$el ?? null)
}

/** 按钮磁吸：光标进入感应区后向光标轻微位移，离开时回弹。 */
function onBtnMove(e: MouseEvent) {
  if (!finePointer || reduced) return
  const el = btnEl()
  if (!el) return
  const r = el.getBoundingClientRect()
  const dx = (e.clientX - (r.left + r.width / 2)) / (r.width / 2)
  const dy = (e.clientY - (r.top + r.height / 2)) / (r.height / 2)
  el.style.transform = `translate3d(${(dx * 5).toFixed(2)}px, ${(dy * 4).toFixed(2)}px, 0)`
}

function onBtnLeave() {
  const el = btnEl()
  if (el) el.style.transform = ''
}
</script>

<template>
  <AuthShell ref="shellRef">
    <!-- ============ 玻璃卡片：注册表单（卡片框架 / 背景 / 品牌区由 AuthShell 提供） ============ -->
    <header class="card__head">
      <p class="card__index"><span>CREATE ACCOUNT</span><em>NEW MEMBER</em></p>
      <h2>创建账号</h2>
      <p class="card__sub">填写以下信息，注册你的仓储管理账号</p>
    </header>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      class="reg-form"
      status-icon
      @keyup.enter="handleSubmit"
    >
      <p class="fld__label" :style="{ '--d': '0.72s' }">账号</p>
      <el-form-item prop="username" :style="{ '--d': '0.78s' }">
        <el-input
          v-model="form.username"
          placeholder="2 - 50 个字符"
          autocomplete="username"
          :prefix-icon="User"
          clearable
        />
      </el-form-item>

      <p class="fld__label" :style="{ '--d': '0.82s' }">密码</p>
      <el-form-item prop="password" :style="{ '--d': '0.88s' }">
        <el-input
          v-model="form.password"
          placeholder="至少 8 位，含字母与数字"
          type="password"
          autocomplete="new-password"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>

      <p class="fld__label" :style="{ '--d': '0.92s' }">确认密码</p>
      <el-form-item prop="confirm" :style="{ '--d': '0.98s' }">
        <el-input
          v-model="form.confirm"
          placeholder="再次输入密码"
          type="password"
          autocomplete="new-password"
          :prefix-icon="Lock"
          show-password
          @keyup.enter="handleSubmit"
        />
      </el-form-item>

      <!-- 姓名 + 手机号 双列：压缩纵向占用，避免矮屏下卡片溢出 -->
      <div class="reg-form__grid">
        <div>
          <p class="fld__label" :style="{ '--d': '1.02s' }">姓名</p>
          <el-form-item prop="realName" :style="{ '--d': '1.08s' }">
            <el-input
              v-model="form.realName"
              placeholder="你的真实姓名"
              autocomplete="name"
              :prefix-icon="UserFilled"
              clearable
            />
          </el-form-item>
        </div>
        <div>
          <p class="fld__label" :style="{ '--d': '1.06s' }">手机号（选填）</p>
          <el-form-item prop="phone" :style="{ '--d': '1.12s' }">
            <el-input
              v-model="form.phone"
              placeholder="11 位手机号"
              autocomplete="tel"
              :prefix-icon="Phone"
              clearable
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
        </div>
      </div>

      <p class="alert" :class="{ show: status === 'error' }" role="alert" :style="{ '--d': '1.3s' }">
        {{ errMsg }}
      </p>

      <el-button
        ref="btnRef"
        class="auth-btn"
        type="primary"
        size="large"
        :style="{ '--d': '1.36s' }"
        :disabled="status === 'loading'"
        :aria-busy="status === 'loading'"
        @click="handleSubmit"
        @mousemove="onBtnMove"
        @mouseleave="onBtnLeave"
      >
        <span v-if="status === 'loading'" class="auth-btn__state">
          <span class="spin" aria-hidden="true"></span>CREATING…
        </span>
        <span v-else-if="status === 'success'" class="auth-btn__state">
          <el-icon><Check /></el-icon>ACCOUNT CREATED
        </span>
        <span v-else class="auth-btn__state">
          CREATE ACCOUNT
          <el-icon class="auth-btn__arrow"><ArrowRight /></el-icon>
        </span>
      </el-button>
    </el-form>

    <footer class="card__foot">
      <span>已有账号？</span>
      <a href="#" class="link" @click.prevent="onBackToLogin">返回登录</a>
    </footer>
  </AuthShell>
</template>

<style scoped lang="scss">
/* 注册页专属样式：共享视觉体系见 AuthShell（.auth-page 命名空间） */

.reg-form {
  :deep(.el-form-item) {
    margin-bottom: var(--sp-4);
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0 12px;

    /* 极窄屏时回到单列，保证输入区可用宽度 */
    @media (max-width: 400px) {
      grid-template-columns: 1fr;
      gap: 0;
    }
  }
}

/* 矮屏桌面：收紧纵向节奏，保证整卡在 100dvh 内完整可见 */
@media (max-height: 840px) and (min-width: 1025px) {
  .auth-page .card__head {
    margin-bottom: var(--sp-4);
  }

  .reg-form {
    :deep(.el-form-item) {
      margin-bottom: var(--sp-3);
    }
  }

  .auth-page .card__foot {
    margin-top: var(--sp-4);
    padding-top: var(--sp-4);
  }
}
</style>
