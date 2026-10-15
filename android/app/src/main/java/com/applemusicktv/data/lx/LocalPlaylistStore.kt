package com.applemusicktv.data.lx

import android.content.Context
import com.applemusicktv.data.model.Song
import com.applemusicktv.data.network.PlaylistDto
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地歌单存储（ZL 自有体系，不依赖 Apple 账号）。
 *
 * 结构：SharedPreferences("zl_playlists") 里的一个 JSON 数组
 * [{id:"zlpl:<uuid>", name, songs:[{i,t,s,al,d,p}]}]；歌曲本体只存展示字段——
 * zl1. 复合 id 自带全部元数据，取链/歌词随时可还原。
 *
 * 「我的收藏」= 固定歌单 [FAV_ID]，永远排第一、不可删除。
 */
@Singleton
class LocalPlaylistStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("zl_playlists", Context.MODE_PRIVATE)

    companion object {
        const val FAV_ID = "zlpl:fav"
        const val FAV_NAME = "我的收藏"
        private const val KEY = "data"
    }

    // ── 序列化 ──

    private fun songToJson(s: Song) = JSONObject()
        .put("i", s.id).put("t", s.title).put("s", s.artistName)
        .put("al", s.albumName).put("d", s.durationMs).put("p", s.artworkUrl ?: "")

    private fun jsonToSong(o: JSONObject) = Song(
        id = o.optString("i"), title = o.optString("t"), artistName = o.optString("s"),
        albumName = o.optString("al"), durationMs = o.optLong("d"),
        artworkUrl = o.optString("p").ifBlank { null }, artworkBgColor = null,
        previewUrl = null, hasLyrics = true, trackNumber = null, genreNames = emptyList(),
        artistId = null, albumId = null, type = "songs",
    )

    private fun readAll(): JSONArray = runCatching {
        JSONArray(prefs.getString(KEY, null) ?: "[]")
    }.getOrDefault(JSONArray())

    private fun writeAll(arr: JSONArray) {
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun JSONObject.plName(): String = optString("n")
    private fun JSONObject.plId(): String = optString("id")

    // ── 对外 API ──

    /** 歌单列表（我的收藏置顶）；空列表时也保证收藏歌单存在 */
    fun lists(): List<PlaylistDto> {
        val arr = readAll()
        val result = mutableListOf<PlaylistDto>()
        result += PlaylistDto(
            id = FAV_ID, name = FAV_NAME, curatorName = "我",
            artworkUrl = coverOf(FAV_ID), artworkBgColor = null,
            description = null, playlistType = null,
        )
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result += PlaylistDto(
                id = o.plId(), name = o.plName(), curatorName = "我",
                artworkUrl = coverOf(o.plId()), artworkBgColor = null,
                description = null, playlistType = null,
            )
        }
        return result
    }

    fun songs(playlistId: String): List<Song> {
        if (playlistId == FAV_ID) {
            // 收藏歌单单独存，避免用户歌单数组里混入
            return runCatching {
                val arr = JSONArray(prefs.getString(FAV_ID, "[]"))
                (0 until arr.length()).map { jsonToSong(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
        val arr = readAll()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.plId() == playlistId) {
                val songs = o.optJSONArray("songs") ?: JSONArray()
                return (0 until songs.length()).map { jsonToSong(songs.getJSONObject(it)) }
            }
        }
        return emptyList()
    }

    /** 加歌（去重）。返回「新加入首数」，-1 表示歌单不存在 */
    fun addTo(playlistId: String, songs: List<Song>): Int {
        val existing = songs(playlistId).map { it.id }.toHashSet()
        val fresh = songs.filter { it.id !in existing }
        if (playlistId == FAV_ID) {
            val arr = runCatching { JSONArray(prefs.getString(FAV_ID, "[]")) }.getOrDefault(JSONArray())
            fresh.forEach { arr.put(songToJson(it)) }
            prefs.edit().putString(FAV_ID, arr.toString()).apply()
            return fresh.size
        }
        val arr = readAll()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.plId() == playlistId) {
                val list = o.optJSONArray("songs") ?: JSONArray()
                fresh.forEach { list.put(songToJson(it)) }
                o.put("songs", list)
                writeAll(arr)
                return fresh.size
            }
        }
        return -1
    }

    /** 新建歌单（自动命名），返回新歌单 */
    fun create(): PlaylistDto {
        val id = "zlpl:" + java.util.UUID.randomUUID().toString().take(8)
        val name = "歌单 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val arr = readAll()
        arr.put(JSONObject().put("id", id).put("n", name).put("songs", JSONArray()))
        writeAll(arr)
        return PlaylistDto(
            id = id, name = name, curatorName = "我",
            artworkUrl = null, artworkBgColor = null, description = null, playlistType = null,
        )
    }

    fun remove(playlistId: String) {
        if (playlistId == FAV_ID) return
        val arr = readAll()
        val out = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).plId() != playlistId) out.put(arr.getJSONObject(i))
        writeAll(out)
    }

    fun removeSong(playlistId: String, songId: String) {
        if (playlistId == FAV_ID) {
            val arr = runCatching { JSONArray(prefs.getString(FAV_ID, "[]")) }.getOrDefault(JSONArray())
            val out = JSONArray()
            for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("i") != songId) out.put(arr.getJSONObject(i))
            prefs.edit().putString(FAV_ID, out.toString()).apply()
            return
        }
        val arr = readAll()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.plId() == playlistId) {
                val songs = o.optJSONArray("songs") ?: JSONArray()
                val out = JSONArray()
                for (j in 0 until songs.length()) if (songs.getJSONObject(j).optString("i") != songId) out.put(songs.getJSONObject(j))
                o.put("songs", out)
                writeAll(arr)
                return
            }
        }
    }

    /** 歌单封面取第一首歌的封面 */
    private fun coverOf(playlistId: String): String? =
        songs(playlistId).firstOrNull()?.artworkUrl
}
