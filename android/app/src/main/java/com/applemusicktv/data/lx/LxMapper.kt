package com.applemusicktv.data.lx

import android.util.Base64
import com.applemusicktv.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * LX 引擎数据 ↔ app 模型映射。
 * 复合 songId：zl1.<base64url(JSON)>，元数据编进 id —— 队列/断点/重启后无会话也能还原。
 */
object LxMapper {

    fun encodeSongId(songmid: String, name: String, singer: String, album: String, sec: Int, img: String?): String {
        val payload = JSONObject()
            .put("i", songmid).put("n", name).put("s", singer)
            .put("al", album).put("d", sec).put("p", img ?: "")
        val raw = payload.toString().toByteArray(Charsets.UTF_8)
        return "zl1." + Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decodeSongId(id: String): JSONObject? = runCatching {
        if (!id.startsWith("zl1.")) return null
        val raw = Base64.decode(id.removePrefix("zl1."), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(raw, Charsets.UTF_8))
    }.getOrNull()

    fun isLxId(id: String): Boolean = id.startsWith("zl1.")
    fun isBoardId(id: String): Boolean = id.startsWith("zlboard:")

    /** kw interval "m:ss" → 秒 */
    private fun intervalSec(v: Any?): Int {
        val s = v?.toString() ?: return 0
        return if (s.contains(':')) {
            val p = s.split(':')
            (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toDoubleOrNull()?.toInt() ?: 0)
        } else s.toIntOrNull() ?: 0
    }

    /** 引擎 search 结果（lx list item）→ app Song */
    fun songFromLx(o: JSONObject): Song = Song(
        id = encodeSongId(
            o.optString("songmid"),
            o.optString("name"),
            o.optString("singer"),
            o.optString("albumName"),
            intervalSec(o.opt("interval")),
            o.optString("img").ifBlank { null },
        ),
        title = o.optString("name"),
        artistName = o.optString("singer"),
        albumName = o.optString("albumName"),
        durationMs = intervalSec(o.opt("interval")) * 1000L,
        artworkUrl = o.optString("img").ifBlank { null },
        artworkBgColor = null,
        previewUrl = null,
        hasLyrics = true,
        trackNumber = null,
        genreNames = emptyList(),
        artistId = null,
        albumId = null,
        type = "songs",
    )

    fun parseSongs(resultJson: String): List<Song> = runCatching {
        val root = JSONObject(resultJson)
        if (root.has("__err")) return emptyList()
        val arr = root.optJSONArray("list") ?: JSONArray()
        (0 until arr.length()).map { songFromLx(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun parseBoards(resultJson: String): List<Pair<String, String>> = runCatching {
        // 榜单列表：[{id,name,bangid,...}]（取 bangid+name）
        val arr = JSONArray(resultJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            o.optString("bangid") to o.optString("name")
        }
    }.getOrDefault(emptyList())

    fun parseLyrics(resultJson: String): String = runCatching {
        val root = JSONObject(resultJson)
        val arr = root.optJSONArray("lines") ?: JSONArray()
        buildString {
            for (i in 0 until arr.length()) {
                val line = arr.getJSONObject(i)
                val ms = line.optLong("time", 0)
                val totalSec = ms / 1000.0
                val min = (totalSec / 60).toInt()
                val sec = totalSec - min * 60
                append('[').append(min).append(':')
                append(String.format("%.2f", sec)).append(']')
                append(line.optString("text")).append('\n')
            }
        }
    }.getOrDefault("")

    /** 取链入参的 lx musicInfo JSON（从复合 id 还原） */
    fun musicInfoJson(id: String): String? {
        val m = decodeSongId(id) ?: return null
        return JSONObject()
            .put("songmid", m.optString("i"))
            .put("name", m.optString("n"))
            .put("singer", m.optString("s"))
            .put("albumName", m.optString("al"))
            .put("interval", m.optInt("d"))
            .put("source", "kw")
            .toString()
    }
}
