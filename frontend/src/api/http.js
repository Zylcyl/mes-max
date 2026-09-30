/**
 * 极简 HTTP 客户端（基于原生 fetch，不引入 axios）。
 * 统一处理：令牌注入、业务码校验、401 跳登录、错误提示。
 */
import { useToast } from '@/stores/toast';

const BASE = '/api';
export const TOKEN_KEY = 'mesmax_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(t) {
  if (t) localStorage.setItem(TOKEN_KEY, t);
  else localStorage.removeItem(TOKEN_KEY);
}

async function request(method, path, { body, params, silent } = {}) {
  let url = BASE + path;
  if (params && Object.keys(params).length) {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') qs.append(k, v);
    });
    const s = qs.toString();
    if (s) url += (url.includes('?') ? '&' : '?') + s;
  }

  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (e) {
    if (!silent) useToast().error('网络异常：无法连接后端服务，请确认后端已启动（默认 8080 端口）');
    throw e;
  }

  const text = await res.text();
  let json;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    // 网关/代理返回的非 JSON 响应（如 Nginx 502 页面），也要给出可读提示
    json = { code: res.status || 500, msg: text.slice(0, 200) || `HTTP ${res.status}` };
  }

  // 后端对鉴权失败会同时返回真实 HTTP 状态码（401/403）与统一响应体，
  // 这里以响应体为准；若响应体结构缺失（例如被网关拦截）则退回按 HTTP 状态码判断。
  if (json.code === undefined) json.code = res.ok ? 200 : res.status;

  if (json.code === 401) {
    setToken('');
    if (!location.hash.includes('/login')) {
      useToast().warn('登录状态已失效，请重新登录');
      location.hash = '#/login';
    }
    throw new Error(json.msg || '未登录');
  }
  if (json.code !== 200) {
    const msg = json.msg || '请求失败';
    if (!silent) useToast().error(msg);
    const err = new Error(msg);
    err.code = json.code;
    throw err;
  }
  return json.data;
}

export const http = {
  // 注意：query 只在这里交给 request() 拼一次。早先写成 path + qs(params) 且又传了 params，
  // 会把同一组参数拼两遍（?a=1&a=1），后端拿到的 map 值会变成 "1,1"。
  get: (path, params, opts) => request('GET', path, { params, ...opts }),
  post: (path, body, opts) => request('POST', path, { body, ...opts }),
  put: (path, body, opts) => request('PUT', path, { body, ...opts }),
  del: (path, opts) => request('DELETE', path, opts),
  sse: (path) => {
    // EventSource 无法自定义请求头，用查询参数携带令牌（后端已支持）
    return new EventSource(`${BASE}${path}${path.includes('?') ? '&' : '?'}token=${encodeURIComponent(getToken())}`);
  },
};
