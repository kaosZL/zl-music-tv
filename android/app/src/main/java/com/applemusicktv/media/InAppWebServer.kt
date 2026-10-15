package com.applemusicktv.media

import android.content.Context
import android.net.wifi.WifiManager
import com.applemusicktv.data.CrossfadePreferences
import com.applemusicktv.data.ServerPreferences
import com.applemusicktv.data.StandalonePreferences
import com.applemusicktv.data.LyricsOffsetPreferences
import com.applemusicktv.data.MutPreferences
import com.applemusicktv.data.NetworkLog
import com.applemusicktv.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppWebServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: MutPreferences,
    private val repo: MusicRepository,
    private val lxEngine: com.applemusicktv.data.lx.LxEngine,
    private val lxScriptStore: com.applemusicktv.data.lx.LxScriptStore,
    private val lxPrefs: com.applemusicktv.data.lx.LxPreferences,
    private val lyricsOffsetPrefs: LyricsOffsetPreferences,
    private val crossfadePrefs: CrossfadePreferences,
    private val standalonePrefs: StandalonePreferences,
    private val serverPrefs: ServerPreferences,
    private val beatAnalyzer: BeatAnalyzer,
) {
    private val logs = ArrayDeque<String>(300)
    private val sseClients = java.util.concurrent.CopyOnWriteArrayList<java.io.OutputStream>()
    // Port 8001: live event stream for curl
    private val eventClients = java.util.concurrent.CopyOnWriteArrayList<java.io.OutputStream>()
    private var job: Job? = null
    private var eventJob: Job? = null
    private var appScope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var eventSocket: ServerSocket? = null
    val port = 8080
    val eventPort = 8081

    private val wsPrefs = context.getSharedPreferences("webserver_prefs", Context.MODE_PRIVATE)
    /** User toggle (Dev → Web Server). Default ON. */
    val isEnabled: Boolean get() = wsPrefs.getBoolean("web_enabled", true)
    val running: Boolean get() = job?.isActive == true

    /** Called once at app start: remember the scope and start only if enabled. */
    fun boot(scope: CoroutineScope) {
        appScope = scope
        // 自定义音源启用时必须起服务器：播放器的 zl1. 歌曲经 /zlstream 路由解析直链
        if (isEnabled || lxPrefs.isEnabled) start(scope)
    }

    /** Toggle from Settings — starts or stops the phone web server and persists the choice. */
    fun setEnabled(on: Boolean) {
        wsPrefs.edit().putBoolean("web_enabled", on).apply()
        if (on) appScope?.let { start(it) } else stop()
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        appScope = scope
        // Stream network-request lines live too, so :8081 and the web page show ALL logs, not just app logs.
        NetworkLog.listener = { line -> broadcastLive(line) }
        job = scope.launch(Dispatchers.IO) {
            val server = try { ServerSocket(port).also { serverSocket = it } } catch (_: Exception) { return@launch }
            while (isActive) {
                try { val s = server.accept(); launch { handle(s) } } catch (_: Exception) { if (!isActive) break }
            }
            try { server.close() } catch (_: Exception) {}
        }
        eventJob = scope.launch(Dispatchers.IO) {
            val server = try { ServerSocket(eventPort).also { eventSocket = it } } catch (e: Exception) {
                addLog("WARN", "Event port $eventPort in use — kill previous instance and restart")
                return@launch
            }
            while (isActive) {
                try { val s = server.accept(); launch { handleEvent(s) } } catch (_: Exception) { if (!isActive) break }
            }
            try { server.close() } catch (_: Exception) {}
        }
        addLog("OK", "Web server started — open ${serverUrl()} on your phone")
        val ip = serverUrl().removePrefix("http://").substringBefore(":")
        addLog("OK", "Event stream: curl http://$ip:$eventPort")
    }

    // Close the sockets too — cancelling the job alone leaves accept() blocked and the port bound.
    fun stop() {
        job?.cancel(); eventJob?.cancel()
        runCatching { serverSocket?.close() }; serverSocket = null
        runCatching { eventSocket?.close() }; eventSocket = null
        addLog("OK", "Web server stopped")
    }

    fun addLog(level: String, msg: String) {
        val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$t][$level] $msg"
        synchronized(logs) {
            if (logs.size >= 300) logs.removeFirst()
            logs.addLast(line)
        }
        broadcastLive(line)
        // Debug mirror to the PC proxy console. SKIP when the proxy isn't reachable —
        // otherwise every log line blocks an IO thread up to connectTimeout (1s) trying
        // to reach a dead host. In standalone-primary mode the app logs constantly during
        // playback, which saturated the IO dispatcher and starved Coil image decode →
        // stuttery menu scroll. The :8081 SSE stream still carries logs live.
        if (!serverPrefs.serverReachable) return
        appScope?.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val body = """{"level":"$level","msg":${org.json.JSONObject.quote(msg)}}"""
                val conn = java.net.URL("${repo.serverBaseUrl()}/api/log")
                    .openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                conn.outputStream.write(body.toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    /** Push one line to every live listener — SSE (web page) and the raw :8081 event stream. */
    private fun broadcastLive(line: String) {
        val sseData = "data: ${line.replace("\n", " ")}\n\n".toByteArray()
        val deadSse = mutableListOf<java.io.OutputStream>()
        for (out in sseClients) { try { out.write(sseData); out.flush() } catch (_: Exception) { deadSse.add(out) } }
        sseClients.removeAll(deadSse)
        val eventLine = "${line.replace("\n", " ")}\n".toByteArray()
        appScope?.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dead = mutableListOf<java.io.OutputStream>()
            for (out in eventClients) { try { out.write(eventLine); out.flush() } catch (_: Exception) { dead.add(out) } }
            eventClients.removeAll(dead)
        }
    }

    fun getLogs(): List<String> = synchronized(logs) { logs.toList() }

    fun serverUrl(): String {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return "http://${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}:$port"
    }

    private fun handle(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedOutputStream(socket.getOutputStream())
            val reqLine = reader.readLine() ?: return
            val parts = reqLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path   = parts.getOrNull(1) ?: "/"
            val hdrs = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrBlank()) {
                val idx = line.indexOf(": ")
                if (idx > 0) hdrs[line.substring(0, idx).lowercase()] = line.substring(idx + 2)
                line = reader.readLine()
            }
            var body = ""
            if (method == "POST") {
                val len = hdrs["content-length"]?.toIntOrNull() ?: 0
                val buf = CharArray(len); reader.read(buf, 0, len); body = String(buf)
            }
            when {
                method == "GET"  && path == "/"            -> send(out, 200, "text/html", html())
                method == "GET"  && path == "/status"      -> send(out, 200, "application/json", status())
                method == "GET"  && path == "/logs"        -> send(out, 200, "application/json", logsJson())
                method == "GET"  && path == "/stream"      -> { handleSse(socket, out); return }

                method == "GET"  && path == "/netlogs"     -> send(out, 200, "application/json", netLogsJson())
                method == "GET"  && path == "/report"      -> send(out, 200, "text/plain", bugReport(), "Content-Disposition: attachment; filename=\"amtv-bugreport.txt\"\r\n")
                method == "POST" && path == "/clear-crash"        -> { com.applemusicktv.util.CrashReporter.clear(context); addLog("OK", "Crash log cleared"); redirect(out, "/") }
                method == "POST" && path == "/set-token"          -> { applyToken(parseField(body, "mut")); redirect(out, "/") }
                method == "POST" && path == "/clear-token"         -> { prefs.setMUT(""); addLog("WARN","Token cleared"); redirect(out, "/") }
                method == "POST" && path == "/set-lyrics-offset"   -> { applyLyricsOffset(parseField(body, "offset")); redirect(out, "/") }
                method == "POST" && path == "/set-beat-latency"    -> { applyBeatLatency(parseField(body, "latency")); redirect(out, "/") }
                method == "POST" && path == "/set-crossfade"       -> { applyCrossfade(parseField(body, "crossfade")); redirect(out, "/") }
            method == "POST" && path == "/set-standalone"      -> { applyStandalone(parseField(body, "standalone")); redirect(out, "/") }
            method == "POST" && path == "/set-server-ip"       -> { applyServerIp(parseField(body, "serverip")); redirect(out, "/") }
                method == "POST" && path == "/set-volume-leveling" -> { applyVolumeLeveling(parseField(body, "vol")); redirect(out, "/") }
                method == "POST" && path == "/set-background-play" -> { applyBackgroundPlay(parseField(body, "bg")); redirect(out, "/") }

                // ── 自定义音源（LX 引擎）──
                method == "GET" && path.startsWith("/zlstream/") -> handleZlStream(out, path.removePrefix("/zlstream/"))
                method == "POST" && path == "/import-lx-url"   -> { applyImportLxUrl(parseField(body, "lxurl")); redirect(out, "/") }
                method == "POST" && path == "/import-lx-code"  -> { applyImportLxCode(parseField(body, "lxcode")); redirect(out, "/") }
                method == "POST" && path == "/lx-toggle-script" -> { applyLxToggleScript(parseField(body, "lxid"), parseField(body, "on") == "1"); redirect(out, "/") }
                method == "POST" && path == "/lx-remove-script" -> { applyLxRemoveScript(parseField(body, "lxid")); redirect(out, "/") }
                else -> send(out, 404, "text/plain", "Not found")
            }
            out.flush(); socket.close()
        } catch (_: Exception) {}
    }

    private fun applyBeatLatency(raw: String) {
        val ms = raw.trim().toLongOrNull()
        if (ms == null || ms < 0) { addLog("ERROR", "Invalid beat latency: $raw"); return }
        beatAnalyzer.latencyMs = ms
        beatAnalyzer.resetBeat()
        addLog("OK", "Beat latency set to ${ms}ms — buffer reset")
    }

    /** Player settings live in the "player_state" prefs the ViewModel owns. */
    private fun playerPrefs() = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)

    private fun applyVolumeLeveling(raw: String) {
        val on = raw.trim() == "1"
        GainProcessor.enabled = on   // takes effect on the audio thread immediately
        playerPrefs().edit().putBoolean("volume_leveling", on).apply()
        addLog("OK", "Volume leveling ${if (on) "ON" else "OFF"}")
    }

    private fun applyBackgroundPlay(raw: String) {
        val on = raw.trim() == "1"
        playerPrefs().edit().putBoolean("background_play", on).apply()
        addLog("OK", "Background play ${if (on) "ON" else "OFF"} — applies on next launch")
    }

    private fun applyServerIp(raw: String) {
        val ip = raw.trim()
        serverPrefs.setPcServerIp(ip)
        addLog("OK", if (ip.isEmpty()) "Proxy IP cleared — using the built-in default"
                     else "Proxy IP set to $ip")
    }

    private fun applyStandalone(raw: String) {
        val on = raw.trim() == "1" || raw.trim().equals("true", ignoreCase = true)
        standalonePrefs.setEnabled(on)
        addLog("OK", "Standalone playback ${if (on) "ON" else "OFF"} — applies from next song")
    }

    private fun applyCrossfade(raw: String) {
        val ms = raw.trim().toLongOrNull()
        if (ms == null) { addLog("ERROR", "Invalid crossfade: $raw"); return }
        crossfadePrefs.setDuration(ms)
        addLog("OK", "Crossfade set to ${crossfadePrefs.getDuration()}ms — applies from next song")
    }

    private fun applyLyricsOffset(raw: String) {
        val ms = raw.trim().toLongOrNull()
        if (ms == null) { addLog("ERROR", "Invalid offset: $raw"); return }
        lyricsOffsetPrefs.setOffset(ms)
        addLog("OK", "Lyrics offset set to ${ms}ms")
    }

    private fun applyToken(mut: String) {
        if (mut.length < 20) { addLog("ERROR", "Token too short"); return }
        prefs.setMUT(mut)
        addLog("OK", "Music-User-Token saved (${mut.length} chars)")
        appScope?.launch {
            runCatching { repo.syncMUTToServer(mut) }.onFailure { addLog("WARN", "Server MUT sync failed: ${it.message}") }
        }
    }

    private fun handleEvent(socket: Socket) {
        try {
            socket.soTimeout = 0
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()
            // Drain HTTP request headers
            var line = reader.readLine()
            while (!line.isNullOrBlank()) { line = reader.readLine() }
            val header = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
            out.write(header.toByteArray()); out.flush()
            // Dump EVERYTHING on connect: network request log + full app log backlog,
            // then keep streaming live app logs. `curl http://<firetv-ip>:8081`.
            out.write("===== NETWORK REQUESTS =====\n".toByteArray())
            for (n in NetworkLog.getAll()) { out.write("${n}\n".toByteArray()) }
            out.write("===== APP LOG =====\n".toByteArray())
            val backlog = synchronized(logs) { logs.toList() }
            for (l in backlog) { out.write("${l}\n".toByteArray()) }
            out.write("===== LIVE =====\n".toByteArray()); out.flush()
            eventClients.add(out)
            try { while (!socket.isClosed) Thread.sleep(500) } catch (_: Exception) {}
            eventClients.remove(out)
        } catch (_: Exception) {}
        try { socket.close() } catch (_: Exception) {}
    }

    private fun handleSse(socket: Socket, out: BufferedOutputStream) {
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        out.write(header.toByteArray()); out.flush()
        sseClients.add(out)
        try { while (!socket.isClosed) Thread.sleep(1000) } catch (_: Exception) {}
        sseClients.remove(out)
        try { socket.close() } catch (_: Exception) {}
    }

    /**
     * zl1. 复合 id 的取流路由：LX 引擎（userApi 脚本）解析出 CDN 直链后 302。
     * 每个连接一个协程（IO），runBlocking 等引擎可接受；15s 超时防止脚本卡死挂住播放器。
     */
    private fun handleZlStream(out: BufferedOutputStream, songId: String) {
        val url = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(15_000) { repo.playableUrl(songId) }
        }
        if (url == null || !url.startsWith("http")) {
            addLog("ERROR", "zlstream 解析失败: ${songId.take(24)}…")
            send(out, 404, "text/plain", "resolve failed")
        } else {
            send(out, 302, "text/plain", "", "Location: $url\r\n")
        }
    }

    private fun applyImportLxUrl(raw: String) {
        val url = raw.trim()
        if (url.isEmpty()) { addLog("WARN", "音源 URL 为空"); return }
        kotlinx.coroutines.runBlocking {
            lxScriptStore.importFromUrl(url)
                .onSuccess { addLog("OK", "音源已导入：${it.name}（重启或切页后生效）") }
                .onFailure { addLog("ERROR", "导入失败：${it.message}") }
        }
    }

    private fun applyImportLxCode(raw: String) {
        if (raw.isBlank()) { addLog("WARN", "脚本内容为空"); return }
        kotlinx.coroutines.runBlocking {
            lxScriptStore.importFromText(raw.trim())
                .onSuccess { addLog("OK", "音源已导入：${it.name}（重启或切页后生效）") }
                .onFailure { addLog("ERROR", "导入失败：${it.message}") }
        }
    }

    private fun applyLxToggleScript(id: String, on: Boolean) {
        lxPrefs.setEnabled(id, on)
        addLog("OK", "音源已${if (on) "启用" else "停用"}（切换歌曲源后生效）")
    }

    private fun applyLxRemoveScript(id: String) {
        lxScriptStore.delete(id)
        addLog("OK", "音源已删除")
    }

    private fun send(out: BufferedOutputStream, code: Int, type: String, body: String, extra: String = "") {
        val bytes = body.toByteArray()
        val status = mapOf(200 to "OK", 302 to "Found", 303 to "See Other", 400 to "Bad Request", 404 to "Not Found")[code] ?: "Error"
        val resp = "HTTP/1.1 $code $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n$extra\r\n"
        out.write(resp.toByteArray()); out.write(bytes)
    }

    private fun redirect(out: BufferedOutputStream, to: String) = send(out, 303, "text/plain", "", "Location: $to\r\n")

    private fun parseField(body: String, field: String) =
        body.split("&").find { it.startsWith("$field=") }?.removePrefix("$field=")
            ?.replace("+", " ")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

    /** A single self-contained text dump for bug reports: environment + crash + logs. */
    private fun bugReport(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val crash = com.applemusicktv.util.CrashReporter.lastCrash(context)
        return buildString {
            appendLine("===== Apple Music TV — bug report =====")
            appendLine("Generated: $ts")
            appendLine("App: v${com.applemusicktv.BuildConfig.VERSION_NAME} (${com.applemusicktv.BuildConfig.VERSION_CODE})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Standalone: ${standalonePrefs.isEnabled()} · Token set: ${prefs.hasMUT()}")
            appendLine()
            appendLine("===== LAST CRASH =====")
            appendLine(crash ?: "(none recorded)")
            appendLine()
            appendLine("===== NETWORK REQUESTS =====")
            NetworkLog.getAll().forEach { appendLine(it) }
            appendLine()
            appendLine("===== APP LOG =====")
            getLogs().forEach { appendLine(it) }
        }
    }

    private fun status(): String {
        val lxScripts = org.json.JSONArray().apply {
            lxPrefs.loadScripts().forEach {
                put(org.json.JSONObject().apply {
                    put("id", it.id); put("name", it.name); put("enabled", it.enabled)
                })
            }
        }
        return """{"hasMUT":${prefs.hasMUT()},"mutLen":${prefs.getMUT().length},"url":"${serverUrl()}","hasCrash":${com.applemusicktv.util.CrashReporter.lastCrash(context) != null},"lxScripts":${lxScripts}}"""
    }
    private fun logsJson() = "[${getLogs().joinToString(",") { "\"${it.replace("\"","'")}\"" }}]"
    private fun netLogsJson() = "[${NetworkLog.getAll().joinToString(",") { "\"${it.replace("\"","'")}\"" }}]"

    private fun html(): String {
        val has = prefs.hasMUT()
        val preview = if (has) prefs.getMUT().take(32) + "…" else ""
        val currentOffset = lyricsOffsetPrefs.getOffset()
        val currentBeatLatency = beatAnalyzer.latencyMs
        val standaloneOn = standalonePrefs.isEnabled()
        val serverIp = serverPrefs.getPcServerIp()
        val pp = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val volLevelOn = pp.getBoolean("volume_leveling", false)
        val bgPlayOn = pp.getBoolean("background_play", true)
        val currentCrossfade = crossfadePrefs.getDuration()
        val crossfadeSec = "%.1f".format(currentCrossfade / 1000f)
        val logRows = getLogs().reversed().take(80).joinToString("") { log ->
            val cls = when { log.contains("[OK]") -> "g"; log.contains("[ERROR]") -> "r"; log.contains("[WARN]") -> "y"; else -> "d" }
            "<div class=$cls>${log.replace("<","&lt;")}</div>"
        }.ifEmpty { "<div class=d>暂无日志。</div>" }
        val netRows = NetworkLog.getAll().reversed().take(100).joinToString("") { log ->
            val cls = when { log.contains("✓") -> "g"; log.contains("✗") -> "r"; else -> "d" }
            "<div class=$cls>${log.replace("<","&lt;")}</div>"
        }.ifEmpty { "<div class=d>暂无请求。</div>" }
        return """<!DOCTYPE html><html lang=en><head><meta charset=UTF-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Apple Music TV</title><style>
*{box-sizing:border-box;margin:0;padding:0}body{font-family:-apple-system,sans-serif;background:#0a0a0a;color:#e5e5e7;padding:20px;max-width:620px;margin:auto}
h1{font-size:20px;font-weight:700;margin-bottom:2px}
.sub{font-size:11px;color:#444;margin-bottom:20px;font-family:monospace}
.card{background:#111;border-radius:12px;padding:16px;margin-bottom:12px}
h2{font-size:10px;font-weight:600;color:#555;text-transform:uppercase;letter-spacing:.8px;margin-bottom:12px}
.row{display:flex;align-items:center;gap:10px;margin-bottom:10px}
.dot{width:9px;height:9px;border-radius:50%;flex-shrink:0}
.dot-g{background:#34c759}.dot-r{background:#ff3b30}
.g{color:#34c759}.r{color:#ff3b30}.y{color:#ffd60a}.d{color:#555}
.label{font-size:13px;color:#ccc}
.sub2{font-size:10px;color:#555;font-family:monospace;word-break:break-all;margin-top:2px}
textarea{width:100%;background:#1c1c1e;border:1.5px solid #2c2c2e;border-radius:8px;color:#e5e5e7;font-family:monospace;font-size:11px;padding:10px;min-height:80px;outline:none;resize:vertical}
textarea:focus{border-color:#fa233b}
.btn{display:block;width:100%;padding:12px;border-radius:8px;font-size:14px;font-weight:600;border:none;cursor:pointer;margin-top:8px;text-align:center}
.btn-p{background:#fa233b;color:#fff}.btn-s{background:#1c1c1e;color:#888}
.btn:active{opacity:.7}
ol{font-size:12px;color:#666;line-height:2.4;padding-left:18px}code{color:#fa233b;font-family:monospace;font-size:11px}
.logs{background:#080808;border-radius:8px;padding:10px;max-height:240px;overflow-y:auto;font-family:monospace;font-size:10px;line-height:1.8}
.rbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
.rbtn{background:none;border:1px solid #222;color:#555;padding:4px 12px;border-radius:6px;font-size:11px;cursor:pointer}
.rbtn:active{color:#aaa}
</style>
<script>
function refresh(){
  fetch('/status').then(r=>r.json()).then(s=>{
    const el=document.getElementById('status');
    if(el)el.innerHTML=s.hasMUT?'<div class="row"><div class="dot dot-g"></div><div><div class=label>Music-User-Token 已生效</div></div></div>':'<div class="row"><div class="dot dot-r"></div><div><div class=label>未设置令牌</div></div></div>';
  });
  fetch('/logs').then(r=>r.json()).then(logs=>{
    const el=document.getElementById('applogs');
    if(el)el.innerHTML=logs.reverse().slice(0,80).map(l=>{const c=l.includes('[OK]')?'g':l.includes('[ERROR]')?'r':l.includes('[WARN]')?'y':'d';return'<div class='+c+'>'+l.replace(/</g,'&lt;')+'</div>';}).join('')||'<div class=d>暂无日志。</div>';
  });
  fetch('/netlogs').then(r=>r.json()).then(logs=>{
    const el=document.getElementById('netlogs');
    if(el)el.innerHTML=logs.reverse().slice(0,100).map(l=>{const c=l.includes('✓')?'g':l.includes('✗')?'r':'d';return'<div class='+c+'>'+l.replace(/</g,'&lt;')+'</div>';}).join('')||'<div class=d>暂无请求。</div>';
  });
}
setInterval(refresh,3000);
function copyLogs(id){
  fetch(id==='applogs'?'/logs':'/netlogs').then(r=>r.json()).then(logs=>{
    const text=logs.reverse().join('\n');
    const b=document.getElementById('copy-'+id);
    const done=()=>{const orig=b.textContent;b.textContent='已复制！';setTimeout(()=>b.textContent=orig,1500);};
    // navigator.clipboard only exists in a secure context (HTTPS/localhost). This
    // page is served over plain HTTP on a LAN IP, so it's usually undefined — fall
    // back to a hidden textarea + execCommand, which works in insecure contexts.
    if(navigator.clipboard&&window.isSecureContext){
      navigator.clipboard.writeText(text).then(done).catch(()=>legacyCopy(text,done,b));
    } else { legacyCopy(text,done,b); }
  });
}
function legacyCopy(text,done,b){
  try{
    const ta=document.createElement('textarea');
    ta.value=text;ta.style.position='fixed';ta.style.top='0';ta.style.left='0';ta.style.opacity='0';
    document.body.appendChild(ta);ta.focus();ta.select();
    const ok=document.execCommand('copy');
    document.body.removeChild(ta);
    if(ok)done();else{b.textContent='复制失败';setTimeout(()=>b.textContent='复制',1500);}
  }catch(e){b.textContent='复制失败';setTimeout(()=>b.textContent='复制',1500);}
}
</script>
</head><body>
<h1>Apple Music TV</h1>
<p class=sub>${serverUrl()}</p>

<div class=card>
<h2>状态</h2>
<div id=status>
<div class=row><div class="dot ${if(has)"dot-g" else "dot-r"}"></div><div><div class=label>Music-User-Token</div>${if(has)"<div class=sub2>$preview</div>" else "<div class=sub2 style=color:#555>未设置 — 请在下方粘贴</div>"}</div></div>
</div>
</div>

<div class=card>
<h2>导入音源脚本</h2>
<form method=POST action=/import-lx-url>
<input name=lxurl placeholder="粘贴音源脚本链接（https://…/latest.js）">
<button class="btn btn-p" type=submit>从链接导入</button>
</form>
<form method=POST action=/import-lx-code>
<textarea name=lxcode placeholder="或直接粘贴完整脚本内容"></textarea>
<button class="btn btn-p" type=submit>从内容导入</button>
</form>
<div id=lxlist></div>
<script>
fetch('/status').then(r=>r.json()).then(s=>{
  const box=document.getElementById('lxlist');
  if(!s.lxScripts||!s.lxScripts.length){box.innerHTML='<div class=sub2>还没有导入音源</div>';return}
  box.innerHTML='<h2>已导入音源</h2>'+s.lxScripts.map(function(x){
    return '<div class=row><div class=label>'+x.name+'</div><div>'+(x.enabled?'已启用':'已停用')+'</div></div>'
      +'<div class=row><form method=POST action=/lx-toggle-script><input type=hidden name=lxid value='+x.id+'><input type=hidden name=on value='+(x.enabled?0:1)+'><button class=btn btn-s type=submit>'+(x.enabled?'停用':'启用')+'</button></form>'
      +'<form method=POST action=/lx-remove-script><input type=hidden name=lxid value='+x.id+'><button class=btn btn-s type=submit>删除</button></form></div>';
  }).join('');
}).catch(()=>{});
</script>
</div>

<div class=card>
<h2>设置令牌（Apple 模式备用）</h2>
<form method=POST action=/set-token>
<textarea name=mut placeholder="eyJra… 在此粘贴你的 Music-User-Token"></textarea>
<button class="btn btn-p" type=submit>保存令牌</button>
</form>
${if(has)"<form method=POST action=/clear-token><button class=btn btn-s type=submit>清除令牌</button></form>" else ""}
</div>

<div class=card>
<h2>歌词偏移</h2>
<div class=row><div class=label>当前偏移</div><div class=sub2 style=color:#aaa>${currentOffset}ms</div></div>
<form method=POST action=/set-lyrics-offset>
<input name=offset type=number value="$currentOffset" placeholder="0" style="width:100%;background:#1c1c1e;border:1.5px solid #2c2c2e;border-radius:8px;color:#e5e5e7;font-family:monospace;font-size:13px;padding:10px;outline:none;margin-top:4px">
<button class="btn btn-p" type=submit style=margin-top:8px>设置偏移（毫秒）</button>
</form>
<div style="font-size:10px;color:#555;margin-top:8px">正值 = 歌词提前显示，负值 = 延后。如果歌词偏慢可尝试 +200。</div>
</div>

<div class=card>
<h2>节拍延迟</h2>
<div class=row><div class=label>当前延迟</div><div class=sub2 style=color:#aaa>${currentBeatLatency}ms</div></div>
<form method=POST action=/set-beat-latency>
<input name=latency type=range min=0 max=500 step=10 value="$currentBeatLatency" oninput="document.getElementById('bv').textContent=this.value+'ms'" style="width:100%;accent-color:#fa233b;margin-top:8px">
<div style="font-size:12px;color:#aaa;text-align:center;margin-top:4px"><span id=bv>${currentBeatLatency}ms</span></div>
<button class="btn btn-p" type=submit style=margin-top:8px>应用并重置</button>
</form>
<div style="font-size:10px;color:#555;margin-top:8px">0 = 电视扬声器，约 200 = 常见蓝牙延迟。应用时会重置节拍缓冲。</div>
</div>

<div class=card>
<h2>电脑（代理服务器）</h2>
<div class=row><div class=label>当前</div><div class=sub2 style=color:#aaa>${if (serverIp.isEmpty()) "未设置" else serverIp}</div></div>
<form method=POST action=/set-server-ip>
<input name=serverip type=text inputmode=decimal placeholder="192.168.1.190" value="$serverIp" style="width:100%;box-sizing:border-box;padding:10px;margin-top:8px;border-radius:6px;border:1px solid #333;background:#0d0d0d;color:#fff;font-family:monospace;font-size:15px">
<button class="btn btn-p" type=submit style=margin-top:8px>设置电脑 IP</button>
</form>
<div style="font-size:10px;color:#555;margin-top:8px">默认使用 3000 端口。浏览、资料库、搜索和歌词都需要它。留空则使用内置默认值。</div>
</div>

<div class=card>
<h2>独立播放模式（无需电脑）</h2>
<div class=row><div class=label>状态</div><div class=sub2 style="color:${if (standaloneOn) "#6bcb77" else "#aaa"}">${if (standaloneOn) "开 — 全部在电视上处理" else "关 — 使用电脑服务器"}</div></div>
<form method=POST action=/set-standalone>
<input type=hidden name=standalone value="${if (standaloneOn) "0" else "1"}">
<button class="btn ${if (standaloneOn) "" else "btn-p"}" type=submit style=margin-top:8px>${if (standaloneOn) "关闭" else "开启"}</button>
</form>
<div style="font-size:10px;color:#555;margin-top:8px">直接连接 Apple 获取浏览、资料库、搜索、封面、歌词并直接播放 — 完全不需要电脑。歌曲约 1 秒起播，而不是 15-20 秒。代价：部分曲目的音源分段存在间隙（电脑重封装可修复），音频可能有轻微爆音。从下一首歌开始生效。</div>
</div>

<div class=card>
<h2>音量均衡</h2>
<div class=row><div class=label>状态</div><div class=sub2 style="color:${if (volLevelOn) "#6bcb77" else "#aaa"}">${if (volLevelOn) "开 — 正在拉平响度" else "关 — 每首曲目按原始音量"}</div></div>
<form method=POST action=/set-volume-leveling>
<input type=hidden name=vol value="${if (volLevelOn) "0" else "1"}">
<button class="btn ${if (volLevelOn) "" else "btn-p"}" type=submit style=margin-top:8px>${if (volLevelOn) "关闭" else "开启"}</button>
</form>
<div style="font-size:10px;color:#555;margin-top:8px">缓慢自动增益，让偏安静的母带升压、偏响的降压。立即生效。可在应用日志（VOL 行）中查看。</div>
</div>

<div class=card>
<h2>后台播放</h2>
<div class=row><div class=label>状态</div><div class=sub2 style="color:${if (bgPlayOn) "#6bcb77" else "#aaa"}">${if (bgPlayOn) "开 — 后台继续播放" else "关 — 离开时暂停"}</div></div>
<form method=POST action=/set-background-play>
<input type=hidden name=bg value="${if (bgPlayOn) "0" else "1"}">
<button class="btn ${if (bgPlayOn) "" else "btn-p"}" type=submit style=margin-top:8px>${if (bgPlayOn) "关闭" else "开启"}</button>
</form>
<div style="font-size:10px;color:#555;margin-top:8px">在下次启动应用时生效。</div>
</div>

<div class=card>
<h2>淡入淡出</h2>
<div class=row><div class=label>当前时长</div><div class=sub2 style=color:#aaa>${crossfadeSec}s</div></div>
<form method=POST action=/set-crossfade>
<input name=crossfade type=range min=${CrossfadePreferences.MIN_MS} max=${CrossfadePreferences.MAX_MS} step=500 value="$currentCrossfade" oninput="document.getElementById('cv').textContent=(this.value/1000).toFixed(1)+'s'" style="width:100%;accent-color:#fa233b;margin-top:8px">
<div style="font-size:12px;color:#aaa;text-align:center;margin-top:4px"><span id=cv>${crossfadeSec}s</span></div>
<button class="btn btn-p" type=submit style=margin-top:8px>设置淡入淡出</button>
</form>
<div style="font-size:10px;color:#555;margin-top:8px">从下一首歌开始生效 — 正在进行的淡出保持原时长。更长的淡出需要更早预取下一首。</div>
</div>

<div class=card>
<h2>问题反馈</h2>
<div class=row><div class=label>最近崩溃</div><div class=sub2 style="color:${if (com.applemusicktv.util.CrashReporter.lastCrash(context) != null) "#ff3b30" else "#6bcb77"}">${if (com.applemusicktv.util.CrashReporter.lastCrash(context) != null) "有崩溃记录" else "无"}</div></div>
<a class="btn btn-p" href=/report download style=text-decoration:none>下载反馈报告</a>
<form method=POST action=/clear-crash><button class="btn btn-s" type=submit>清除崩溃日志</button></form>
<div style="font-size:10px;color:#555;margin-top:8px">将应用版本、设备信息、最近崩溃以及近期应用和网络日志打包成一个文本文件以便分享。</div>
</div>

<div class=card>
<h2>如何获取你的令牌</h2>
<ol>
<li>用 Chrome 打开 <strong style=color:#ccc>music.apple.com</strong> · 登录</li>
<li>开发者工具（F12）→ Network（网络）面板</li>
<li>播放任意歌曲</li>
<li>找到发往 <code>amp-api-edge.music.apple.com</code> 的请求</li>
<li>复制请求头 <code>Media-User-Token</code> 的值</li>
</ol>
</div>

<div class=card>
<div class=rbar><h2 style=margin:0>网络活动</h2><div style=display:flex;gap:6px><button class=rbtn id=copy-netlogs onclick="copyLogs('netlogs')">复制</button><button class=rbtn onclick=refresh()>↻</button></div></div>
<div class=logs id=netlogs>$netRows</div>
</div>

<div class=card>
<div class=rbar><h2 style=margin:0>应用日志</h2><div style=display:flex;gap:6px><button class=rbtn id=copy-applogs onclick="copyLogs('applogs')">复制</button><button class=rbtn onclick=refresh()>↻</button></div></div>
<div class=logs id=applogs>$logRows</div>
</div>
</body></html>"""
    }
}
