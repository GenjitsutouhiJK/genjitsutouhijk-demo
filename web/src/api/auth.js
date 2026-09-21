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

/**
 * 注册
 *
 * 后端用的是同一个请求体结构（用户名 + 密码），返回的也是同一套凭证，
 * 所以这个函数的写法和 login 几乎一模一样 —— 这不是重复，是接口设计得规整。
 *
 * 后端的校验规则（RegisterRequest 上那两行注解）：
 *   用户名：非空，长度 3 ~ 20
 *   密码  ：非空，长度 6 ~ 32
 * 前端 RegisterView 里会先按同一套规则自检一遍，好处是用户不用等一个网络往返
 * 就能看到提示；但后端那份校验**不能省**，因为请求可以被绕过前端直接发。
 *
 * @param {{ username: string, password: string }} payload
 * @returns {Promise<{ code: number, message: string, data: object }>}
 */
export function register(payload) {
  return request('/api/auth/register', {
    method: 'POST',
    data: payload,
  })
}
