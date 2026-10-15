# ZL Music TV 改造路线图

原项目：JObersi10/apple-music-tv（Apple Music 官方 API + Widevine）。
改造目标：**外壳不变**（全部 UI / 焦点 / 播放页交互保留），**数据层替换**为自定义音源。

## 阶段

### Phase 0 —— fork 跑通（当前）
- [x] fork + CI 出 APK
- [ ] 电视实装：确认外壳、导航、播放页在目标电视上表现

### Phase 1 —— 数据层接口勘察与抽象
- [ ] 摸清 `data/datasource` / `data/repository` / `data/network` 的接口面（搜索/取链/歌词/榜单/专辑封面）
- [ ] 定义 `MusicSource` Kotlin 接口，把 Apple 实现降级为其中一种 Source
- [ ] App 启动不再强依赖 proxy / MUT（无源时进空态，不卡启动）

### Phase 2 —— lx 音源引擎（✅ 2026-10-15 落地，方案 A 设备端）
- [x] QuickJS（quickjs-kt）内嵌引擎：酷我 SDK（搜索/榜单/歌词）+ lx userApi 脚本宿主，JS 载荷 Node 六项预验证
- [x] 洛雪式导入：设置页「自定义音源」区块（URL 导入/启停/删除），手机网页服务器也留了导入路由
- [x] 搜索 / 取播放链（320k→128k→flac 降级）/ 歌词 三接口打通；取流走 app 内置 8080 /zlstream 路由 302 直链
- [ ] 待办：手机网页端「粘贴脚本」卡片、歌单收藏（Phase 3 一起）、更多平台 SDK（kg/wy/tx）

### Phase 3 —— 播放与歌单落地
- [ ] ExoPlayer 直接播 http(s) 音频流（去掉 Widevine/EXT-X-KEY 改写链路）
- [ ] 歌单库（本地持久化）+ 我的收藏
- [ ] 队列/切歌/断点续播

### Phase 4 —— 品牌与发布
- [ ] 改名/图标/banner 全面 ZL 化，删 Apple 相关遥测与更新检查
- [ ] 发版流水线（版本号 + 构建标记纪律沿用 ZL_Musicos）

## 许可证
保持 GPL-3.0（fork 义务），保留上游版权声明（THIRD_PARTY_NOTICES.md 一并保留）。
