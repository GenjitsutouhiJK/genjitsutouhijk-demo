import { request } from '../utils/request'
import type { ApiResponse, Credentials, LoginResponse } from '../types/api'

/**
 * 登录
 *
 * 返回值是**整个响应体**而不是只把 data 解出来，这是刻意的：
 * 失败时后端会带一句可以直接给用户看的 message，
 * 如果这里只返回 data，调用方就拿不到那句话了。
 * 所以调用方统一写成 `if (json.code === 0) { 用 json.data } else { 显示 json.message }`。
 */
export function login(payload: Credentials): Promise<ApiResponse<LoginResponse>> {
  return request<LoginResponse>('/api/auth/login', {
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
 * 注意返回类型和 login 完全相同 —— 注册成功即登录，后端直接签发凭证，
 * 省掉了"注册完再去调一次登录"的往返。前端的处理也就能复用同一条分支。
 */
export function register(payload: Credentials): Promise<ApiResponse<LoginResponse>> {
  return request<LoginResponse>('/api/auth/register', {
    method: 'POST',
    data: payload,
  })
}
