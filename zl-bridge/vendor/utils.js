// toMD5 垫片（原文件用 react-native-quick-md5；Node 直接 crypto）
import { createHash } from 'node:crypto'
export const toMD5 = (str) => createHash('md5').update(String(str)).digest('hex')
