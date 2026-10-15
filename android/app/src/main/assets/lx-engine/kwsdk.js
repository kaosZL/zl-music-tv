// ZL LX Engine —— 酷我 SDK（自 ZL_Musicos musicSdk/kw 展平移植，IIFE 逐文件隔离）
// 前置全局（env.js 注入）：httpFetch / decodeName / formatPlayTime / dateFormat2 / toMD5 / aesEncryptSync / aesDecryptSync / AES_MODE
"use strict";

const __kwDecodeLyric = async () => { throw new Error('加密歌词路径未启用（引擎走 JSON 歌词接口）') };
const __kwUtil = (() => {
// import BackgroundTimer from 'react-native-background-timer'
// import { httpGet, httpFetch } from '../../request'

// const kw_token = {
//   token: null,
//   isGetingToken: false,
// }

// const translationMap = {
//   "{'": '{"',
//   "'}\n": '"}',
//   "'}": '"}',
//   "':'": '":"',
//   "','": '","',
//   "':{'": '":{"',
//   "':['": '":["',
//   "'}],'": '"}],"',
//   "':[{'": '":[{"',
//   "'},'": '"},"',
//   "'},{'": '"},{"',
//   "':[],'": '":[],"',
//   "':{},'": '":{},"',
//   "'}]}": '"}]}',
// }

// export const objStr2JSON = str => {
//   return JSON.parse(str.replace(/(^{'|'}\n$|'}$|':'|','|':\[{'|'}\],'|':{'|'},'|'},{'|':\['|':\[\],'|':{},'|'}]})/g, s => translationMap[s]))
// }

const objStr2JSON = str => {
  return JSON.parse(str.replace(/('(?=(,\s*')))|('(?=:))|((?<=([:,]\s*))')|((?<={)')|('(?=}))/g, '"'))
}


const formatSinger = rawData => rawData.replace(/&/g, '、')

const matchToken = headers => {
  try {
    return headers['set-cookie'].match(/kw_token=(\w+)/)[1]
  } catch (err) {
    return null
  }
}

// const wait = time => new Promise(resolve => setTimeout(() => resolve(), time))


// export const getToken = (retryNum = 0) => new Promise((resolve, reject) => {
//   if (retryNum > 2) return Promise.reject(new Error('try max num'))

//   if (kw_token.isGetingToken) return wait(1000).then(() => getToken(retryNum).then(token => resolve(token)))
//   if (kw_token.token) return resolve(kw_token.token)
//   kw_token.isGetingToken = true
//   httpGet('http://www.kuwo.cn/', (err, resp) => {
//     kw_token.isGetingToken = false
//     if (err) return getToken(++retryNum)
//     if (resp.statusCode != 200) return reject(new Error('获取失败'))
//     const token = kw_token.token = matchToken(resp.headers)
//     resolve(token)
//   })
// })

// export const tokenRequest = async(url, options = {}) => {
//   let token = kw_token.token
//   if (!token) token = await getToken()
//   if (!options.headers) {
//     options.headers = {
//       Referer: 'http://www.kuwo.cn/',
//       csrf: token,
//       cookie: 'kw_token=' + token,
//     }
//   }
//   const requestObj = httpFetch(url, options)
//   requestObj.promise = requestObj.promise.then(resp => {
//     // console.log(resp)
//     if (resp.statusCode == 200) {
//       kw_token.token = matchToken(resp.headers)
//     }
//     return resp
//   })
//   return requestObj
// }

const lrcTools = {
  rxps: {
    wordLine: /^(\[\d{1,2}:.*\d{1,4}\])\s*(\S+(?:\s+\S+)*)?\s*/,
    tagLine: /\[(ver|ti|ar|al|offset|by|kuwo):\s*(\S+(?:\s+\S+)*)\s*\]/,
    wordTimeAll: /<(-?\d+),(-?\d+)(?:,-?\d+)?>/g,
    wordTime: /<(-?\d+),(-?\d+)(?:,-?\d+)?>/,
  },
  offset: 1,
  offset2: 1,
  isOK: false,
  lines: [],
  tags: [],
  getWordInfo(str, str2, prevWord) {
    const offset = parseInt(str)
    const offset2 = parseInt(str2)
    let startTime = Math.abs((offset + offset2) / (this.offset * 2))
    let endTime = Math.abs((offset - offset2) / (this.offset2 * 2)) + startTime
    if (prevWord) {
      if (startTime < prevWord.endTime) {
        prevWord.endTime = startTime
        if (prevWord.startTime > prevWord.endTime) {
          prevWord.startTime = prevWord.endTime
        }

        prevWord.newTimeStr = `<${prevWord.startTime},${prevWord.endTime - prevWord.startTime}>`
        // console.log(prevWord)
      }
    }
    return {
      startTime,
      endTime,
      timeStr: `<${startTime},${endTime - startTime}>`,
    }
  },
  parseLine(line) {
    if (line.length < 6) return
    let result = this.rxps.wordLine.exec(line)
    if (result) {
      const time = result[1]
      let words = result[2]
      if (words == null) {
        words = ''
      }
      const wordTimes = words.match(this.rxps.wordTimeAll)
      if (!wordTimes) return
      // console.log(wordTimes)
      let preTimeInfo
      for (const timeStr of wordTimes) {
        const result = this.rxps.wordTime.exec(timeStr)
        const wordInfo = this.getWordInfo(result[1], result[2], preTimeInfo)
        words = words.replace(timeStr, wordInfo.timeStr)
        if (preTimeInfo?.newTimeStr) words = words.replace(preTimeInfo.timeStr, preTimeInfo.newTimeStr)
        preTimeInfo = wordInfo
      }
      this.lines.push(time + words)
      return
    }
    result = this.rxps.tagLine.exec(line)
    if (!result) return
    if (result[1] == 'kuwo') {
      let content = result[2]
      if (content != null && content.includes('][')) {
        content = content.substring(0, content.indexOf(']['))
      }
      const valueOf = parseInt(content, 8)
      this.offset = Math.trunc(valueOf / 10)
      this.offset2 = Math.trunc(valueOf % 10)
      if (this.offset == 0 || Number.isNaN(this.offset) || this.offset2 == 0 || Number.isNaN(this.offset2)) {
        this.isOK = false
      }
    } else {
      this.tags.push(line)
    }
  },
  parse(lrc) {
    // console.log(lrc)
    const lines = lrc.split(/\r\n|\r|\n/)
    const tools = Object.create(this)
    tools.isOK = true
    tools.offset = 1
    tools.offset2 = 1
    tools.lines = []
    tools.tags = []

    for (const line of lines) {
      if (!tools.isOK) throw new Error('failed')
      tools.parseLine(line)
    }
    if (!tools.lines.length) return ''
    let lrcs = tools.lines.join('\n')
    if (tools.tags.length) lrcs = `${tools.tags.join('\n')}\n${lrcs}`
    // console.log(lrcs)
    return lrcs
  },
}


// const createAesEncrypt = (buffer, mode, key, iv) => {
//   const cipher = createCipheriv(mode, key, iv)
//   return Buffer.concat([cipher.update(buffer), cipher.final()])
// }

// const createAesDecrypt = (buffer, mode, key, iv) => {
//   const cipher = createDecipheriv(mode, key, iv)
//   return Buffer.concat([cipher.update(buffer), cipher.final()])
// }

