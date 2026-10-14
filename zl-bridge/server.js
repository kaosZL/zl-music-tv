// ZL Bridge —— 把「Apple Music TV 代理协议」翻译成 lx 自定义音源
// 端点契约源：android/app/src/main/java/com/applemusicktv/data/network/ProxyApi.kt
// 数据引擎：vendor/kw（酷我搜索/榜单/歌词，ZL_Musicos musicSdk 原样移植）
// 取链引擎：scripts/*.js（lx userApi 脚本，userapi-host.js 宿主）
//
// 鉴权：要求请求头 X-Music-User-Token === ZL_TOKEN（app 把你设置的「Music-User-Token」
// 原样发给服务器——那个假 token 就是访问密钥；ZL_TOKEN 为空时关闭鉴权，仅限内网测试）
import http from 'node:http'
import { UserApiHost } from './userapi-host.js'
import kw from './vendor/kw/index.js'

const PORT = Number(process.env.ZL_PORT || 9127)
const TOKEN = process.env.ZL_TOKEN || ''
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const here = typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url))
const SCRIPT = process.env.ZL_SCRIPT || path.join(here, 'scripts/juhe.js')

// ── 无状态复合 ID：元数据编进 id，服务器重启/无会话也能解析 ──────────────
const b64u = (s) => Buffer.from(s, 'utf-8').toString('base64url')
const unb64u = (s) => Buffer.from(s, 'base64url').toString('utf-8')
const makeSongId = (m) => 'zl1.' + b64u(JSON.stringify(m))
const parseSongId = (id) => {
  if (!id?.startsWith('zl1.')) return null
  try { return JSON.parse(unb64u(id.slice(4))) } catch { return null }
}

// kw 的 interval 是 formatPlayTime 出来的 "m:ss" 字符串，统一转秒
const intervalToSec = (v) => {
  if (typeof v === 'number') return v
  const t = String(v || '').split(':')
  if (t.length === 2) return Number(t[0]) * 60 + Number(t[1])
  return Number(v) || 0
}

const songDto = (m) => ({
  id: makeSongId({ i: m.songmid, n: m.name, s: m.singer, al: m.albumName || '', d: intervalToSec(m.interval), p: m.img || m.pic || '' }),
  title: m.name || '',
  artistName: m.singer || '',
  albumName: m.albumName || '',
  durationMs: intervalToSec(m.interval) * 1000,
  artworkUrl: m.img || m.pic || null,
  artworkBgColor: null,
  previewUrl: null,
  previewHlsUrl: null,
  hasLyrics: true,
  trackNumber: null,
  genreNames: [],
})

// 榜单伪专辑：id 空间 zlboard:<bangId>
const boardAlbumDto = (b) => ({
  id: `zlboard:${b.bangid}`,
  title: b.name,
  artistName: '酷我榜',
  artworkUrl: b.pic || null,
  type: 'albums',
  artworkBgColor: null,
  releaseDate: null,
  trackCount: 0,
  genreNames: [],
  recordLabel: null,
  copyright: null,
  editorialNotes: null,
  isMasteredForItunes: false,
  motionUrl: null,
  tagline: null,
  wideArtworkUrl: null,
})

// ── HTTP 服务 ────────────────────────────────────────────────────────────
const json = (res, code, data) => {
  const body = JSON.stringify(data)
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) })
  res.end(body)
}

