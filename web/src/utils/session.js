/**
 * 会话状态（当前登录用户）
 *
 * 为什么单独抽一个文件？
 *   登录页拿到 token 之后，主页也要用这些数据。如果把变量写在 LoginView 里面，
 *   组件一销毁数据就没了。所以把它提到组件外面，做成"跨页面共享的一份状态"。
 *
 * 这里用最朴素的方式实现：
 *   - ref()         让数据变成响应式的，谁用它，数据一变谁就自动更新
 *   - sessionStorage 顺手存一份，刷新页面（F5）也不会丢，关掉标签页就清空
 *
 * 以后项目长大了可以换成 Pinia（Vue 官方的状态管理库），
 * 但核心概念就是这一份 —— "组件外面的一份共享数据"，只是写法更规范。
 */
import { ref } from 'vue'

const STORAGE_KEY = 'genjitsutouhijk.session'

/** 从 sessionStorage 读回上次的会话；读不到、或者内容坏了，就返回 null */
function readFromStorage() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY)) ?? null
  } catch {
    return null
  }
}

/** 当前会话，null 表示未登录 */
export const session = ref(readFromStorage())

/**
 * 登录成功后写入会话
 *
 * @param {{ accessToken: string, tokenType: string, expiresIn: number, username: string }} data
 *        直接就是后端 /api/auth/login 返回体里的 data 字段
 */
export function setSession(data) {
  const value = { ...data, loggedInAt: Date.now() }
  session.value = value
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value))
}

/** 退出登录：清空会话 */
export function clearSession() {
  session.value = null
  sessionStorage.removeItem(STORAGE_KEY)
}

/** 是否已登录，路由守卫里用得到 */
export function isLoggedIn() {
  return session.value !== null
}
