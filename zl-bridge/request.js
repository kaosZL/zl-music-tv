// lx httpFetch 的 Node 垫片
// 契约：httpFetch(url, options) → { promise: Promise<{statusCode, headers, body}>, cancelHttp() }
// body 自动按 content-type JSON 解析（对齐 RN 版行为）
export const httpFetch = (url, options = {}) => {
  const ctrl = new AbortController()
  const promise = fetch(url, {
    method: options.method || 'GET',
    headers: options.headers || {},
    body: options.body,
    signal: ctrl.signal,
    redirect: 'follow',
  }).then(async resp => {
    // lx 请求契约里 binary: true（如酷我 newlrc 接口）需要 Buffer
    if (options.binary) {
      const buf = Buffer.from(await resp.arrayBuffer())
      return { statusCode: resp.status, headers: resp.headers, body: buf }
    }
    const text = await resp.text()
    let body = text
    const ct = resp.headers.get('content-type') || ''
    if (ct.includes('json') || ct.includes('javascript')) {
      try { body = JSON.parse(text) } catch { /* 保持文本 */ }
    } else if (text.startsWith('{') || text.startsWith('[')) {
      try { body = JSON.parse(text) } catch { /* 保持文本 */ }
    }
    return { statusCode: resp.status, headers: resp.headers, body }
  })
  return { promise, cancelHttp: () => ctrl.abort() }
}
export default httpFetch
