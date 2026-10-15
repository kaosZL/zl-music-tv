// ZL LX Engine 环境：lx httpFetch/加密垫层 + userApi 脚本宿主（globalThis.lx）
// 双态：QuickJS（__zl* 桥由 Kotlin 注入）/ Node 测试（__zl* 由测试 harness 注入），接口一致
"use strict";

// ── 平台桥（必须由宿主先注入）：__zlHttp / __zlMd5 / __zlAesEncrypt / __zlAesDecrypt ──
if (typeof globalThis.__zlHttp !== 'function') throw new Error('LX Engine: 缺少 __zlHttp 桥')

// ── lx httpFetch 契约：{promise: Promise<{statusCode, headers, body}>, cancelHttp()} ──
globalThis.httpFetch = (url, options = {}) => {
  let cancelled = false
  const promise = (async () => {
    // 桥协议：字符串进出（QuickJS 侧好桥接），Kotlin 返回 JSON 字符串
    const __reqJson = JSON.stringify({
      method: options.method || 'GET',
      headers: options.headers || {},
      body: typeof options.body === 'string' ? options.body : (options.body != null ? String(options.body) : null),
      binary: !!options.binary,
    })
    const resp = JSON.parse(await __zlHttp(url, __reqJson))
    if (cancelled) throw new Error('request cancelled')
    let body = resp.body
    if (!options.binary && typeof body === 'string') {
      const s = body.trimStart()
      if (s.startsWith('{') || s.startsWith('[')) { try { body = JSON.parse(body) } catch {} }
    }
    return { statusCode: resp.statusCode, headers: resp.headers, body }
  })()
  return { promise, cancelHttp: () => { cancelled = true } }
}

// ── 工具函数（musicSdk 顶层契约）──
const __entityMap = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' }
globalThis.decodeName = (str = '') => {
  if (!str) return ''
  return String(str)
    .replace(/&#x(\w+);/g, (m, h) => String.fromCharCode(parseInt(h, 16)))
    .replace(/&(\w+|#\d+);/g, (m, e) => {
      if (e.startsWith('#')) return String.fromCharCode(parseInt(e.slice(1)))
      return __entityMap[e] ?? m
    })
}
globalThis.formatPlayTime = (time) => {
  let minute = Math.floor(time / 60)
  let second = Math.floor(time % 60)
  second = second >= 10 ? second : `0${second}`
  return `${minute}:${second}`
}
globalThis.dateFormat2 = (time) => {
  const d = new Date(time)
  const p = (n) => (n >= 10 ? n : `0${n}`)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
globalThis.toMD5 = (str) => __zlMd5(String(str))
globalThis.AES_MODE = { CBC_128_PKCS7Padding: 'AES/CBC/PKCS7Padding', ECB_128_NoPadding: 'AES/ECB/NoPadding' }
// 契约：text/key/iv 均 base64；encrypt 出 base64；decrypt 出 utf8
globalThis.aesEncryptSync = (text, key, iv, mode) => __zlAesEncrypt(text, key, iv, mode)
globalThis.aesDecryptSync = (text, key, iv, mode) => __zlAesDecrypt(text, key, iv, mode)

// ── lx userApi 脚本宿主 ──
const __lxHandlers = new Map()
globalThis.lx = {
  version: '2.0.0',
  EVENT_NAMES: { request: 'request', inited: 'inited', updateAlert: 'updateAlert' },
  on(name, cb) { if (!__lxHandlers.has(name)) __lxHandlers.set(name, new Set()); __lxHandlers.get(name).add(cb) },
  send(name, data) { globalThis.__zlLxEvent && globalThis.__zlLxEvent(name, data) },
  request(url, options, cb) {
    const __reqJson = JSON.stringify({
      method: (options && options.method) || 'GET',
      headers: (options && options.headers) || {},
      body: (options && options.body) != null ? String(options.body) : null,
      binary: false,
    })
    __zlHttp(url, __reqJson).then(raw => {
      const resp = JSON.parse(raw)
      let body = resp.body
      if (typeof body === 'string') { try { body = JSON.parse(body) } catch {} }
      cb(null, { statusCode: resp.statusCode, headers: resp.headers, body })
    }).catch(err => cb(err))
  },
}
globalThis.__zlLxHasHandler = (name) => __lxHandlers.has(name) && __lxHandlers.get(name).size > 0
globalThis.__zlLxDispatch = async (payload) => {
  const set = __lxHandlers.get('request')
  if (!set || !set.size) throw new Error('无已加载的 userApi 脚本')
  for (const fn of set) {
    const r = await fn(payload)
    if (typeof r === 'string' && r.startsWith('http')) return r
  }
  throw new Error('脚本未能解析出链接')
}
