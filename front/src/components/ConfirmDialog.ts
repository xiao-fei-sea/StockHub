import { ElMessageBox, ElMessage } from 'element-plus'

/** 统一确认对话框参数（封装 ElMessageBox，交互设计 §3）。 */
export interface ConfirmOptions {
  /** 标题，默认「操作确认」 */
  title?: string
  /** 确认正文（支持多行说明） */
  message: string
  /** 确认按钮文案，默认「确认」 */
  confirmText?: string
  cancelText?: string
  /** warning=常规确认；danger=高危（红色主按钮） */
  type?: 'warning' | 'danger' | 'info'
  /**
   * 高危操作需人工输入指定文本（如过账输入单据编号）。
   * 输入不匹配时提示并返回 false（视为取消，不调用接口）。
   */
  requireInput?: {
    /** 需要用户逐字输入的文本 */
    text: string
    /** 输入框提示语 */
    placeholder?: string
  }
}

/**
 * 统一确认弹窗，返回是否确认：
 * - 通过/取消 → Promise<boolean>
 * - 输入校验不通过 → 提示并返回 false
 */
export function confirmDialog(options: ConfirmOptions): Promise<boolean> {
  const {
    title = '操作确认',
    message,
    confirmText = '确认',
    cancelText = '取消',
    type = 'warning',
    requireInput,
  } = options

  const confirmButtonType = type === 'danger' ? 'danger' : 'primary'

  if (!requireInput) {
    return ElMessageBox.confirm(message, title, {
      confirmButtonText: confirmText,
      cancelButtonText: cancelText,
      confirmButtonClass: confirmButtonType,
      type: type === 'danger' ? 'error' : 'warning',
      autofocus: false,
      customStyle: { borderRadius: '8px' },
    })
      .then(() => true)
      .catch(() => false)
  }

  return ElMessageBox.prompt(message, title, {
    confirmButtonText: confirmText,
    cancelButtonText: cancelText,
    confirmButtonClass: confirmButtonType,
    inputPlaceholder: requireInput.placeholder ?? `请输入 ${requireInput.text} 以确认`,
    inputValidator: (value: string) => {
      if (value !== requireInput.text) return `输入不正确，应为「${requireInput.text}」`
      return true
    },
    autofocus: false,
    customStyle: { borderRadius: '8px' },
  })
    .then(() => true)
    .catch((action: unknown) => {
      // inputValidator 返回错误串时走 catch（message 已由 ElMessageBox 展示）
      if (typeof action === 'string' && action !== 'cancel' && action !== 'close') {
        ElMessage.warning(action)
      }
      return false
    })
}

export default confirmDialog
