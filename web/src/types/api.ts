/**
 * 前后端契约的类型镜像
 *
 * ⚠️ 先说清楚这个文件的性质：**它是手写的**。
 *
 *   这些类型不会因为后端改了而自动更新 —— 后端加一个字段，TypeScript 一点都不知道。
 *   真正能拦住"前后端不一致"的，是 server 那边那 46 个用例，
 *   加上"改后端 DTO 时记得同步这里"这条纪律。
 *
 *   那它到底买到了什么？买到的是**改动时的即时反馈**：
 *   以前只能靠"我记得 LoginView 里取的是 json.data.accessToken"这种记忆，
 *   现在写错字段名、或者给 setSession 传了少一个字段的对象，编辑器立刻标红。
 *   类型系统管不了运行时，但它能把"记错字段名"这类低级错误挡在提交之前。
 *
 * 各个类型都在注释里标了它对应的后端文件，方便两边对着看。
 */

// ------------------------------------------------------------------
// 统一响应体
// ------------------------------------------------------------------

/**
 * 后端 `dto/ApiResponse.java` 的镜像。
 *
 * 前端与后端的约定：**只认 code，不认 HTTP 状态码**。
 *   code === 0 → 成功，data 是业务数据
 *   code !== 0 → 失败，message 可以直接显示给用户
 *
 * ⚠️ `data` 为什么是 `T | null` 而不是 `T`？
 *   看 ApiResponse.java 里的 `failure(...)`：失败时它**固定返回 null**，
 *   这不是"可能不返回"，是"确定会给一个 null"。
 *   写成 `T` 就是在撒谎，而且这个谎会一直传到调用方 ——
 *   所以这里老老实实带上 `| null`，让每个取 data 的地方都必须先判 code。
 *
 *
 * ⚠️ 那能不能写成"可辨识联合"，让判过 code 之后自动收窄掉 null？
 *   **试过了，不行。** 想法是这样：
 *       | { code: 0;      message: string; data: T    }
 *       | { code: number; message: string; data: null }
 *   但 `code === 0` 收窄时两个分支都满足 —— 失败分支的 code 类型是 number，
 *   而 number 包含 0，编译器没法把它排除掉，于是 data 仍然停在 `T | null`。
 *   要真排除掉，就得把失败码写成字面量联合（1001 | 1002 | … | 9999），
 *   那样后端每加一个错误码，前端都得跟着改 —— 比每次判 null 更糟。
 *
 *   所以结论：**调用方必须同时判 `code === 0` 和 `data !== null`**。
 *   这不是额外负担，它恰好点出了一个真实约束 —— "code 说成功，也可能没带数据"。
 *   （LoginView / RegisterView 的成功分支就是这么写的。）
 */
export interface ApiResponse<T = unknown> {
  /** 0 表示成功；非 0 是下面 ErrorCode 里列的业务错误码 */
  code: number
  /** 给用户看的话，失败时直接显示即可 */
  message: string
  /** 成功时是业务数据；失败时后端固定给 null */
  data: T | null
}

// ------------------------------------------------------------------
// 业务错误码
// ------------------------------------------------------------------

/**
 * 后端 `exception/ErrorCode.java` 的镜像。
 *
 * 这些数字是**前后端之间的硬契约**：后端改了码，这里必须同步改。
 * 好在两端各只有一处定义（后端枚举 / 这个常量表），对照着改不容易漏。
 *
 * 0 不在这里 —— 成功由 ApiResponse.success() 负责，不是"错误码"。
 */
export const ErrorCode = {
  LOGIN_FAILED: 1001,
  INVALID_PARAMETER: 1002,
  MALFORMED_BODY: 1003,
  USERNAME_TAKEN: 1004,
  UNAUTHORIZED: 1005,
  TOKEN_EXPIRED: 1006,
  TOKEN_INVALID: 1007,
  INTERNAL_ERROR: 9999,
} as const

/** 上面那张表里所有值的联合类型：1001 | 1002 | ... | 9999 */
export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

// ------------------------------------------------------------------
// 认证相关
// ------------------------------------------------------------------

/**
 * 登录 / 注册的请求体。
 * 对应后端 `dto/LoginRequest.java` 和 `dto/RegisterRequest.java`（两者结构一样）。
 *
 * 后端的校验规则（那两个类上的注解）：
 *   用户名：非空，长度 3 ~ 20
 *   密码  ：非空，长度 6 ~ 32
 * 前端表单里按同一套规则先自检一遍，避免让用户白等一个网络往返；
 * 但后端那层校验不会因此省掉 —— 请求可以被绕过前端直接发。
 */
export interface Credentials {
  username: string
  password: string
}

/**
 * 登录接口返回的凭证。对应后端 `dto/LoginResponse.java`。
 *
 * 注册接口返回的也是它 —— 后端设计成"注册完直接算登录"，
 * 省掉了一次"注册成功后再去调登录"的往返。
 */
export interface LoginResponse {
  /** JWT 本身 */
  accessToken: string
  /** 固定是 "Bearer"，配合 Authorization 头使用 */
  tokenType: string
  /** 有效期，单位**秒**（后端字段名就叫 expiresIn，语义是"还有多少秒过期"） */
  expiresIn: number
  username: string
}

/**
 * 当前登录用户的资料。对应后端 `dto/UserProfile.java`。
 *
 * ⚠️ 后端**故意**不直接返回 User 实体 —— 那个实体里有个 passwordHash 字段，
 *   直接返回会把密码哈希发到浏览器。这个 DTO 只挑该给前端看的字段。
 *   所以以后想加字段，记得是改 UserProfile，而不是让接口去返回实体。
 */
export interface UserProfile {
  id: number
  username: string
  /**
   * 后端是 `LocalDateTime`，序列化成 ISO-8601 字符串
   * （例如 "2026-09-29T09:03:12.845"）。
   * 想格式化显示的话，用 Intl / Date 自己转，别假设它已经是给人看的样子。
   */
  createdAt: string
}

// ------------------------------------------------------------------
// JWT
// ------------------------------------------------------------------

/**
 * JWT 的 payload（三段里的中间那段，Base64URL 解码出来的 JSON）。
 *
 * ⚠️ 这里字段全部可选，是有意的：
 *   前端**不能假设**这些 claim 一定存在 —— 票可能是别的系统签的、可能是坏数据。
 *   所以每个读它的地方都要先判存在性（见 session.ts 的 isTokenExpired）。
 *
 * ⚠️ 另外提醒一句：payload 只是 Base64URL **编码**，不是加密。
 *   谁都能解开看内容，所以里面不能放密码这类敏感数据。
 *   它能防的只有"篡改" —— 内容改过签名就对不上，服务端会拒绝。
 */
export interface JwtPayload {
  /** 签发时间，Unix 秒 */
  iat?: number
  /** 过期时间，Unix 秒 */
  exp?: number
  /** 用户名，后端 JwtService 把用户名写在这个标准字段里 */
  sub?: string
  /** 留个口子：后端以后往 payload 里加字段时，前端不必跟着改这个类型 */
  [claim: string]: unknown
}
