package com.applemusicktv.data.source

/**
 * ZL 自定义音源接口（Phase 1，见 docs/ZL_ROADMAP.md）。
 *
 * 目标：把「音乐数据从哪来」从 Apple Music 专用实现中抽象出来。
 * 现有 Apple 数据层（ProxyApi / Direct*）后续降级为该接口的一种实现；
 * lx-music userApi 引擎（QuickJS 设备端 / 局域网转译桥）作为新实现接入。
 *
 * 设计约束：
 * - 只覆盖 UI 真正消费的能力（搜索 / 取播放链 / 歌词 / 榜单），不贪大
 * - Song 映射到 app 现有 data.model.Song（id 用 "sourceId:songId" 前缀防撞）
 * - 全部 suspend + 返回可空/Result：音源是网络世界，任何失败都不能炸 UI
 */
interface MusicSource {

    /** 稳定标识（如 "kw"、"lx:<脚本名>"） */
    val id: String

    /** 展示名（如 "酷我"、"聚合"） */
    val displayName: String

    /** 该源是否支持榜单（Home/排行榜页按能力降级展示） */
    val supportsBoards: Boolean get() = false

    /** 搜索：歌曲为主，专辑/艺人等富结果 Phase 2 再扩 */
    suspend fun search(
        keyword: String,
        page: Int = 1,
        limit: Int = 30,
    ): SourceSearchResult

    /**
     * 取播放地址。返回可直接给 ExoPlayer 的 http(s) URL；
     * 取不到返回 null（调用方负责跳过/换源重试，语义对齐 lx 的「取链失败」）。
     */
    suspend fun getStreamUrl(song: SourceSong, quality: String? = null): String?

    /** 歌词：返回行级歌词（时间戳 + 文本）；无歌词返回 null */
    suspend fun getLyrics(song: SourceSong): SourceLyrics?
}

/** 可选能力：榜单。不支持的源 [MusicSource.supportsBoards] 为 false 且不实现本接口 */
interface BoardCapableSource {
    suspend fun getBoards(): List<SourceBoard>
    suspend fun getBoardDetail(boardId: String, page: Int = 1): SourceSearchResult
}

// ── 传输模型（与 app 的 UI 模型解耦，映射发生在仓库层） ─────────────────

data class SourceSong(
    /** 源内歌曲 id（不含源前缀） */
    val songId: String,
    /** 所属源 id */
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    /** 毫秒；拿不到就 0（UI 显示 "—"） */
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
) {
    /** 全局唯一 id："kw:12345"——防止与 Apple catalog id 及其他源撞车 */
    val globalId: String get() = "$sourceId:$songId"
}

data class SourceSearchResult(
    val songs: List<SourceSong> = emptyList(),
    val total: Int = songs.size,
    val page: Int = 1,
    val maxPage: Int = 1,
    val limit: Int = 30,
)

data class SourceLyricLine(
    /** 毫秒时间戳 */
    val timeMs: Long,
    val text: String,
)

data class SourceLyrics(
    val lines: List<SourceLyricLine> = emptyList(),
    /** 是否逐字（YRC/QRC）；当前 UI 按行展示，逐字数据 Phase 2 再利用 */
    val wordLevel: Boolean = false,
)

data class SourceBoard(
    val id: String,
    val name: String,
    /** 封面，可空 */
    val artworkUrl: String? = null,
)
