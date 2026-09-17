import { request } from '../utils/request'

/**
 * 登录
 *
 * @param {{ username: string, password: string }} payload
 * @returns {Promise<{ code: number, message: string }>}
 */
export function login(payload) {
  return request('/api/auth/login', {
    method: 'POST',
    data: payload,
  })
}