const wbdCrypto = {
  aesMode: 'aes-128-ecb',
  // aesKey: Buffer.from([112, 87, 39, 61, 199, 250, 41, 191, 57, 68, 45, 114, 221, 94, 140, 228], 'binary'),
  aesKey: 'cFcnPcf6Kb85RC1y3V6M5A==',
  aesIv: '',
  appId: 'y67sprxhhpws',
  decodeData(base64Result) {
    // const data = Buffer.from(decodeURIComponent(base64Result), 'base64')
    // return JSON.parse(createAesDecrypt(data, this.aesMode, this.aesKey, this.aesIv).toString())
    const data = decodeURIComponent(base64Result)
    return JSON.parse(aesDecryptSync(data, this.aesKey, this.aesIv, AES_MODE.ECB_128_NoPadding))
  },
  createSign(data, time) {
    const str = `${this.appId}${data}${time}`
    return toMD5(str).toUpperCase()
  },
  buildParam(jsonData) {
    // const data = Buffer.from(JSON.stringify(jsonData))
    // const time = Date.now()

    // const encodeData = createAesEncrypt(data, this.aesMode, this.aesKey, this.aesIv).toString('base64')
    const data = Buffer.from(JSON.stringify(jsonData)).toString('base64')
    const time = Date.now()

    const encodeData = aesEncryptSync(data, this.aesKey, this.aesIv, AES_MODE.ECB_128_NoPadding)
    const sign = this.createSign(encodeData, time)

    return `data=${encodeURIComponent(encodeData)}&time=${time}&appId=${this.appId}&sign=${sign}`
  },
}

