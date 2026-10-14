// RN 原生 crypto 模块的 Node 等价实现
// 契约（源：CryptoModule.java + AES.java）：text/key/iv 均为 base64；
// encrypt 输出 base64；decrypt 输出 utf8 明文。AES_MODE 对应 Java cipher 名。
import { createCipheriv, createDecipheriv } from 'node:crypto'

export const AES_MODE = {
  CBC_128_PKCS7Padding: 'AES-128-CBC',
  ECB_128_NoPadding: 'aes-128-ecb',
}

const b64d = (s) => Buffer.from(s, 'base64')
const b64e = (b) => Buffer.from(b).toString('base64')

// Node 的 aes-128-ecb 不自动去 PKCS7——Java NoPadding 语义 = 原样返回，
// kw 的 wbdCrypto 场景密文本身不带 padding，直接解即可
export const aesDecryptSync = (text, key, iv, mode) => {
  const data = b64d(text)
  const k = b64d(key)
  const algo = mode === 'aes-128-ecb' ? 'aes-128-ecb' : 'aes-128-cbc'
  if (algo === 'aes-128-ecb') {
    const d = createDecipheriv(algo, k, algo === 'aes-128-ecb' ? null : b64d(iv))
    return Buffer.concat([d.update(data), d.final()]).toString('utf8')
  }
  
  return Buffer.concat([d.update(data), d.final()]).toString('utf8')
}

export const aesEncryptSync = (text, key, iv, mode) => {
  const data = b64d(text)
  const k = b64d(key)
  if (mode === 'aes-128-ecb') {
    const c = createCipheriv('aes-128-ecb', k, null)
    return b64e(Buffer.concat([c.update(data), c.final()]))
  }
  const c = createCipheriv('aes-128-cbc', k, b64d(iv))
  return b64e(Buffer.concat([c.update(data), c.final()]))
}
