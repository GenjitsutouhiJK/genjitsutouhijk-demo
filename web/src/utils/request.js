/**
 * 统一的请求封装（基于 fetch）
 *
 * 负责三件事，避免每个接口都重复写一遍：
 *   1. 拼接后端基础地址（改地址只改这一处）
 *   2. 统一 JSON 序列化 / 反序列化
 *   3. 网络异常或响应不是合法 JSON 时抛错，交给调用方兜底
 */

/** 后端基础地址 */
export const BASE_URL = 'http://localhost:8080'

/**
 * 发起一次请求
 *
 * @param {string} path  接口路径，例如 '/api/auth/login'
 * @param {object} [options]
 * @param {string} [options.method='GET'] 请求方法
 * @param {object} [options.data]        请求体数据，会被 JSON 序列化
 * @param {object} [options.headers]     额外请求头
 * @returns {Promise<any>} 解析后的响应体
 */
export async function request(path, { method = 'GET', data, headers } = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    body: data === undefined ? undefined : JSON.stringify(data),
  })

  // 与原先的写法保持一致：不判断 HTTP 状态码，一律按 JSON 解析，
  // 业务上的成功/失败由调用方根据返回体里的 code 判断。
  return response.json()
}
