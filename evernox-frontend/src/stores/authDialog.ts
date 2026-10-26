import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useUserStore } from './user'

export type AuthMode = 'login' | 'register' | 'forgot'

interface OpenOptions {
  /** 登录成功后要继续执行的操作 */
  afterLogin?: () => void
  /** 登录成功后要跳转的路由（默认留在当前页） */
  redirect?: string
}

export const useAuthDialogStore = defineStore('authDialog', () => {
  const visible = ref(false)
  const mode = ref<AuthMode>('login')
  const pendingAction = ref<(() => void) | null>(null)
  const pendingRedirect = ref<string | null>(null)
  const prefilledUsername = ref('')

  function open(nextMode: AuthMode = 'login', opts: OpenOptions = {}) {
    mode.value = nextMode
    pendingAction.value = opts.afterLogin ?? null
    pendingRedirect.value = opts.redirect ?? null
    if (nextMode !== 'login') {
      prefilledUsername.value = ''
    }
    visible.value = true
  }

  function close() {
    visible.value = false
    pendingAction.value = null
    pendingRedirect.value = null
    prefilledUsername.value = ''
  }

  function switchMode(nextMode: AuthMode) {
    mode.value = nextMode
  }

  /**
   * 已登录：立即执行 action 并返回 true
   * 未登录：记录 action、打开登录框、返回 false
   */
  function requireAuth(action?: () => void, redirect?: string): boolean {
    const userStore = useUserStore()
    if (userStore.isLoggedIn) {
      action?.()
      return true
    }
    open('login', { afterLogin: action, redirect })
    return false
  }

  /** 取出并清空待执行操作（登录成功后由弹窗调用） */
  function consumePending() {
    const action = pendingAction.value
    const redirect = pendingRedirect.value
    close()
    return { action, redirect }
  }

  function setPrefilledUsername(username: string) {
    prefilledUsername.value = username
  }

  return {
    visible,
    mode,
    prefilledUsername,
    open,
    close,
    switchMode,
    requireAuth,
    consumePending,
    setPrefilledUsername,
  }
})