return { objStr2JSON, formatSinger, matchToken, lrcTools, wbdCrypto }
})();
const { objStr2JSON, formatSinger, matchToken, lrcTools, wbdCrypto } = __kwUtil
const decodeLyric = __kwDecodeLyric
const tipSearch = (() => {
// import { decodeName } from '../../index'
// import { tokenRequest } from './util'

return {
  regExps: {
    relWord: /RELWORD=(.+)/,
  },
  requestObj: null,
  async tipSearchBySong(str) {
    // 报错403，加了referer还是有问题（直接换一个
    // this.requestObj = await tokenRequest(`http://www.kuwo.cn/api/www/search/searchKey?key=${encodeURIComponent(str)}`)

    this.cancelTipSearch()
    this.requestObj = httpFetch(`https://tips.kuwo.cn/t.s?corp=kuwo&newver=3&p2p=1&notrace=0&c=mbox&w=${encodeURIComponent(str)}&encoding=utf8&rformat=json`, {
      Referer: 'http://www.kuwo.cn/',
    })
    return this.requestObj.promise.then(({ body, statusCode }) => {
      if (statusCode != 200 || !body.WORDITEMS) return Promise.reject(new Error('请求失败'))
      return body.WORDITEMS
    })
  },
  handleResult(rawData) {
    return rawData.map(item => item.RELWORD)
  },
  cancelTipSearch() {
    if (this.requestObj && this.requestObj.cancelHttp) this.requestObj.cancelHttp()
  },
  async search(str) {
    return this.tipSearchBySong(str).then(result => this.handleResult(result))
  },
}

})();
const musicSearch = (() => {
// import '../../polyfill/array.find'

// import { debug } from '../../utils/env'

return {
  regExps: {
    mInfo: /level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/,
  },
  limit: 30,
  total: 0,
  page: 0,
  allPage: 1,
  // cancelFn: null,
  musicSearch(str, page, limit) {
    const musicSearchRequestObj = httpFetch(`http://search.kuwo.cn/r.s?client=kt&all=${encodeURIComponent(str)}&pn=${page - 1}&rn=${limit}&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1`)
    return musicSearchRequestObj.promise
  },
  // getImg(songId) {
  //   return httpGet(`http://player.kuwo.cn/webmusic/sj/dtflagdate?flag=6&rid=MUSIC_${songId}`)
  // },
  // getLrc(songId) {
  //   return httpGet(`http://mobile.kuwo.cn/mpage/html5/songinfoandlrc?mid=${songId}&flag=0`)
  // },
  handleResult(rawData) {
    const result = []
    if (!rawData) return result
    // console.log(rawData)
    for (let i = 0; i < rawData.length; i++) {
      const info = rawData[i]
      let songId = info.MUSICRID.replace('MUSIC_', '')
      // const format = (info.FORMATS || info.formats).split('|')

      if (!info.N_MINFO) {
        console.log('N_MINFO is undefined')
        return null
      }

      const types = []
      const _types = {}

      let infoArr = info.N_MINFO.split(';')
      for (let info of infoArr) {
        info = info.match(this.regExps.mInfo)
        if (info) {
          switch (info[2]) {
            case '4000':
              types.push({ type: 'flac24bit', size: info[4] })
              _types.flac24bit = {
                size: info[4].toLocaleUpperCase(),
              }
              break
            case '2000':
              types.push({ type: 'flac', size: info[4] })
              _types.flac = {
                size: info[4].toLocaleUpperCase(),
              }
              break
            case '320':
              types.push({ type: '320k', size: info[4] })
              _types['320k'] = {
                size: info[4].toLocaleUpperCase(),
              }
              break
            case '128':
              types.push({ type: '128k', size: info[4] })
              _types['128k'] = {
                size: info[4].toLocaleUpperCase(),
              }
              break
          }
        }
      }
      types.reverse()

      let interval = parseInt(info.DURATION)

      result.push({
        name: decodeName(info.SONGNAME),
        singer: formatSinger(decodeName(info.ARTIST)),
        source: 'kw',
        // img = (info.album.name === '' || info.album.name === '空')
        //   ? `http://player.kuwo.cn/webmusic/sj/dtflagdate?flag=6&rid=MUSIC_160911.jpg`
        //   : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${info.album.mid}.jpg`
        songmid: songId,
        albumId: decodeName(info.ALBUMID || ''),
        interval: Number.isNaN(interval) ? 0 : formatPlayTime(interval),
        albumName: info.ALBUM ? decodeName(info.ALBUM) : '',
        lrc: null,
        img: null,
        otherSource: null,
        types,
        _types,
        typeUrl: {},
      })
    }
    // console.log(result)
    return result
  },
  search(str, page = 1, limit, retryNum = 0) {
    if (retryNum > 2) return Promise.reject(new Error('try max num'))
    if (limit == null) limit = this.limit
    // http://newlyric.kuwo.cn/newlyric.lrc?62355680
    return this.musicSearch(str, page, limit).then(({ body: result }) => {
      // console.log(result)
      if (!result || (result.TOTAL !== '0' && result.SHOW === '0')) return this.search(str, page, limit, ++retryNum)
      let list = this.handleResult(result.abslist)

      if (list == null) return this.search(str, page, limit, ++retryNum)

      this.total = parseInt(result.TOTAL)
      this.page = page
      this.allPage = Math.ceil(this.total / limit)

      return Promise.resolve({
        list,
        allPage: this.allPage,
        total: this.total,
        limit,
        source: 'kw',
      })
    })
  },
}

})();
const leaderboard = (() => {


const boardList = [{ id: 'kw__93', name: '飙升榜', bangid: '93' }, { id: 'kw__17', name: '新歌榜', bangid: '17' }, { id: 'kw__16', name: '热歌榜', bangid: '16' }, { id: 'kw__158', name: '抖音热歌榜', bangid: '158' }, { id: 'kw__292', name: '铃声榜', bangid: '292' }, { id: 'kw__284', name: '热评榜', bangid: '284' }, { id: 'kw__290', name: 'ACG新歌榜', bangid: '290' }, { id: 'kw__286', name: '台湾KKBOX榜', bangid: '286' }, { id: 'kw__279', name: '冬日暖心榜', bangid: '279' }, { id: 'kw__281', name: '巴士随身听榜', bangid: '281' }, { id: 'kw__255', name: 'KTV点唱榜', bangid: '255' }, { id: 'kw__280', name: '家务进行曲榜', bangid: '280' }, { id: 'kw__282', name: '熬夜修仙榜', bangid: '282' }, { id: 'kw__283', name: '枕边轻音乐榜', bangid: '283' }, { id: 'kw__278', name: '古风音乐榜', bangid: '278' }, { id: 'kw__264', name: 'Vlog音乐榜', bangid: '264' }, { id: 'kw__242', name: '电音榜', bangid: '242' }, { id: 'kw__187', name: '流行趋势榜', bangid: '187' }, { id: 'kw__204', name: '现场音乐榜', bangid: '204' }, { id: 'kw__186', name: 'ACG神曲榜', bangid: '186' }, { id: 'kw__185', name: '最强翻唱榜', bangid: '185' }, { id: 'kw__26', name: '经典怀旧榜', bangid: '26' }, { id: 'kw__104', name: '华语榜', bangid: '104' }, { id: 'kw__182', name: '粤语榜', bangid: '182' }, { id: 'kw__22', name: '欧美榜', bangid: '22' }, { id: 'kw__184', name: '韩语榜', bangid: '184' }, { id: 'kw__183', name: '日语榜', bangid: '183' }, { id: 'kw__145', name: '会员畅听榜', bangid: '145' }, { id: 'kw__153', name: '网红新歌榜', bangid: '153' }, { id: 'kw__64', name: '影视金曲榜', bangid: '64' }, { id: 'kw__176', name: 'DJ嗨歌榜', bangid: '176' }, { id: 'kw__106', name: '真声音', bangid: '106' }, { id: 'kw__12', name: 'Billboard榜', bangid: '12' }, { id: 'kw__49', name: 'iTunes音乐榜', bangid: '49' }, { id: 'kw__180', name: 'beatport电音榜', bangid: '180' }, { id: 'kw__13', name: '英国UK榜', bangid: '13' }, { id: 'kw__164', name: '百大DJ榜', bangid: '164' }, { id: 'kw__246', name: 'YouTube音乐排行榜', bangid: '246' }, { id: 'kw__265', name: '韩国Genie榜', bangid: '265' }, { id: 'kw__14', name: '韩国M-net榜', bangid: '14' }, { id: 'kw__8', name: '香港电台榜', bangid: '8' }, { id: 'kw__15', name: '日本公信榜', bangid: '15' }, { id: 'kw__151', name: '腾讯音乐人原创榜', bangid: '151' }]

const sortQualityArray = array => {
  const qualityMap = {
    flac24bit: 4,
    flac: 3,
    '320k': 2,
    '128k': 1,
  }
  const rawQualityArray = []
  const newQualityArray = []

  array.forEach((item, index) => {
    const type = qualityMap[item.type]
    if (!type) return
    rawQualityArray.push({ type, index })
  })

  rawQualityArray.sort((a, b) => a.type - b.type)

  rawQualityArray.forEach(item => {
    newQualityArray.push(array[item.index])
  })

  return newQualityArray
}

return {
  list: [
    {
      id: 'kwbiaosb',
      name: '飙升榜',
      bangid: 93,
    },
    {
      id: 'kwregb',
      name: '热歌榜',
      bangid: 16,
    },
    {
      id: 'kwhuiyb',
      name: '会员榜',
      bangid: 145,
    },
    {
      id: 'kwdouyb',
      name: '抖音榜',
      bangid: 158,
    },
    {
      id: 'kwqsb',
      name: '趋势榜',
      bangid: 187,
    },
    {
      id: 'kwhuaijb',
      name: '怀旧榜',
      bangid: 26,
    },
    {
      id: 'kwhuayb',
      name: '华语榜',
      bangid: 104,
    },
    {
      id: 'kwyueyb',
      name: '粤语榜',
      bangid: 182,
    },
    {
      id: 'kwoumb',
      name: '欧美榜',
      bangid: 22,
    },
    {
      id: 'kwhanyb',
      name: '韩语榜',
      bangid: 184,
    },
    {
      id: 'kwriyb',
      name: '日语榜',
      bangid: 183,
    },
  ],
  // getUrl: (p, l, id) => `http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=${p - 1}&rn=${l}&type=bang&data=content&id=${id}&show_copyright_off=0&pcmp4=1&isbang=1`,
  regExps: {
    mInfo: /level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/,
  },
  limit: 100,
  _requestBoardsObj: null,

  getBoardsData() {
    if (this._requestBoardsObj) this._requestBoardsObj.cancelHttp()
    this._requestBoardsObj = httpFetch('http://qukudata.kuwo.cn/q.k?op=query&cont=tree&node=2&pn=0&rn=1000&fmt=json&level=2')
    return this._requestBoardsObj.promise
  },
  getData(url) {
    const requestDataObj = httpFetch(url)
    return requestDataObj.promise
  },
  filterData(rawList) {
    return rawList.map(item => {
      let types = []
      const _types = {}
      const qualitys = new Set()

      item.n_minfo.split(';').forEach(i => {
        const info = i.match(this.regExps.mInfo)
        if (!info) return

        const quality = info[2]
        const size = info[4].toLocaleUpperCase()

        if (qualitys.has(quality)) return
        qualitys.add(quality)

        switch (quality) {
          case '4000':
            types.push({ type: 'flac24bit', size })
            _types.flac24bit = { size }
            break
          case '2000':
            types.push({ type: 'flac', size })
            _types.flac = { size }
            break
          case '320':
            types.push({ type: '320k', size })
            _types['320k'] = { size }
            break
          case '128':
            types.push({ type: '128k', size })
            _types['128k'] = { size }
            break
        }
      })
      types = sortQualityArray(types)

      return {
        singer: formatSinger(decodeName(item.artist)),
        name: decodeName(item.name),
        albumName: decodeName(item.album),
        albumId: item.albumId,
        songmid: item.id,
        source: 'kw',
        interval: formatPlayTime(parseInt(item.duration)),
        img: item.pic,
        lrc: null,
        otherSource: null,
        types,
        _types,
        typeUrl: {},
      }
    })
  },

  filterBoardsData(rawList) {
    // console.log(rawList)
    let list = []
    for (const board of rawList) {
      if (board.source != '1') continue
      list.push({
        id: 'kw__' + board.sourceid,
        name: board.name,
        bangid: String(board.sourceid),
      })
    }
    return list
  },
  async getBoards(retryNum = 0) {
    // if (++retryNum > 3) return Promise.reject(new Error('try max num'))
    // let response
    // try {
    //   response = await this.getBoardsData()
    // } catch (error) {
    //   return this.getBoards(retryNum)
    // }
    // console.log(response.body)
    // if (response.statusCode !== 200 || !response.body.child) return this.getBoards(retryNum)
    // const list = this.filterBoardsData(response.body.child)
    // // console.log(list)
    // console.log(JSON.stringify(list))
    // this.list = list
    // return {
    //   list,
    //   source: 'kw',
    // }
    this.list = boardList
    return {
      list: boardList,
      source: 'kw',
    }
  },

  getList(id, page, retryNum = 0) {
    if (++retryNum > 3) return Promise.reject(new Error('try max num'))

    const requestBody = { uid: '', devId: '', sFrom: 'kuwo_sdk', user_type: 'AP', carSource: 'kwplayercar_ar_6.0.1.0_apk_keluze.apk', id, pn: page - 1, rn: this.limit }
    const requestUrl = `https://wbd.kuwo.cn/api/bd/bang/bang_info?${wbdCrypto.buildParam(requestBody)}`
    const request = httpFetch(requestUrl, { cache: 'default' }).promise

    return request.then(({ statusCode, body }) => {
      const rawData = wbdCrypto.decodeData(body)
      // console.log(rawData)
      const data = rawData.data
      if (statusCode !== 200 || rawData.code != 200 || !data.musiclist) return this.getList(id, page, retryNum)

      const total = parseInt(data.total)
      const list = this.filterData(data.musiclist)

      return {
        total,
        list,
        limit: this.limit,
        page,
        source: 'kw',
      }
    })
  },

  // getDetailPageUrl(id) {
  //   return `http://www.kuwo.cn/rankList/${id}`
  // },
}

})();
const lyric = (() => {

/*
return {
  formatTime(time) {
    let m = parseInt(time / 60)
    let s = (time % 60).toFixed(2)
    return (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s)
  },
  sortLrcArr(arr) {
    const lrcSet = new Set()
    let lrc = []
    let lrcT = []

    for (const item of arr) {
      if (lrcSet.has(item.time)) {
        const tItem = lrc.pop()
        tItem.time = lrc[lrc.length - 1].time
        lrcT.push(tItem)
        lrc.push(item)
      } else {
        lrc.push(item)
        lrcSet.add(item.time)
      }
    }

    if (lrcT.length && lrc.length > lrcT.length) {
      const tItem = lrc.pop()
      tItem.time = lrc[lrc.length - 1].time
      lrcT.push(tItem)
    }

    return {
      lrc,
      lrcT,
    }
  },
  transformLrc(songinfo, lrclist) {
    return `[ti:${songinfo.songName}]\n[ar:${songinfo.artist}]\n[al:${songinfo.album}]\n[by:]\n[offset:0]\n${lrclist ? lrclist.map(l => `[${this.formatTime(l.time)}]${l.lineLyric}\n`).join('') : '暂无歌词'}`
  },
  getLyric(songId) {
    const requestObj = httpFetch(`http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${songId}`)
    requestObj.promise = requestObj.promise.then(({ body }) => {
      // console.log(body)
      if (!body.data?.lrclist?.length) return Promise.reject(new Error('Get lyric failed'))
      let lrcInfo
      try {
        lrcInfo = this.sortLrcArr(body.data.lrclist)
      } catch {
        return Promise.reject(new Error('Get lyric failed'))
      }
      // console.log(body.data.lrclist)
      // console.log(lrcInfo.lrc, lrcInfo.lrcT)
      // console.log({
      //   lyric: decodeName(this.transformLrc(body.data.songinfo, lrc)),
      //   tlyric: decodeName(this.transformLrc(body.data.songinfo, lrcT)),
      // })
      return {
        lyric: decodeName(this.transformLrc(body.data.songinfo, lrcInfo.lrc)),
        tlyric: lrcInfo.lrcT.length ? decodeName(this.transformLrc(body.data.songinfo, lrcInfo.lrcT)) : '',
      }
    })
    return requestObj
  },
}
 */

const buf_key = Buffer.from('yeelion')
const buf_key_len = buf_key.length
const buildParams = (id, isGetLyricx) => {
  let params = `user=12345,web,web,web&requester=localhost&req=1&rid=MUSIC_${id}`
  if (isGetLyricx) params += '&lrcx=1'
  const buf_str = Buffer.from(params)
  const buf_str_len = buf_str.length
  const output = new Uint16Array(buf_str_len)
  let i = 0
  while (i < buf_str_len) {
    let j = 0
    while (j < buf_key_len && i < buf_str_len) {
      output[i] = buf_key[j] ^ buf_str[i]
      i++
      j++
    }
  }
  return Buffer.from(output).toString('base64')
}

// console.log(buildParams('207527604', false))
// console.log(buildParams('207527604', true))

const timeExp = /^\[([\d:.]*)\]{1}/g
const existTimeExp = /\[\d{1,2}:.*\d{1,4}\]/
const lyricxTag = /^<-?\d+,-?\d+>/
return {
  /* sortLrcArr(arr) {
    const lrcSet = new Set()
    let lrc = []
    let lrcT = []
    let markIndex = []
    for (const item of arr) {
      if (lrcSet.has(item.time)) {
        if (lrc.length < 2) continue
        const index = lrc.findIndex(l => l.time == item.time)
        markIndex.push(index)
        if (index == lrc.length - 1) {
          lrcT.push({ ...lrc[index], time: item.time })
          lrc.push(item)
        } else {
          lrcT.push({ ...lrc[index], time: lrc[index + 1].time })
          if (item.text) {
            //   const lastIndex = lrc.length - 1
            //   markIndex.push(lastIndex)
            //   lrcT.push({ ...lrc[lastIndex], time: lrc[lastIndex - 1].time })
            lrc.push(item)
          }
        }
      } else {
        lrc.push(item)
        lrcSet.add(item.time)
      }
    }

    // console.log(markIndex)
    markIndex = Array.from(new Set(markIndex))
    for (let index = markIndex.length - 1; index >= 0; index--) {
      lrc.splice(markIndex[index], 1)
    }

    // if (lrcT.length) {
    //   if (lrc.length * 0.4 < lrcT.length) { // 翻译数量需大于歌词数量的0.4倍，否则认为没有翻译
    //     const tItem = lrc.pop()
    //     tItem.time = lrc[lrc.length - 1].time
    //     lrcT.push(tItem)
    //   } else {
    //     lrc = arr
    //     lrcT = []
    //   }
    // }

    console.log(lrc, lrcT)

    return {
      lrc,
      lrcT,
    }
  }, */
  sortLrcArr(arr) {
    const lrcSet = new Set()
    let lrc = []
    let lrcT = []

    let isLyricx = false
    for (const item of arr) {
      if (lrcSet.has(item.time)) {
        if (lrc.length < 2) continue
        const tItem = lrc.pop()
        tItem.time = lrc[lrc.length - 1].time
        lrcT.push(tItem)
        lrc.push(item)
      } else {
        lrc.push(item)
        lrcSet.add(item.time)
      }
      if (!isLyricx && lyricxTag.test(item.text)) isLyricx = true
    }

    if (!isLyricx && lrcT.length > lrc.length * 0.3 && lrc.length - lrcT.length > 6) {
      throw new Error('failed')
      // if (lrc.length * 0.4 < lrcT.length) { // 翻译数量需大于歌词数量的0.4倍，否则认为没有翻译
      //   const tItem = lrc.pop()
      //   tItem.time = lrc[lrc.length - 1].time
      //   lrcT.push(tItem)
      // } else {
      //   lrc = arr
      //   lrcT = []
      // }
    }

    return {
      lrc,
      lrcT,
    }
  },
  transformLrc(tags, lrclist) {
    return `${tags.join('\n')}\n${lrclist ? lrclist.map(l => `[${l.time}]${l.text}\n`).join('') : '暂无歌词'}`
  },
  parseLrc(lrc) {
    const lines = lrc.split(/\r\n|\r|\n/)
    let tags = []
    let lrcArr = []
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim()
      let result = timeExp.exec(line)
      if (result) {
        const text = line.replace(timeExp, '').trim()
        let time = RegExp.$1
        if (/\.\d\d$/.test(time)) time += '0'
        lrcArr.push({
          time,
          text,
        })
      } else if (lrcTools.rxps.tagLine.test(line)) {
        tags.push(line)
      }
    }
    const lrcInfo = this.sortLrcArr(lrcArr)
    return {
      lyric: decodeName(this.transformLrc(tags, lrcInfo.lrc)),
      tlyric: lrcInfo.lrcT.length ? decodeName(this.transformLrc(tags, lrcInfo.lrcT)) : '',
    }
  },
  // getLyric2(musicInfo, isGetLyricx = true) {
  //   const requestObj = httpFetch(`http://newlyric.kuwo.cn/newlyric.lrc?${buildParams(musicInfo.songmid, isGetLyricx)}`)
  //   requestObj.promise = requestObj.promise.then(({ statusCode, body, raw }) => {
  //     if (statusCode != 200) return Promise.reject(new Error(JSON.stringify(body)))
  //     return decodeLyric({ lrcBase64: raw.toString('base64'), isGetLyricx }).then(base64Data => {
  //       let lrcInfo
  //       console.log(Buffer.from(base64Data, 'base64').toString())
  //       try {
  //         lrcInfo = this.parseLrc(Buffer.from(base64Data, 'base64').toString())
  //       } catch {
  //         return Promise.reject(new Error('Get lyric failed'))
  //       }
  //       if (lrcInfo.tlyric) lrcInfo.tlyric = lrcInfo.tlyric.replace(lrcTools.rxps.wordTimeAll, '')
  //       lrcInfo.lxlyric = lrcTools.parse(lrcInfo.lyric)
  //       // console.log(lrcInfo.lyric)
  //       // console.log(lrcInfo.tlyric)
  //       // console.log(lrcInfo.lxlyric)
  //       // console.log(JSON.stringify(lrcInfo))
  //     })
  //   })
  //   return requestObj
  // },
  getLyric(musicInfo, isGetLyricx = true) {
    // this.getLyric2(musicInfo)
    const requestObj = httpFetch(`http://newlyric.kuwo.cn/newlyric.lrc?${buildParams(musicInfo.songmid, isGetLyricx)}`, {
      cache: false,
      binary: true,
    })
    requestObj.promise = requestObj.promise.then(({ statusCode, body }) => {
      if (statusCode != 200) return Promise.reject(new Error(JSON.stringify(body)))
      return decodeLyric({ lrcBuffer: body, isGetLyricx }).then(base64Data => {
        // let lrcInfo
        // try {
        //   lrcInfo = this.parseLrc(Buffer.from(base64Data, 'base64').toString())
        // } catch {
        //   return Promise.reject(new Error('Get lyric failed'))
        // }
        let lrcInfo
        // console.log(Buffer.from(base64Data, 'base64').toString())
        try {
          lrcInfo = this.parseLrc(Buffer.from(base64Data, 'base64').toString())
        } catch (err) {
          return Promise.reject(new Error('Get lyric failed'))
        }
        // console.log(lrcInfo)
        if (lrcInfo.tlyric) lrcInfo.tlyric = lrcInfo.tlyric.replace(lrcTools.rxps.wordTimeAll, '')
        try {
          lrcInfo.lxlyric = lrcTools.parse(lrcInfo.lyric)
        } catch {
          lrcInfo.lxlyric = ''
        }
        lrcInfo.lyric = lrcInfo.lyric.replace(lrcTools.rxps.wordTimeAll, '')
        if (!existTimeExp.test(lrcInfo.lyric)) return Promise.reject(new Error('Get lyric failed'))
        // console.log(lrcInfo)
        return lrcInfo
      })
    })
    return requestObj
  },
}

})();
const pic = (() => {

return {
  getPic({ songmid }) {
    const requestObj = httpFetch(`http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=500&size=500&rid=${songmid}`)
    requestObj.promise = requestObj.promise.then(({ body }) => /^http/.test(body) ? body : null)
    return requestObj.promise
  },
}

})();
const hotSearch = (() => {

return {
  _requestObj: null,
  async getList(retryNum = 0) {
    if (this._requestObj) this._requestObj.cancelHttp()
    if (retryNum > 2) return Promise.reject(new Error('try max num'))

    const _requestObj = httpFetch('http://hotword.kuwo.cn/hotword.s?prod=kwplayer_ar_9.3.0.1&corp=kuwo&newver=2&vipver=9.3.0.1&source=kwplayer_ar_9.3.0.1_40.apk&p2p=1&notrace=0&uid=0&plat=kwplayer_ar&rformat=json&encoding=utf8&tabid=1', {
      headers: {
        'User-Agent': 'Dalvik/2.1.0 (Linux; U; Android 9;)',
      },
    })
    const { body, statusCode } = await _requestObj.promise
    if (statusCode != 200 || body.status !== 'ok') throw new Error('获取热搜词失败')
    // console.log(body, statusCode)
    return { source: 'kw', list: this.filterList(body.tagvalue) }
  },
  filterList(rawList) {
    return rawList.map(item => item.key)
  },
}

})();
const comment = (() => {

return {
  _requestObj: null,
  _requestObj2: null,
  async getComment({ songmid }, page = 1, limit = 20) {
    if (this._requestObj) this._requestObj.cancelHttp()

    const _requestObj = httpFetch(`http://ncomment.kuwo.cn/com.s?f=web&type=get_comment&aapiver=1&prod=kwplayer_ar_10.5.2.0&digest=15&sid=${songmid}&start=${limit * (page - 1)}&msgflag=1&count=${limit}&newver=3&uid=0`, {
      headers: {
        'User-Agent': 'Dalvik/2.1.0 (Linux; U; Android 9;)',
      },
    })
    const { body, statusCode } = await _requestObj.promise
    if (statusCode != 200 || body.code != '200') throw new Error('获取评论失败')
    // console.log(body)

    const total = body.comments_counts
    return {
      source: 'kw',
      comments: this.filterComment(body.comments),
      total,
      page,
      limit,
      maxPage: Math.ceil(total / limit) || 1,
    }
  },
  async getHotComment({ songmid }, page = 1, limit = 100) {
    if (this._requestObj2) this._requestObj2.cancelHttp()

    const _requestObj2 = httpFetch(`http://ncomment.kuwo.cn/com.s?f=web&type=get_rec_comment&aapiver=1&prod=kwplayer_ar_10.5.2.0&digest=15&sid=${songmid}&start=${limit * (page - 1)}&msgflag=1&count=${limit}&newver=3&uid=0`, {
      headers: {
        'User-Agent': 'Dalvik/2.1.0 (Linux; U; Android 9;)',
      },
    })
    const { body, statusCode } = await _requestObj2.promise
    if (statusCode != 200 || body.code != '200') throw new Error('获取热门评论失败')
    // console.log(body)

    const total = body.hot_comments_counts
    return {
      source: 'kw',
      comments: this.filterComment(body.hot_comments),
      total,
      page,
      limit,
      maxPage: Math.ceil(total / limit) || 1,
    }
  },
  filterComment(rawList) {
    if (!rawList) return []
    return rawList.map(item => {
      return {
        id: item.id,
        text: item.msg,
        time: item.time,
        timeStr: dateFormat2(Number(item.time) * 1000),
        userName: item.u_name,
        avatar: item.u_pic,
        userId: item.u_id,
        likedCount: item.like_num,
        images: item.mpic ? [decodeURIComponent(item.mpic)] : [],
        reply: item.child_comments
          ? item.child_comments.map(i => {
            return {
              id: i.id,
              text: i.msg,
              time: i.time,
              timeStr: dateFormat2(Number(i.time) * 1000),
              userName: i.u_name,
              avatar: i.u_pic,
              userId: i.u_id,
              likedCount: i.like_num,
              images: i.mpic ? [i.mpic] : [],
            }
          })
          : [],
      }
    })
  },
}

})();
const album = (() => {

// let requestObj_list
return {
  limit_list: 36,
  limit_song: 1000,
  filterListDetail(rawList, albumName, albumId) {
    // console.log(rawList)
    // console.log(rawList.length, rawList2.length)
    return rawList.map((item, inedx) => {
      let formats = item.formats.split('|')
      let types = []
      let _types = {}
      if (formats.includes('MP3128')) {
        types.push({ type: '128k', size: null })
        _types['128k'] = {
          size: null,
        }
      }
      // if (formats.includes('MP3192')) {
      //   types.push({ type: '192k', size: null })
      //   _types['192k'] = {
      //     size: null,
      //   }
      // }
      if (formats.includes('MP3H')) {
        types.push({ type: '320k', size: null })
        _types['320k'] = {
          size: null,
        }
      }
      // if (formats.includes('AL')) {
      //   types.push({ type: 'ape', size: null })
      //   _types.ape = {
      //     size: null,
      //   }
      // }
      if (formats.includes('ALFLAC')) {
        types.push({ type: 'flac', size: null })
        _types.flac = {
          size: null,
        }
      }
      if (formats.includes('HIRFLAC')) {
        types.push({ type: 'flac24bit', size: null })
        _types.flac24bit = {
          size: null,
        }
      }
      // types.reverse()
      return {
        singer: formatSinger(decodeName(item.artist)),
        name: decodeName(item.name),
        albumName,
        albumId,
        songmid: item.id,
        source: 'kw',
        interval: null,
        img: item.pic,
        lrc: null,
        otherSource: null,
        types,
        _types,
        typeUrl: {},
      }
    })
  },
  /**
   * 格式化播放数量
   * @param {*} num
   */
  formatPlayCount(num) {
    if (num > 100000000) return parseInt(num / 10000000) / 10 + '亿'
    if (num > 10000) return parseInt(num / 1000) / 10 + '万'
    return num
  },
  getAlbumListDetail(id, page, retryNum = 0) {
    if (retryNum > 2) return Promise.reject(new Error('try max num'))
    const requestObj_listDetail = httpFetch(`http://search.kuwo.cn/r.s?pn=${page - 1}&rn=${this.limit_song}&stype=albuminfo&albumid=${id}&show_copyright_off=0&encoding=utf&vipver=MUSIC_9.1.0`)
    return requestObj_listDetail.promise.then(({ statusCode, body }) => {
      if (statusCode !== 200) return this.getAlbumListDetail(id, page, ++retryNum)
      body = objStr2JSON(body)
      // console.log(body)
      if (!body.musiclist) return this.getAlbumListDetail(id, page, ++retryNum)
      body.name = decodeName(body.name)
      return {
        list: this.filterListDetail(body.musiclist, body.name, body.albumid),
        page,
        limit: this.limit_song,
        total: parseInt(body.songnum),
        source: 'kw',
        info: {
          name: body.name,
          img: body.img || body.hts_img,
          desc: decodeName(body.info),
          author: decodeName(body.artist),
          // play_count: this.formatPlayCount(body.playnum),
        },
      }
    })
  },
  // getAlbumListDetail(id, page, retryNum = 0) {
  //   if (retryNum > 2) return Promise.reject(new Error('try max num'))
  //   return tokenRequest(`http://www.kuwo.cn/api/www/album/albumInfo?albumId=${id}&pn=${page}&rn=${this.limit_song}&httpsStatus=1`).then((resp) => {
  //     return resp.promise.then(({ statusCode, body }) => {
  //       console.log(body)
  //       return Promise.reject(new Error('failed'))
  //       // if (statusCode !== 200) return this.getAlbumListDetail(id, page, ++retryNum)
  //       // const data = body.data
  //       // console.log(data)
  //       // if (!data.musicList) return this.getAlbumListDetail(id, page, ++retryNum)
  //       // return {
  //       //   list: this.filterListDetail(data.musiclist),
  //       //   page,
  //       //   limit: this.limit_song,
  //       //   total: data.total,
  //       //   source: 'kw',
  //       //   info: {
  //       //     name: data.album,
  //       //     img: data.pic,
  //       //     desc: data.albuminfo,
  //       //     author: data.artist,
  //       //     play_count: this.formatPlayCount(data.playCnt),
  //       //   },
  //       // }
  //     })
  //   })
  // },
}

})();
const songList = (() => {

return {
  _requestObj_tags: null,
  _requestObj_hotTags: null,
  _requestObj_list: null,
  limit_list: 36,
  limit_song: 1000,
  successCode: 200,
  sortList: [
    {
      name: '最新',
      tid: 'new',
      id: 'new',
    },
    {
      name: '最热',
      tid: 'hot',
      id: 'hot',
    },
  ],
  regExps: {
    mInfo: /level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/,
    // http://www.kuwo.cn/playlist_detail/2886046289
    // https://m.kuwo.cn/h5app/playlist/2736267853?t=qqfriend
    listDetailLink: /^.+\/playlist(?:_detail)?\/(\d+)(?:\?.*|&.*$|#.*$|$)/,
  },
  tagsUrl: 'http://wapi.kuwo.cn/api/pc/classify/playlist/getTagList?cmd=rcm_keyword_playlist&user=0&prod=kwplayer_pc_9.0.5.0&vipver=9.0.5.0&source=kwplayer_pc_9.0.5.0&loginUid=0&loginSid=0&appUid=76039576',
  hotTagUrl: 'http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmTagList?loginUid=0&loginSid=0&appUid=76039576',
  getListUrl({ sortId, id, type, page }) {
    if (!id) return `http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576&&pn=${page}&rn=${this.limit_list}&order=${sortId}`
    switch (type) {
      case '10000': return `http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576&pn=${page}&id=${id}&rn=${this.limit_list}`
      case '43': return `http://mobileinterfaces.kuwo.cn/er.s?type=get_pc_qz_data&f=web&id=${id}&prod=pc`
    }
    // http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576&id=173&pn=1&rn=100
  },
  getListDetailUrl(id, page) {
    // http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=2858093057&pn=0&rn=100&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1
    return `http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${id}&pn=${page - 1}&rn=${this.limit_song}&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1`
    // http://mobileinterfaces.kuwo.cn/er.s?type=get_pc_qz_data&f=web&id=140&prod=pc
  },

  // http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=2849349915&pn=0&rn=100&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1
  // 获取标签
  getTag(tryNum = 0) {
    if (this._requestObj_tags) this._requestObj_tags.cancelHttp()
    if (tryNum > 2) return Promise.reject(new Error('try max num'))
    this._requestObj_tags = httpFetch(this.tagsUrl)
    return this._requestObj_tags.promise.then(({ body }) => {
      if (body.code !== this.successCode) return this.getTag(++tryNum)
      return this.filterTagInfo(body.data)
    })
  },
  // 获取标签
  getHotTag(tryNum = 0) {
    if (this._requestObj_hotTags) this._requestObj_hotTags.cancelHttp()
    if (tryNum > 2) return Promise.reject(new Error('try max num'))
    this._requestObj_hotTags = httpFetch(this.hotTagUrl)
    return this._requestObj_hotTags.promise.then(({ body }) => {
      if (body.code !== this.successCode) return this.getHotTag(++tryNum)
      return this.filterInfoHotTag(body.data[0].data)
    })
  },
  filterInfoHotTag(rawList) {
    return rawList.map(item => ({
      id: `${item.id}-${item.digest}`,
      name: item.name,
      source: 'kw',
    }))
  },
  filterTagInfo(rawList) {
    return rawList.map(type => ({
      name: type.name,
      list: type.data.map(item => ({
        parent_id: type.id,
        parent_name: type.name,
        id: `${item.id}-${item.digest}`,
        name: item.name,
        source: 'kw',
      })),
    }))
  },

  // 获取列表数据
  getList(sortId, tagId, page, tryNum = 0) {
    if (this._requestObj_list) this._requestObj_list.cancelHttp()
    if (tryNum > 2) return Promise.reject(new Error('try max num'))
    let id
    let type
    if (tagId) {
      let arr = tagId.split('-')
      id = arr[0]
      type = arr[1]
    } else {
      id = null
    }
    this._requestObj_list = httpFetch(this.getListUrl({ sortId, id, type, page }))
    return this._requestObj_list.promise.then(({ body }) => {
      if (!id || type == '10000') {
        if (body.code !== this.successCode) return this.getList(sortId, tagId, page, ++tryNum)
        return {
          list: this.filterList(body.data.data),
          total: body.data.total,
          page: body.data.pn,
          limit: body.data.rn,
          source: 'kw',
        }
      } else if (!body.length) {
        return this.getList(sortId, tagId, page, ++tryNum)
      }
      return {
        list: this.filterList2(body),
        total: 1000,
        page,
        limit: 1000,
        source: 'kw',
      }
    })
  },


  /**
   * 格式化播放数量
   * @param {*} num
   */
  formatPlayCount(num) {
    if (num > 100000000) return parseInt(num / 10000000) / 10 + '亿'
    if (num > 10000) return parseInt(num / 1000) / 10 + '万'
    return num
  },
  filterList(rawData) {
    return rawData.map(item => ({
      play_count: this.formatPlayCount(item.listencnt),
      id: `digest-${item.digest}__${item.id}`,
      author: item.uname,
      name: item.name,
      // time: item.publish_time,
      total: item.total,
      img: item.img,
      grade: item.favorcnt / 10,
      desc: item.desc,
      source: 'kw',
    }))
  },
  filterList2(rawData) {
    // console.log(rawData)
    const list = []
    rawData.forEach(item => {
      if (!item.label) return
      list.push(...item.list.map(item => ({
        play_count: item.play_count && this.formatPlayCount(item.listencnt),
        id: `digest-${item.digest}__${item.id}`,
        author: item.uname,
        name: item.name,
        total: item.total,
        // time: item.publish_time,
        img: item.img,
        grade: item.favorcnt && item.favorcnt / 10,
        desc: item.desc,
        source: 'kw',
      })))
    })
    return list
  },

  getListDetailDigest8(id, page, tryNum = 0) {
    if (tryNum > 2) return Promise.reject(new Error('try max num'))

    const requestObj = httpFetch(this.getListDetailUrl(id, page))
    return requestObj.promise.then(({ body }) => {
      if (body.result !== 'ok') return this.getListDetail(id, page, ++tryNum)
      return {
        list: this.filterListDetail(body.musiclist),
        page,
        limit: body.rn,
        total: body.total,
        source: 'kw',
        info: {
          name: body.title,
          img: body.pic,
          desc: body.info,
          author: body.uname,
          play_count: this.formatPlayCount(body.playnum),
        },
      }
    })
  },
  getListDetailDigest5Info(id, tryNum = 0) {
    if (tryNum > 2) return Promise.reject(new Error('try max num'))
    const requestObj = httpFetch(`http://qukudata.kuwo.cn/q.k?op=query&cont=ninfo&node=${id}&pn=0&rn=1&fmt=json&src=mbox&level=2`)
    return requestObj.promise.then(({ statusCode, body }) => {
      if (statusCode != 200 || !body.child) return this.getListDetail(id, ++tryNum)
      // console.log(body)
      return body.child.length ? body.child[0].sourceid : null
    })
  },
  getListDetailDigest5Music(id, page, tryNum = 0) {
    if (tryNum > 2) return Promise.reject(new Error('try max num'))
    const requestObj = httpFetch(`http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${id}&pn=${page - 1}}&rn=${this.limit_song}&encode=utf-8&keyset=pl2012&identity=kuwo&pcmp4=1`)
    return requestObj.promise.then(({ body }) => {
      // console.log(body)
      if (body.result !== 'ok') return this.getListDetail(id, page, ++tryNum)
      return {
        list: this.filterListDetail(body.musiclist),
        page,
        limit: body.rn,
        total: body.total,
        source: 'kw',
        info: {
          name: body.title,
          img: body.pic,
          desc: body.info,
          author: body.uname,
          play_count: this.formatPlayCount(body.playnum),
        },
      }
    })
  },
  async getListDetailDigest5(id, page, retryNum) {
    const detailId = await this.getListDetailDigest5Info(id, retryNum)
    return this.getListDetailDigest5Music(detailId, page, retryNum)
  },

  filterBDListDetail(rawList) {
    return rawList.map(item => {
      let types = []
      let _types = {}
      for (let info of item.audios) {
        info.size = info.size?.toLocaleUpperCase()
        switch (info.bitrate) {
          case '4000':
            types.push({ type: 'flac24bit', size: info.size })
            _types.flac24bit = {
              size: info.size,
            }
            break
          case '2000':
            types.push({ type: 'flac', size: info.size })
            _types.flac = {
              size: info.size,
            }
            break
          case '320':
            types.push({ type: '320k', size: info.size })
            _types['320k'] = {
              size: info.size,
            }
            break
          case '128':
            types.push({ type: '128k', size: info.size })
            _types['128k'] = {
              size: info.size,
            }
            break
        }
      }
      types.reverse()

      return {
        singer: item.artists.map(s => s.name).join('、'),
        name: item.name,
        albumName: item.album,
        albumId: item.albumId,
        songmid: item.id,
        source: 'kw',
        interval: formatPlayTime(item.duration),
        img: item.albumPic,
        releaseDate: item.releaseDate,
        lrc: null,
        otherSource: null,
        types,
        _types,
        typeUrl: {},
      }
    })
  },
  getReqId() {
    function t() {
      return (65536 * (1 + Math.random()) | 0).toString(16).substring(1)
    }
    return t() + t() + t() + t() + t() + t() + t() + t()
  },
  async getListDetailMusicListByBDListInfo(id, source) {
    const { body: infoData } = await httpFetch(`https://bd-api.kuwo.cn/api/service/playlist/info/${id}?reqId=${this.getReqId()}&source=${source}`, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36',
        plat: 'h5',
      },
    }).promise.catch(() => ({ code: 0 }))

    if (infoData.code != 200) return null

    return {
      name: infoData.data.name,
      img: infoData.data.pic,
      desc: infoData.data.description,
      author: infoData.data.creatorName,
      play_count: infoData.data.playNum,
    }
  },
  async getListDetailMusicListByBDUserPub(id) {
    const { body: infoData } = await httpFetch(`https://bd-api.kuwo.cn/api/ucenter/users/pub/${id}?reqId=${this.getReqId()}`, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36',
        plat: 'h5',
      },
    }).promise.catch(() => ({ code: 0 }))

    if (infoData.code != 200) return null

    // console.log(infoData)
    return {
      name: infoData.data.userInfo.nickname + '喜欢的音乐',
      img: infoData.data.userInfo.headImg,
      desc: '',
      author: infoData.data.userInfo.nickname,
      play_count: '',
    }
  },
  async getListDetailMusicListByBDList(id, source, page, tryNum = 0) {
    const { body: listData } = await httpFetch(`https://bd-api.kuwo.cn/api/service/playlist/${id}/musicList?reqId=${this.getReqId()}&source=${source}&pn=${page}&rn=${this.limit_song}`, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36',
        plat: 'h5',
      },
    }).promise.catch(() => {
      if (tryNum > 2) return Promise.reject(new Error('try max num'))
      return this.getListDetailMusicListByBDList(id, source, page, ++tryNum)
    })

    if (listData.code !== 200) return Promise.reject(new Error('failed'))

    return {
      list: this.filterBDListDetail(listData.data.list),
      page,
      limit: listData.data.pageSize,
      total: listData.data.total,
      source: 'kw',
    }
  },
  async getListDetailMusicListByBD(id, page) {
    const uid = /uid=(\d+)/.exec(id)?.[1]
    const listId = /playlistId=(\d+)/.exec(id)?.[1]
    const source = /source=(\d+)/.exec(id)?.[1]
    if (!listId) return Promise.reject(new Error('failed'))

    const task = [this.getListDetailMusicListByBDList(listId, source, page)]
    switch (source) {
      case '4':
        task.push(this.getListDetailMusicListByBDListInfo(listId, source))
        break
      case '5':
        task.push(this.getListDetailMusicListByBDUserPub(uid ?? listId))
        break
    }
    const [listData, info] = await Promise.all(task)
    listData.info = info ?? {
      name: '',
      img: '',
      desc: '',
      author: '',
      play_count: '',
    }
    // console.log(listData)
    return listData
  },

  // 获取歌曲列表内的音乐
  getListDetail(id, page, retryNum = 0) {
    // console.log(id)
    // https://h5app.kuwo.cn/m/bodian/collection.html?uid=000&playlistId=000&source=5&ownerId=000
    // https://h5app.kuwo.cn/m/bodian/collection.html?uid=000&playlistId=000&source=4&ownerId=
    if (/\/bodian\//.test(id)) return this.getListDetailMusicListByBD(id, page)
    if ((/[?&:/]/.test(id))) id = id.replace(this.regExps.listDetailLink, '$1')
    else if (/^digest-/.test(id)) {
      let [digest, _id] = id.split('__')
      digest = digest.replace('digest-', '')
      id = _id
      switch (digest) {
        case '8':
          break
        case '13': return album.getAlbumListDetail(id, page, retryNum)
        case '5':
        default: return this.getListDetailDigest5(id, page, retryNum)
      }
    }
    return this.getListDetailDigest8(id, page, retryNum)
  },
  filterListDetail(rawData) {
    // console.log(rawData)
    return rawData.map(item => {
      let infoArr = item.N_MINFO.split(';')
      let types = []
      let _types = {}
      for (let info of infoArr) {
        info = info.match(this.regExps.mInfo)
        if (info) {
          switch (info[2]) {
            case '4000':
              types.push({ type: 'flac24bit', size: info[4] })
              _types.flac24bit = {
                size: info[4].toLocaleUpperCase(),
              }
              break
            case '2000':
              types.push({ type: 'flac', size: info[4] })
              _types.flac = {
                size: info[4].toLocaleUpperCase(),
              }
              break
            case '320':
              types.push({ type: '320k', size: info[4] })
              _types['320k'] = {
                size: info[4].toLocaleUpperCase(),
              }
              break
            case '128':
              types.push({ type: '128k', size: info[4] })
              _types['128k'] = {
                size: info[4].toLocaleUpperCase(),
              }
              break
          }
        }
      }
      types.reverse()

      return {
        singer: formatSinger(decodeName(item.artist)),
        name: decodeName(item.name),
        albumName: decodeName(item.album),
        albumId: item.albumid,
        songmid: item.id,
        source: 'kw',
        interval: formatPlayTime(parseInt(item.duration)),
        img: null,
        lrc: null,
        otherSource: null,
        types,
        _types,
        typeUrl: {},
      }
    })
  },
  getTags() {
    return Promise.all([this.getTag(), this.getHotTag()]).then(([tags, hotTag]) => ({ tags, hotTag, source: 'kw' }))
  },
  getDetailPageUrl(id) {
    if ((/[?&:/]/.test(id))) id = id.replace(this.regExps.listDetailLink, '$1')
    else if (/^digest-/.test(id)) {
      let result = id.split('__')
      id = result[1]
    }
    return `http://www.kuwo.cn/playlist_detail/${id}`
  },

  search(text, page, limit = 20) {
    return httpFetch(`http://search.kuwo.cn/r.s?all=${encodeURIComponent(text)}&pn=${page - 1}&rn=${limit}&rformat=json&encoding=utf8&ver=mbox&vipver=MUSIC_8.7.7.0_BCS37&plat=pc&devid=28156413&ft=playlist&pay=0&needliveshow=0`)
      .promise.then(({ body }) => {
        body = objStr2JSON(body)
        // console.log(body)
        return {
          list: body.abslist.map(item => {
            return {
              play_count: this.formatPlayCount(item.playcnt),
              id: item.playlistid,
              author: decodeName(item.nickname),
              name: decodeName(item.name),
              total: item.songnum,
              // time: item.publish_time,
              img: item.pic,
              desc: decodeName(item.intro),
              source: 'kw',
            }
          }),
          limit,
          total: parseInt(body.TOTAL),
          source: 'kw',
        }
      })
  },
}

