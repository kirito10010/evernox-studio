import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, AuthResponse } from '@/types/user'
import { login as loginApi, register as registerApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import type { LoginRequest, RegisterRequest } from '@/types/user'
import { ElMessage } from 'element-plus'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshTokenValue = ref<string | null>(localStorage.getItem('refreshToken'))
  const userInfo = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'admin')
  const isSuperMember = computed(() => userInfo.value?.role === 'super_member')
  const userRole = computed(() => userInfo.value?.role || 'member')

  const setToken = (auth: AuthResponse) => {
    token.value = auth.accessToken
    refreshTokenValue.value = auth.refreshToken
    localStorage.setItem('accessToken', auth.accessToken)
    localStorage.setItem('refreshToken', auth.refreshToken)
  }

  const clearToken = () => {
    token.value = null
    refreshTokenValue.value = null
    userInfo.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }

  const login = async (loginData: LoginRequest) => {
    try {
      const res = await loginApi(loginData)
      setToken(res.data)
      // 登录成功后获取用户信息
      await fetchUserInfo()
      ElMessage.success('登录成功')
      return true
    } catch (error) {
      return false
    }
  }

  const register = async (registerData: RegisterRequest) => {
    try {
      await registerApi(registerData)
      ElMessage.success('注册成功，请登录')
      return true
    } catch (error) {
      return false
    }
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      // ignore
    } finally {
      clearToken()
      ElMessage.success('已退出登录')
      // 强制刷新页面跳转到登录页
      window.location.href = '/login'
    }
  }

  const fetchUserInfo = async () => {
    try {
      const res = await getUserInfo()
      if (res.data) {
        userInfo.value = {
          id: res.data.id,
          username: res.data.username,
          email: res.data.email,
          role: res.data.role,
          status: res.data.status,
          points: res.data.points,
          createdAt: res.data.createdAt,
          lastLoginAt: res.data.lastLoginAt,
        } as User
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
    }
  }

  return {
    token,
    refreshToken: refreshTokenValue,
    userInfo,
    isLoggedIn,
    isAdmin,
    isSuperMember,
    userRole,
    setToken,
    clearToken,
    login,
    register,
    logout,
    fetchUserInfo,
  }
})
