# ZL Bridge —— lx 音源 → Apple Music TV 代理协议桥

让 [zl-music-tv](../README.md) app 不改一行代码就用上自定义音源：app 的「代理服务器」指向本服务即可。

- **数据引擎**：`vendor/kw`（酷我搜索/榜单/歌词，自 ZL_Musicos musicSdk 原样移植 + 4 个 Node 垫片）
- **取链引擎**：`scripts/juhe.js`（lx userApi 聚合脚本）经 `userapi-host.js`（node:vm 沙箱宿主）解析 320k/128k/flac
- **协议**：与 app 端 `ProxyApi.kt` 对齐（search / stream 302 / lyrics / home 榜单货架 / auth 打桩）
- **鉴权**：请求头 `X-Music-User-Token` 必须等于 `ZL_TOKEN`（app 把你在 8080 页设置的 token 原样发来——那个「假 token」就是访问密钥）
- **无状态**：歌曲元数据 base64 编进 songId（`zl1.` 前缀），服务重启不丢队列

## 部署（本机已是 systemd --user 服务 zl-bridge）

```bash
cd zl-bridge
npm ci                        # 仅 iconv-lite + pako
npx esbuild server.js --bundle --platform=node --format=cjs --outfile=server.bundle.cjs
systemctl --user restart zl-bridge
# 端口/密钥在 ~/.config/systemd/user/zl-bridge.service（ZL_PORT / ZL_TOKEN）
```

## 接入（电视端）

1. app 设置（⚙）→ 连接 → 服务器地址填 `http://<服务器IP>:8081/`
2. 手机访问 `http://<电视IP>:8080`，Music-User-Token 填 ZL_TOKEN 的值
3. 重启 app：首页变酷我榜单货架、搜索走酷我、播放/歌词全链路走桥
