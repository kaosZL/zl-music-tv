// musicSdk 顶层工具函数垫片（源：ZL_Musicos src/utils/common.ts + index.ts，逻辑原样）
export const formatPlayTime = (time) => {
  let minute = Math.floor(time / 60)
  let second = Math.floor(time % 60)
  if (minute >= 60) {
    const hour = Math.floor(minute / 60)
    minute = minute - hour * 60
  }
  second = second >= 10 ? second : `0${second}`
  return `${minute}:${second}`
}

export const dateFormat2 = (time) => {
  const d = new Date(time)
  const pad = (n) => (n >= 10 ? n : `0${n}`)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const entityRE = /&(\w+|#\d+);/g
const entityMap = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' }
export const decodeName = (str = '') => {
  if (!str) return ''
  return String(str)
    .replace(entityRE, (m, e) => {
      if (e.startsWith('#')) return String.fromCharCode(parseInt(e.slice(1)))
      return entityMap[e] ?? m
    })
    .replace(/&#x(\w+);/g, (m, h) => String.fromCharCode(parseInt(h, 16)))
}