async function main() {
  const host = await new UserApiHost(SCRIPT, 'userapi-script').load()
  console.log(`[zl-bridge] userApi 脚本已加载: ${SCRIPT}`)
  console.log(`[zl-bridge] 监听 :${PORT}  鉴权: ${TOKEN ? '开（X-Music-User-Token）' : '关'}`)

  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost')
    const path = url.pathname
    try {
      if (TOKEN && req.headers['x-music-user-token'] !== TOKEN) {
        return json(res, 401, { error: 'unauthorized' })
      }
      const send = (data) => json(res, 200, data)
      const notFound = () => json(res, 404, { error: 'not found' })

      if (req.method === 'GET' && path === '/health') return send({ ok: true })
      if (req.method === 'GET' && path === '/auth/status') {
        return send({ hasMUT: true, mutSetAt: new Date().toISOString(), hasBearer: true })
      }
      if (req.method === 'POST' && path === '/auth/token') return send({ ok: true })
      if (req.method === 'DELETE' && path === '/auth/token') return send({ ok: true })
      if (req.method === 'GET' && path === '/api/status/apple') {
        return send({ ok: true, services: [], checkedAt: new Date().toISOString() })
      }

      // ── 搜索 ──
      if (req.method === 'GET' && path === '/api/search') {
        const term = url.searchParams.get('term') || ''
        if (!term.trim()) return send({ songs: [], albums: [], artists: [], playlists: [], curators: [] })
        const result = await kw.musicSearch.search(term, 1, 30)
        return send({ songs: (result.list || []).map(songDto), albums: [], artists: [], playlists: [], curators: [] })
      }

      // ── 歌曲 ──
      let m = path.match(/^\/api\/songs\/([^/]+)$/)
      if (req.method === 'GET' && m) {
        const meta = parseSongId(m[1])
        if (!meta) return notFound()
        return send({
          id: m[1], title: meta.n, artistName: meta.s, albumName: meta.al,
          durationMs: meta.d * 1000, artworkUrl: meta.p || null, artworkBgColor: null,
          previewUrl: null, previewHlsUrl: null, hasLyrics: true, trackNumber: null, genreNames: [],
        })
      }
      m = path.match(/^\/api\/songs$/)
      if (req.method === 'GET' && m) {
        const ids = (url.searchParams.get('ids') || '').split(',').filter(Boolean)
        return send({ songs: ids.map(id => parseSongId(id)).filter(Boolean).map(meta => ({
          id: makeSongId(meta), title: meta.n, artistName: meta.s, albumName: meta.al,
          durationMs: meta.d * 1000, artworkUrl: meta.p || null, artworkBgColor: null,
          previewUrl: null, previewHlsUrl: null, hasLyrics: true, trackNumber: null, genreNames: [],
        })) })
      }

      // ── 取流（ExoPlayer 直接请求这里；302 到真实音源）──
      m = path.match(/^\/api\/stream\/(?:prefetch\/)?(.+)$/)
      if (req.method === 'GET' && m) {
        const meta = parseSongId(m[1])
        if (!meta) return notFound()
        const musicInfo = { songmid: meta.i, name: meta.n, singer: meta.s, albumName: meta.al, interval: meta.d, source: 'kw' }
        let streamUrl = null
        for (const quality of ['320k', '128k', 'flac']) {
          try {
            streamUrl = await host.musicUrl('kw', musicInfo, quality)
            if (streamUrl) break
          } catch { /* 换下一档 */ }
        }
        if (!streamUrl) return json(res, 404, { error: 'no stream' })
        res.writeHead(302, { Location: streamUrl })
        return res.end()
      }

      // ── 歌词 ──
      m = path.match(/^\/api\/lyrics\/(.+)$/)
      if (req.method === 'GET' && m) {
        const meta = parseSongId(m[1])
        if (!meta) return notFound()
        try {
          const { lyric } = await kw.getLyric({ songmid: meta.i }).promise
          const lines = []
          for (const ln of String(lyric || '').split('\n')) {
            const t = ln.match(/^\[(\d+):(\d+(?:\.\d+)?)\](.*)$/)
            if (!t) continue
            const startMs = Math.round((Number(t[1]) * 60 + Number(t[2])) * 1000)
            if (t[3].trim()) lines.push({ startMs, endMs: 0, text: t[3].trim() })
          }
          return send({ lines, source: 'kw' })
        } catch {
          return send({ lines: [], source: null })
        }
      }

      // ── 首页/浏览：榜单货架 ──
      if (req.method === 'GET' && (path === '/api/home' || path === '/api/browse')) {
        const boards = kw.leaderboard.list || []
        return send({ sections: [{ title: '酷我排行榜', albums: boards.map(boardAlbumDto) }] })
      }

      // ── 榜单伪专辑 ──
      m = path.match(/^\/api\/albums\/zlboard:([^/]+)$/)
      if (req.method === 'GET' && m) {
        const boards = kw.leaderboard.list || []
        const b = boards.find(x => String(x.bangid) === m[1])
        if (!b) return notFound()
        return send(boardAlbumDto(b))
      }
      m = path.match(/^\/api\/albums\/zlboard:([^/]+)\/tracks$/)
      if (req.method === 'GET' && m) {
        const detail = await kw.leaderboard.getList(m[1], 1)
        return send({ tracks: (detail?.list || []).map(songDto), next: null })
      }

      // ── 未实现的 Apple 专有端点：安全空响应 ──
      if (path.startsWith('/api/albums/')) return send({ id: path.split('/')[3] || '', title: '', artistName: '', artworkUrl: null, type: 'albums', artworkBgColor: null, releaseDate: null, trackCount: 0, genreNames: [] })
      if (path.startsWith('/api/artists/')) return notFound()
      if (path.startsWith('/api/library')) return send(path.includes('playlists') ? { playlists: [] } : { songs: [] })
      if (path.startsWith('/api/browse/')) return send({ sections: [] })
      if (path.startsWith('/api/genres')) return send({ genres: [] })
      if (path === '/api/curators') return send({ curators: [] })
      return notFound()
    } catch (err) {
      console.error(`[zl-bridge] ${req.method} ${path} →`, err.message)
      return json(res, 500, { error: err.message })
    }
  })
  server.listen(PORT, '0.0.0.0')
}
main().catch(err => { console.error('启动失败:', err); process.exit(1) })
