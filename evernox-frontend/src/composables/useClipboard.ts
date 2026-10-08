import { ElMessage } from 'element-plus'

/**
 * 复制文本到剪贴板。
 *
 * 为什么不能只用 navigator.clipboard：生产环境 nginx 以 http 提供服务，
 * 非安全上下文下 `navigator.clipboard` 为 undefined，直接调用会抛 TypeError
 * （读取 undefined 的属性），复制会静默失败。因此必须先判断 isSecureContext，
 * 不满足时降级到 execCommand('copy')。
 */
export function useClipboard() {
  /**
   * @param text           要复制的文本
   * @param successMessage 成功提示文案；传 '' 则不提示
   * @returns 是否复制成功
   */
  const copy = async (text: string, successMessage = '已复制'): Promise<boolean> => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text)
      } else {
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.setAttribute('readonly', '')
        // fixed + 透明：避免插入元素时页面滚动跳动，也避免闪现
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
      }
      if (successMessage) ElMessage.success(successMessage)
      return true
    } catch {
      ElMessage.warning('复制失败，请手动复制')
      return false
    }
  }

  return { copy }
}
