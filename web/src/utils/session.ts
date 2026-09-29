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
const NOTICE_KEY = 'genjitsutouhijk.notice'

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

// ------------------------------------------------------------------
// 在本地读出 JWT 的过期时间
// ------------------------------------------------------------------

/**
 * 把 JWT 中间那一段（payload）解出来。
 *
 * ★ 先说清楚一件事：JWT 的 header 和 payload 只是 Base64URL **编码**，不是加密。
 *   谁都能解开看到里面写了什么 —— 所以 payload 里**不能放密码、身份证号**这类东西。
 *   它能防的只有"篡改"：内容被改过，签名就对不上，服务端会拒绝。
 *
 * 解不出来（不是 JWT、格式坏了）就返回 null，由调用方决定怎么处理。
 */
export function readTokenPayload(token) {
  if (typeof token !== 'string') return null

  const parts = token.split('.')
  if (parts.length !== 3) return null // 真 JWT 一定是用两个点分成三段

  try {
    // Base64URL 是给 URL 用的变体，把 +/ 换成了 -_，atob 不认，先换回标准字符。
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    // 再补回被去掉的 = 填充：长度不是 4 的倍数时 atob 会直接抛错。
    base64 += '='.repeat((4 - (base64.length % 4)) % 4)

    // atob 得到的是"一个字符一个字节"的二进制字符串，
    // 而 payload 里的中文是多个字节的 UTF-8，照原样显示会乱码。
    // 所以先转成 %XX 形式喂给 decodeURIComponent，才能还原成真正的字符。
    const binary = atob(base64)
    const json = decodeURIComponent(
      Array.from(binary, (ch) => `%${ch.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''),
    )

    return JSON.parse(json)
  } catch {
    return null
  }
}

/**
 * 这张票在**本地**看是否已经过期。
 *
 * ⚠️ 前端读 exp 不是安全边界，只是体验优化，两者分工是这样的：
 *   本地能判 —— "放太久了、已经过期"。覆盖绝大多数真实场景，
 *               不发任何请求、同步完成、0 毫秒，所以可以在路由守卫里用；
 *   只能服务端判 —— 签名被篡改、用户被删、密钥轮换、将来的黑名单。
 *               这些本地永远看不出来，只能靠后端那次核验兜底。
 *   所以："能不能访问"永远由后端说了算；本地检查只是提前拦掉"反正也会失败"的那些。
 *
 * 读不出 exp（不是 JWT、或者 payload 里没写 exp）时返回 false —— 不下结论，交给服务端。
 * 宁可多跑一次核验，也不要因为解析意外，把好端端登录着的人踢去登录页。
 */
export function isTokenExpired() {
  const payload = readTokenPayload(session.value?.accessToken)
  if (!payload || typeof payload.exp !== 'number') return false
  return payload.exp * 1000 <= Date.now()
}

// ------------------------------------------------------------------
// 跨页面的一次性提示
// ------------------------------------------------------------------

/**
 * 记一句"要给用户看的话"，比如"登录已过期，请重新登录"。
 *
 * 为什么需要它：会话失效发生在主页，但人马上会被送去登录页 ——
 * 如果不把原因一起带过去，用户的体感就是"我正看着面板，突然就回到登录页了"，
 * 像出了 bug，而且不知道该怎么办。
 *
 * 存 sessionStorage 而不是放在内存变量里：跳转过程中页面可能会换，
 * 内存变量不一定活得下来。存下来更稳，也顺手得到"刷一下提示就没了"这个行为。
 */
export function setNotice(message) {
  if (!message) return
  sessionStorage.setItem(NOTICE_KEY, message)
}

/**
 * 取出那条提示，并**顺手清掉** —— 它只会被读出来显示一次。
 *
 * 这个"读一次就没了"是刻意的：提示描述的是"刚刚发生了什么"。
 * 用户下次自己点进登录页时它已经过时了，一直挂在页面上会让人以为又过期了一次。
 */
export function takeNotice() {
  const message = sessionStorage.getItem(NOTICE_KEY)
  if (message) sessionStorage.removeItem(NOTICE_KEY)
  return message ?? ''
}
