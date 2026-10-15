package com.applemusicktv.data.lx

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class StoredScript(
    val meta: LxScript,
    val code: String,
)

/**
 * lx userApi 脚本仓库：文件本体在 filesDir/lx_sources/，元数据（启停）在 [LxPreferences]。
 * 「洛雪式导入」：粘贴脚本 URL（下载 .js）或直接粘贴脚本内容。
 */
@Singleton
class LxScriptStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: LxPreferences,
) {
    private val http = OkHttpClient.Builder().build()

    private fun dir(): File = File(context.filesDir, "lx_sources").apply { mkdirs() }

    fun enabledScripts(): List<StoredScript> =
        prefs.loadScripts().filter { it.enabled }.mapNotNull { meta ->
            val file = File(dir(), meta.fileName)
            if (!file.exists()) return@mapNotNull null
            StoredScript(meta, file.readText())
        }

    /**
     * 从 URL 导入（下载脚本内容 → 存文件 → 入列表并默认启用）。
     * 返回 Result<LxScript>。
     */
    suspend fun importFromUrl(url: String): Result<LxScript> = withContext(Dispatchers.IO) {
        runCatching {
            val body = http.newCall(Request.Builder().url(url.trim()).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) error("下载失败 HTTP ${resp.code}")
                    resp.body?.string() ?: error("脚本内容为空")
                }
            if (!body.contains("globalThis.lx") && !body.contains("lx.")) {
                error("不像 lx 音源脚本（未发现 lx 接口调用）")
            }
            saveScript(body, url)
        }
    }

    /** 直接导入脚本内容（手机网页粘贴 / 二维码通道后续复用） */
    suspend fun importFromText(code: String): Result<LxScript> = withContext(Dispatchers.IO) {
        runCatching { saveScript(code, null) }
    }

    private fun saveScript(code: String, fromUrl: String?): LxScript {
        val name = extractName(code) ?: (fromUrl?.substringAfterLast('/')?.take(24) ?: "音源脚本")
        val id = md5(code).take(10)
        val fileName = "$id.js"
        File(dir(), fileName).writeText(code)
        val script = LxScript(id = id, fileName = fileName, name = name, enabled = true)
        // 同 id 覆盖旧的，其余保留
        val others = prefs.loadScripts().filter { it.id != id }
        prefs.saveScripts(others + script)
        return script
    }

    fun delete(id: String) {
        prefs.loadScripts().find { it.id == id }?.let { meta ->
            File(dir(), meta.fileName).delete()
        }
        prefs.remove(id)
    }

    /** 脚本头 @name 提取（lx 脚本约定：注释块里 @name xxx） */
    private fun extractName(code: String): String? =
        Regex("@name\\s+(.+)").findAll(code).firstOrNull()?.groupValues?.get(1)?.trim()?.take(24)

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
