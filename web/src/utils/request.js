/**
 * 统一的请求封装（基于 fetch）
 *
 * 负责四件事，避免每个接口都重复写一遍：
 *   1. 拼接后端基础地址（改地址只改这一处）
 *   2. 统一 JSON 序列化 / 反序列化
 *   3. ★ 自动带上登录凭证（里程碑 ③ 新增）
 *   4. ★ 发现凭证失效时自动清理会话并回登录页（里程碑 ③ 新增）
 */
import { session, clearSession } from './session'

/** 后端基础地址 */
export const BASE_URL = 'http://localhost:8080'

/**
 * 后端判定"这张票不能用了"的错误码
 *
 * 和后端 exception/ErrorCode.java 里的值是**一一对应**的：
 *   1005 UNAUTHORIZED    —— 根本没带凭证
 *   1006 TOKEN_EXPIRED   —— 带了，但过期了
 *   1007 TOKEN_INVALID   —— 带了，但是伪造的 / 改过的
 *
 * 三个码对前端的处理动作是一样的（都是"重新登录"），所以这里合成一组。
 * 如果哪天想区分（比如"过期"就静默刷新、"无效"就弹个提示），再拆开即可。
 *
 * ⚠️ 这套数字是前后端之间的**契约**。后端改了码，这里必须同步改 ——
 *    这是没有类型系统保护的那类改动，最容易漏。
 */
const SESSION_LOST_CODES = [1005, 1006, 1007]

/**
 * 每个码对应一句要带给用户的话（会显示在登录页上）。
 *
 * 为什么 1005 故意留空？它表示"请求里根本没带凭证"，
 * 通常发生在未登录状态下的一次正常跳转，安静处理就好；
 * 这时候弹一句"请先登录"只会让人莫名其妙。
 */
const SESSION_LOST_NOTICES = {
  1006: '登录已过期，请重新登录',
  1007: '登录状态异常，请重新登录',
}

/**
 * 会话失效时的处理函数
 *
 * 为什么不在这里直接 import router 然后 router.push('/login')？
 *   因为会形成**循环依赖**：
 *     request.js → router/index.js → views/LoginView.vue → api/auth.js → request.js
 *   循环依赖在 ESM 里不一定会报错（取决于谁先被求值），
 *   但一旦出问题，表现是"某个变量莫名其妙是 undefined"，非常难查。
 *
 * 换个思路就干净了：这里只负责**宣布**"会话没了"，
 * 至于怎么跳转（用 router 还是 location.href），由入口文件决定。
 * 这叫"依赖倒置"——底层不反过来依赖上层，而是上层把动作注册进来。
 * 注册的动作在 src/main.js 里完成。
 */
let unauthorizedHandler = null

/**
 * 注册"会话失效"的回调。由 main.js 调用，业务代码不需要关心。
 *
 * 回调会收到一个字符串参数：要展示给用户的失效原因（没有原因时是空字符串）。
 * 这样"怎么跳转"和"怎么记提示"两件事都留在上层，这一层只负责判定和宣布。
 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

/**
 * 发起一次请求
 *
 * @param {string} path  接口路径，例如 '/api/auth/login'
 * @param {object} [options]
 * @param {string} [options.method='GET'] 请求方法
 * @param {object} [options.data]        请求体数据，会被 JSON 序列化
 * @param {object} [options.headers]     额外请求头（会覆盖同名的默认头）
 * @returns {Promise<any>} 解析后的响应体
 */
export async function request(path, { method = 'GET', data, headers } = {}) {
  // 从会话里取凭证。未登录时是 undefined，那就干脆不带这个头 ——
  // 拼出一个 "Bearer undefined" 发过去，只会让后端多判一次"凭证无效"。
  const token = session.value?.accessToken

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      // ★ 这就是 JWT 认证最关键的一步。
      //   JWT 是"无状态"的 —— 服务端不记谁登录过，全靠每个请求自带凭证。
      //   所以这个头一旦漏掉，后端就认为你是匿名用户，受保护接口一律 401。
      //   放在这个统一的封装里，业务代码就再也不会忘记加它。
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: data === undefined ? undefined : JSON.stringify(data),
  })

  // 与原先的写法保持一致：不判断 HTTP 状态码，一律按 JSON 解析，
  // 业务上的成功/失败由调用方根据返回体里的 code 判断。
  //
  // 里程碑 ③ 之后，认证失败是 HTTP 401 —— 但我们照样只解析 body。
  // 之所以可以这样，是因为后端特意定制了 401 的响应体，
  // 让它也是 { code, message, data } 这个统一格式（见 RestAuthenticationEntryPoint）。
  // 换句话说：HTTP 状态码让网关和浏览器调试工具看得懂，
  // body 里的 code 让前端代码看得懂，两者各司其职、互不干扰。
  let json
  try {
    json = await response.json()
  } catch {
    // 后端保证**连失败也**返回 { code, message, data } 这个统一格式
    // （401 是靠 RestAuthenticationEntryPoint 定制的），所以正常情况下一定能解析出来。
    // 解析不了说明拿到的根本不是我们的接口 —— 比如被反向代理/网关截住，
    // 返回了一个 HTML 错误页。这时候报"解析失败"比报"连不上"更接近事实，
    // 否则会把人往"后端是不是没启动"的方向带偏（这正是这类问题最难查的地方）。
    throw new Error(`后端返回的不是 JSON（HTTP ${response.status}），请求可能没走到我们的接口`)
  }

  if (SESSION_LOST_CODES.includes(json.code)) {
    // 先清本地会话，再通知上层跳转。
    // 顺序很重要：如果先跳转后清理，登录页的守卫会发现"还登录着"，
    // 又把你送回主页，来回弹跳。
    clearSession()
    // 把"为什么失效"一起传出去，最终会显示在登录页上。
    // 不带这句话的话，用户只会看到自己莫名其妙回到了登录页。
    unauthorizedHandler?.(SESSION_LOST_NOTICES[json.code] ?? '')
  }

  return json
}
