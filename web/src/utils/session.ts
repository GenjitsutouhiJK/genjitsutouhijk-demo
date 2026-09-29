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
import type { JwtPayload, LoginResponse } from '../types/api'

const STORAGE_KEY = 'genjitsutouhijk.session'
const NOTICE_KEY = 'genjitsutouhijk.notice'

/**
 * 一条会话 = 后端给的凭证 + 前端自己记的一笔时间
 */
export interface Session extends LoginResponse {
  /**
   * 本地写入时刻（毫秒时间戳）
   *
   * 为什么需要它：后端只告诉我们"这张票还有 expiresIn 秒"，
   * 但刷新页面之后，我们已经不知道那个秒数是从哪一刻开始算的。
   * 记下写入时刻，才能算出"现在还剩多少"（主页那条进度条就是靠它）。
   */
  loggedInAt: number
}

/** 从 sessionStorage 读回上次的会话；读不到、或者内容坏了，就返回 null */
function readFromStorage(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    // ⚠️ 这里必须显式判 null，不能直接把 getItem 的结果丢给 JSON.parse。
    //   getItem 的返回类型是 `string | null`，而 JSON.parse 只收 string ——
    //   不判的话这一行会直接编译不过。
    //   （其实不判也能跑：JSON.parse(null) 会把参数转成字符串 "null" 再解析成 null。
    //     但那属于"靠类型转换的巧合在工作"，写明白更好。）
    if (raw === null) return null
    return JSON.parse(raw) as Session
  } catch {
    return null
  }
}

/**
 * 当前会话，null 表示未登录
 *
 * `ref<Session | null>` 里的类型参数是**必须写**的：
 * 写成 `ref(readFromStorage())` 也能跑，但 TS 会按调用结果反推，
 * 而 readFromStorage() 的返回类型是 Session | null —— 这个推断本身没错，
 * 显式写出来是为了让"这里可能为空"这件事在声明处就一眼可见。
 */
export const session = ref<Session | null>(readFromStorage())

/**
 * 登录成功后写入会话
 *
 * @param data 直接就是后端 /api/auth/login（或 /register）返回体里的 data 字段
 */
export function setSession(data: LoginResponse): void {
  const value: Session = { ...data, loggedInAt: Date.now() }
  session.value = value
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value))
}

/** 退出登录：清空会话 */
export function clearSession(): void {
  session.value = null
  sessionStorage.removeItem(STORAGE_KEY)
}

/** 是否已登录，路由守卫里用得到 */
export function isLoggedIn(): boolean {
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
 * 参数类型写成 `unknown` 而不是 `string`，是有意的：
 *   调用方手里的 token 来自会话（可能为空）或别处，本来就是"什么都可能有"。
 *   收成 string 会逼调用方先断言，那等于把"这里可能不是字符串"这个事实藏起来。
 *   用 unknown 则强制这一层自己去判（下面第一行就是），责任落在该落的地方。
 *
 * 解不出来（不是 JWT、格式坏了）就返回 null，由调用方决定怎么处理。
 */
export function readTokenPayload(token: unknown): JwtPayload | null {
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

    // JSON.parse 的结果是 any，这里断言成 JwtPayload。
    // 断言在这里是安全的：后面每个读 claim 的地方都还得自己判存在性
    // （见下面的 isTokenExpired），断言只是标出"这段 JSON 我们希望长这样"。
    return JSON.parse(json) as JwtPayload
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
 *
 * 注意中间那句 `typeof payload.exp !== 'number'`：
 *   JwtPayload 里 exp 是可选的（后端理论上可以不写），
 *   所以 TS 会强制我们在这里收窄一次 —— **这正是我们想要它管的地方**。
 *   这一步以前只写在注释里，现在编译器会一直盯着。
 */
export function isTokenExpired(): boolean {
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
export function setNotice(message: string): void {
  if (!message) return
  sessionStorage.setItem(NOTICE_KEY, message)
}

/**
 * 取出那条提示，并**顺手清掉** —— 它只会被读出来显示一次。
 *
 * 这个"读一次就没了"是刻意的：提示描述的是"刚刚发生了什么"。
 * 用户下次自己点进登录页时它已经过时了，一直挂在页面上会让人以为又过期了一次。
 *
 * 返回类型是 string 而不是 string | null：读不到时给空字符串，
 * 调用方（LoginView）就不用为了"可能为空"多写一层判断 —— 空字符串在模板里天然是假值。
 */
export function takeNotice(): string {
  const message = sessionStorage.getItem(NOTICE_KEY)
  if (message) sessionStorage.removeItem(NOTICE_KEY)
  return message ?? ''
}