// getList
// getTags
// getListDetail

})();
const kw = (() => {

const kw = {
  _musicInfoRequestObj: null,
  _musicInfoPromiseCancelFn: null,
  _musicPicRequestObj: null,
  _musicPicPromiseCancelFn: null,
  // context: null,


  // init(context) {
  //   if (this.isInited) return
  //   this.isInited = true
  //   this.context = context

  //   // this.musicSearch.search('我又想你了').then(res => {
  //   //   console.log(res)
  //   // })

  //   // this.getMusicUrl('62355680', '320k').then(url => {
  //   //   console.log(url)
  //   // })
  // },

  tipSearch,
  musicSearch,
  leaderboard,
  songList,
  hotSearch,
  comment,
  getLyric(songInfo, isGetLyricx) {
    // let singer = songInfo.singer.indexOf('、') > -1 ? songInfo.singer.split('、')[0] : songInfo.singer
    return lyric.getLyric(songInfo, isGetLyricx)
  },
  handleMusicInfo(songInfo) {
    return this.getMusicInfo(songInfo).then(info => {
      // console.log(JSON.stringify(info))
      songInfo.name = info.name
      songInfo.singer = formatSinger(info.artist)
      songInfo.img = info.pic
      songInfo.albumName = info.album
      return songInfo
      // return Object.assign({}, songInfo, {
      //   name: info.name,
      //   singer: formatSinger(info.artist),
      //   img: info.pic,
      //   albumName: info.album,
      // })
    })
  },

  getMusicUrl(songInfo, type) {
    return apis('kw').getMusicUrl(songInfo, type)
  },

  getMusicInfo(songInfo) {
    if (this._musicInfoRequestObj) this._musicInfoRequestObj.cancelHttp()
    this._musicInfoRequestObj = httpFetch(`http://www.kuwo.cn/api/www/music/musicInfo?mid=${songInfo.songmid}`)
    return this._musicInfoRequestObj.promise.then(({ body }) => {
      return body.code === 200 ? body.data : Promise.reject(new Error(body.msg))
    })
  },

  getMusicUrls(musicInfo, cb) {
    let tasks = []
    let songId = musicInfo.songmid
    musicInfo.types.forEach(type => {
      tasks.push(kw.getMusicUrl(songId, type.type).promise)
    })
    Promise.all(tasks).then(urlInfo => {
      let typeUrl = {}
      urlInfo.forEach(info => {
        typeUrl[info.type] = info.url
      })
      cb(typeUrl)
    })
  },

  getPic(songInfo) {
    return pic.getPic(songInfo)
  },

  getMusicDetailPageUrl(songInfo) {
    return `http://www.kuwo.cn/play_detail/${songInfo.songmid}`
  },

  // init() {
  //   return getToken()
  // },
}

return kw

})();

const apis = () => ({ getMusicUrl: async () => { throw new Error('内置取链已禁用：请导入 userApi 脚本') } })
globalThis.__zlKw = kw
