// ZL LX Engine 出口：Kotlin/测试侧统一调用 globalThis.zlEngine.*
// 依赖：env.js（先执行）+ kwsdk.js（酷我 SDK）+ userApi 脚本（可选，经 evaluate 注入）
"use strict";

globalThis.zlEngine = {
  // 搜索：kw.musicSearch.search(keyword, page, limit) → {list,total,allPage,...}
  async search(keyword, page = 1, limit = 30) {
    return await globalThis.__zlKw.musicSearch.search(keyword, page, limit)
  },
  // 榜单列表：[{id,name,bangid,...}]
  boards() {
    return globalThis.__zlKw.leaderboard.list
  },
  // 榜单详情：getList(bangid, page) → {list,total,...}
  async boardDetail(bangId, page = 1) {
    return await globalThis.__zlKw.leaderboard.getList(String(bangId), page)
  },
  // 歌词：走 m.kuwo.cn JSON 接口（无需解密），返回 {lines:[{time, text}]}
  async lyric(songId) {
    const { body } = await globalThis.httpFetch(`http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${songId}`).promise
    const rows = (body && body.data && body.data.lrclist) || []
    return {
      lines: rows
        .filter(r => r.lineLyric && r.lineLyric.trim())
        .map(r => ({ time: Math.round((Number(r.timeCoef) || 0) * 1000), text: String(r.lineLyric).trim() })),
    }
  },
  // 取链（userApi 脚本）：source 如 'kw'，musicInfo 为 lx 歌曲对象
  async musicUrl(source, musicInfo, type = '320k') {
    return await globalThis.__zlLxDispatch({ action: 'musicUrl', source, info: { musicInfo, type } })
  },
  hasUserApi() {
    return globalThis.__zlLxHasHandler('request')
  },
}
