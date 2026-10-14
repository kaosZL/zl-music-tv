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

### Phase 2 —— lx 音源引擎（核心攻坚）
- 方案 A：内嵌 QuickJS 运行 lx userApi 脚本（设备端独立）
- 方案 B：复用原项目 proxy-server 思路，局域网跑 lx-music-source（Node）做转译桥
- [ ] 音源管理页（导入 .js 脚本 / 扫码导入——复用 ZL_Musicos 的 NanoHTTPD 经验）
- [ ] 搜索 / 取播放链 / 歌词 三个核心接口打通

### Phase 3 —— 播放与歌单落地
- [ ] ExoPlayer 直接播 http(s) 音频流（去掉 Widevine/EXT-X-KEY 改写链路）
- [ ] 歌单库（本地持久化）+ 我的收藏
- [ ] 队列/切歌/断点续播

### Phase 4 —— 品牌与发布
- [ ] 改名/图标/banner 全面 ZL 化，删 Apple 相关遥测与更新检查
- [ ] 发版流水线（版本号 + 构建标记纪律沿用 ZL_Musicos）

## 许可证
保持 GPL-3.0（fork 义务），保留上游版权声明（THIRD_PARTY_NOTICES.md 一并保留）。
