import { request } from '../utils/request'
import type { ApiResponse, UserProfile } from '../types/api'

/**
 * 取当前登录用户的资料
 *
 * 这是里程碑 ③ 引入的第一个**受保护接口** —— 也就是说，不带有效 token 调它，
 * 后端会返回 401 + code 1005/1006/1007。
 *
 * 调用它不需要在这里手动加 token：utils/request.ts 会自动从会话里取出来放进
 * Authorization 头。这正是把请求逻辑统一封装的收益 ——
 * 否则每加一个受保护接口，都要记得复制一行拼 header 的代码，迟早会漏。
 *
 * 返回的 data 类型是 UserProfile（对应后端 dto/UserProfile.java）。
 * ⚠️ 它是后端专门为"给前端看"准备的 DTO，不是数据库实体 ——
 *   实体里有 passwordHash，直接返回会把密码哈希发到浏览器。
 *   所以以后要加字段，是去改那个 DTO，而不是让接口返回实体。
 */
export function getMe(): Promise<ApiResponse<UserProfile>> {
  return request<UserProfile>('/api/user/me')
}
