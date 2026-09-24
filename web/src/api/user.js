import { request } from '../utils/request'

/**
 * 取当前登录用户的资料
 *
 * 这是里程碑 ③ 引入的第一个**受保护接口** —— 也就是说，不带有效 token 调它，
 * 后端会返回 401 + code 1005/1006/1007。
 *
 * 调用它不需要在这里手动加 token：utils/request.js 会自动从会话里取出来放进
 * Authorization 头。这正是把请求逻辑统一封装的收益 ——
 * 否则每加一个受保护接口，都要记得复制一行拼 header 的代码，迟早会漏。
 *
 * @returns {Promise<{ code: number, message: string, data?: { id: number, username: string, createdAt: string } }>}
 */
export function getMe() {
  return request('/api/user/me')
}
