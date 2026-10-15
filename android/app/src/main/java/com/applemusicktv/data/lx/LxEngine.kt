package com.applemusicktv.data.lx

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LX 引擎：QuickJS 单实例跑酷我 SDK + lx userApi 脚本宿主。
 *
 * JS 侧三件套（assets/lx-engine/）：env.js（宿主环境/桥适配）→ kwsdk.js（酷我搜索/榜单）
 * → engine.js（zlEngine 出口）。userApi 脚本由 [LxScriptStore] 提供，加载后提供取链能力。
 * 桥协议全部字符串进出：__zlHttp(url, reqJson) -> respJson；加密/MD5 同步字符串。
 *
 * 全部 evaluate 串行（QuickJS 实例非线程安全），返回 JSON 字符串由调用方解析。
 */
@Singleton
class LxEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scriptStore: LxScriptStore,
) {
    private val mutex = Mutex()
    private var quickJs: QuickJs? = null

    private val http = OkHttpClient.Builder().build()

    /** 冷启动懒加载：三件套 + 已启用脚本。失败抛错由调用方兜底（引擎不可用≠app不可用）。 */
    private suspend fun ensure(): QuickJs = quickJs ?: mutex.withLock {
        quickJs ?: run {
            val js = QuickJs.create(Dispatchers.Default)
            try {
                bindBridges(js)
                withContext(Dispatchers.IO) {
                    js.evaluate(AssetLoader.load(context, "lx-engine/env.js"))
                    js.evaluate(AssetLoader.load(context, "lx-engine/kwsdk.js"))
                    js.evaluate(AssetLoader.load(context, "lx-engine/engine.js"))
                }
                scriptStore.enabledScripts().forEach { script ->
                    runCatching { jsEvaluate(js, script.code) }
                        .onFailure { it.printStackTrace() }
                }
            } catch (e: Exception) {
                runCatching { js.close() }
                throw e
            }
            quickJs = js
            js
        }
    }

    /** 脚本列表变化后重建引擎（脚本注册发生在 evaluate 时，卸载只能重建） */
    suspend fun reload() = mutex.withLock {
        quickJs?.let { runCatching { it.close() } }
        quickJs = null
    }

    // ── 对外 API（返回 JSON 字符串；出错返回 {"__err": "..."}）──

    suspend fun search(keyword: String, page: Int = 1, limit: Int = 30): String =
        call("zlEngine.search(${jsStr(keyword)}, $page, $limit)")

    suspend fun boards(): String = call("zlEngine.boards()")

    suspend fun boardDetail(bangId: String, page: Int = 1): String =
        call("zlEngine.boardDetail(${jsStr(bangId)}, $page)")

    suspend fun lyric(songId: String): String = call("zlEngine.lyric(${jsStr(songId)})")

    suspend fun musicUrl(musicInfoJson: String, type: String = "320k"): String =
        call("zlEngine.musicUrl('kw', ${musicInfoJson}, ${jsStr(type)})")

    suspend fun hasUserApi(): Boolean = call("zlEngine.hasUserApi()").trim() == "true"

    /** 在引擎里执行任意 JS（加载脚本用） */
    private fun jsEvaluate(js: QuickJs, code: String) {
        js.evaluate(code)
    }

    private suspend fun call(expr: String): String {
        val js = try {
            ensure()
        } catch (e: Exception) {
            return JSONObject().put("__err", "engine init: ${e.message}").toString()
        }
        val wrapped = "(async () => { try { const __r = await $expr; " +
            "return typeof __r === 'string' ? __r : JSON.stringify(__r) } " +
            "catch (e) { return JSON.stringify({ __err: String((e && e.message) || e) }) } })()"
        return mutex.withLock {
            try {
                js.evaluate<String>(wrapped) ?: JSONObject().put("__err", "null result").toString()
            } catch (e: Exception) {
                JSONObject().put("__err", e.message ?: e.toString()).toString()
            }
        }
    }

    // ── QuickJS ↔ Kotlin 桥 ──

    private fun bindBridges(js: QuickJs) {
        // async：HTTP（字符串协议）
        js.asyncFunction<String, String>("__zlHttp") { args ->
            val url = args.getOrNull(0) as? String ?: throw IllegalArgumentException("url")
            val reqJson = args.getOrNull(1) as? String ?: "{}"
            bridgeHttp(url, reqJson)
        }
        // 同步：MD5 / AES（kuwo 榜单签名用；契约与 RN 原生模块一致：base64 进出）
        js.function<Any?, String>("__zlMd5") { args ->
            md5Hex(args.getOrNull(0) as? String ?: "")
        }
        js.function<Any?, String>("__zlAesEncrypt") { args ->
            aes(args.getOrNull(0) as? String, args.getOrNull(1) as? String,
                args.getOrNull(2) as? String, args.getOrNull(3) as? String, encrypt = true) ?: ""
        }
        js.function<Any?, String>("__zlAesDecrypt") { args ->
            aes(args.getOrNull(0) as? String, args.getOrNull(1) as? String,
                args.getOrNull(2) as? String, args.getOrNull(3) as? String, encrypt = false) ?: ""
        }
    }

    private fun bridgeHttp(url: String, reqJson: String): String {
        val req = runCatching { JSONObject(reqJson) }.getOrNull() ?: JSONObject()
        val builder = Request.Builder().url(url)
        when (req.optString("method", "GET").uppercase()) {
            "POST" -> builder.post(okhttp3.RequestBody.create(null, req.opt("body")?.toString() ?: ""))
        }
        req.optJSONObject("headers")?.let { headers ->
            for (key in headers.keys()) builder.header(key, headers.optString(key))
        }
        return try {
            http.newCall(builder.build()).execute().use { resp ->
                val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
                val body = if (req.optBoolean("binary")) {
                    Base64.getEncoder().encodeToString(bodyBytes)
                } else {
                    String(bodyBytes, Charsets.UTF_8)
                }
                JSONObject()
                    .put("statusCode", resp.code)
                    .put("headers", JSONObject())
                    .put("body", body)
                    .toString()
            }
        } catch (e: Exception) {
            JSONObject().put("__netErr", e.message ?: e.toString()).toString()
        }
    }

    /** JS 字符串字面量安全引用（JSON 转义） */
    private fun jsStr(v: String): String = org.json.JSONObject.quote(v) ?: "\"...\""

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** AES/ECB/NoPadding，base64 进出（对齐 RN CryptoModule/AES.java；失败返回 null） */
    private fun aes(text: String?, key: String?, iv: String?, mode: String?, encrypt: Boolean): String? {
        if (text == null || key == null) return null
        return try {
            val transformation = when {
                mode?.contains("ECB") == true -> "AES/ECB/NoPadding"
                else -> "AES/CBC/PKCS5Padding"
            }
            val secret = SecretKeySpec(Base64.getDecoder().decode(key), "AES")
            val cipher = Cipher.getInstance(transformation)
            if (transformation.contains("ECB")) {
                cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, secret)
            } else {
                val ivBytes = Base64.getDecoder().decode(iv ?: "")
                val safeIv = if (ivBytes.size == 16) ivBytes else ByteArray(16)
                cipher.init(
                    if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                    secret,
                    javax.crypto.spec.IvParameterSpec(safeIv),
                )
            }
            val data = Base64.getDecoder().decode(text)
            val out = cipher.doFinal(data)
            if (encrypt) Base64.getEncoder().encodeToString(out) else String(out, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private object AssetLoader {
        fun load(context: Context, path: String): String =
            context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
