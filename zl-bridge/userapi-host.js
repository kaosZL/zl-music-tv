// lx-music userApi 脚本宿主（Node 版）
// 契约源：ZL_Musicos android JsHandler.java + 实际脚本（pdone 聚合/野草）：
//   globalThis.lx = { version, EVENT_NAMES, on(name,cb), send(name,data), request(url,opts,cb) }
//   脚本加载后自行 fetch init.conf 并 send(EVENT_NAMES.inited, init)
//   取链：host 派发 {action:'musicUrl', source, info:{musicInfo, type}} 给 on('request') 注册的 handler，
//   handler（async）resolve 出播放 URL
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'

const EVENT_NAMES = {
  request: 'request',
  inited: 'inited',
  updateAlert: 'updateAlert',
}

export class UserApiHost {
  constructor(scriptPath, scriptName = 'userapi') {
    this.scriptPath = scriptPath
    this.scriptName = scriptName
    this.handlers = new Map()   // eventName -> Set<fn>
    this.initPromise = null
    this.initResolve = null
    this.initInfo = null
  }

  async load() {
    const code = await readFile(this.scriptPath, 'utf-8')
    const lx = {
      version: '2.0.0',
      EVENT_NAMES,
      on: (name, cb) => {
        if (!this.handlers.has(name)) this.handlers.set(name, new Set())
        this.handlers.get(name).add(cb)
      },
      send: (name, data) => {
        if (name === EVENT_NAMES.inited) {
          this.initInfo = data
          this.initResolve?.(data)
          this.initResolve = null
        }
      },
      request: (url, options = {}, cb) => {
        fetch(url, {
          method: options.method || 'GET',
          headers: options.headers || {},
          body: options.body,
          redirect: 'follow',
        }).then(async resp => {
          const text = await resp.text()
          let body = text
          try { body = JSON.parse(text) } catch { /* 保持文本 */ }
          cb(null, { statusCode: resp.status, headers: resp.headers, body })
        }).catch(err => cb(err))
      },
    }
    this.initPromise = new Promise(resolve => { this.initResolve = resolve })
    // 10 秒 init 超时不算致命：脚本可能仅取链失败才暴露
    const context = vm.createContext({ globalThis: { lx } , console, setTimeout, clearTimeout, Promise, JSON })
    context.globalThis.lx = lx
    vm.runInContext(code, context, { filename: this.scriptName })
    // init.conf 是脚本自己的异步流程，等它（最多 10s）
    await Promise.race([this.initPromise, new Promise(r => setTimeout(r, 10000))])
    return this
  }

  /** 取播放链；info 为 lx musicInfo，type 如 '320k' */
  async musicUrl(source, musicInfo, type = '320k') {
    const set = this.handlers.get(EVENT_NAMES.request)
    if (!set || !set.size) throw new Error('脚本未注册 request 处理器')
    let lastErr = null
    for (const fn of set) {
      try {
        const url = await fn({ action: 'musicUrl', source, info: { musicInfo, type } })
        if (typeof url === 'string' && url.startsWith('http')) return url
      } catch (e) { lastErr = e }
    }
    throw lastErr || new Error('脚本未能解析出播放链接')
  }
}
