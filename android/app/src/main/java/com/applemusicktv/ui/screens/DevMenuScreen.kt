package com.applemusicktv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.applemusicktv.BuildConfig
import com.applemusicktv.ui.viewmodel.DevMenuViewModel
import com.applemusicktv.ui.viewmodel.PlayerViewModel
import com.applemusicktv.util.UpdateChecker
import com.applemusicktv.util.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DevMenuScreen(
    playerVm: PlayerViewModel,
    onDataRefresh: () -> Unit = {},
    initialUpdate: UpdateInfo? = null,
    modifier: Modifier = Modifier,
) {
    val vm: DevMenuViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    // ZL 定制：设置页只做音源管理（老板 2026-10-15 拍板）——保留原骨架与焦点流，
    // 正文区块精简为「音源管理」+「关于」
    var showQr by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("设置", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)

                // ── 音源管理（ZL 核心功能）────────────────────────────
                SectionLabel("音源管理")
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("搜索与播放使用导入的自定义音源（洛雪 userApi 脚本）", fontSize = 11.sp, color = Color(0xFF777777))
                    ActionBtn(
                        if (state.lxEnabled) "自定义音源：已启用（按 OK 关闭）" else "自定义音源：已关闭（按 OK 启用）",
                        if (state.lxEnabled) Color(0xFF1A2A1A) else Color(0xFF2A2A2A),
                    ) { vm.setLxEnabled(!state.lxEnabled) }

                    ActionBtn("扫码导入音源（手机打开网页粘贴链接）", Color(0xFF2A2A2A)) { showQr = true }

                    Text("已导入音源（${state.lxScripts.size}）", fontSize = 9.sp, color = Color(0xFF888888), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    if (state.lxScripts.isEmpty()) {
                        Text("还没有导入音源。不导入也可用酷我内置搜索，但取链成功率较低，建议扫码导入一个。", fontSize = 11.sp, color = Color(0xFFAA7744))
                    }
                    state.lxScripts.forEach { script ->
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF0D0D0D), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(script.name, fontSize = 13.sp, color = if (script.enabled) Color.White else Color(0xFF666666), modifier = Modifier.weight(1f))
                            ActionBtn(if (script.enabled) "停用" else "启用", Color(0xFF2A2A2A), small = true) { vm.toggleLxScript(script.id, !script.enabled) }
                            ActionBtn("删除", Color(0xFF2A1A1A), small = true) { vm.removeLxScript(script.id) }
                        }
                    }
                }

                // ── 关于 ────────────────────────────────────────────
                SectionLabel("关于")
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("ZL Music（Apple Music TV 风格 · 自定义音源版）", fontSize = 13.sp, color = Color.White)
                    Text(com.applemusicktv.BuildConfig.VERSION_NAME, fontSize = 11.sp, color = Color(0xFF777777))
                }

                Spacer(Modifier.height(20.dp))
            }
        }

    if (showQr && state.webServerUrl.isNotEmpty()) {
        com.applemusicktv.ui.components.QrImportOverlay(
            url = state.webServerUrl,
            hint = "手机与电视连同一 WiFi → 扫码打开导入页 → 粘贴音源脚本链接或上传文件",
            onClose = { showQr = false },
        )
    }
}

@Composable
private fun PhoneServerRow(webServerUrl: String, playerVm: PlayerViewModel) {
    var enabled by remember { mutableStateOf(playerVm.webServerEnabled()) }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("手机网页服务器", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(if (enabled) "已开启 — 局域网内可访问" else "已关闭", fontSize = 10.sp, color = Color(0xFF777777))
            }
            // Toggle to turn the :8080 page on/off (it's on by default).
            Surface(
                onClick = { enabled = !enabled; playerVm.setWebServerEnabled(enabled) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (enabled) Color(0xFF34C759) else Color(0xFF3A3A3C),
                    focusedContainerColor = if (enabled) Color(0xFF4AD866) else Color(0xFF5A5A5C),
                ),
            ) {
                Text(if (enabled) "开" else "关", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
        if (enabled && webServerUrl.isNotEmpty()) {
            Text("在手机上打开以设置令牌和更改设置：", fontSize = 10.sp, color = Color(0xFF777777))
            Text(webServerUrl, fontSize = 15.sp, color = Color(0xFF6BCB77),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        } else if (enabled) {
            Text("连接 Wi-Fi 以启用手机页面。", fontSize = 10.sp, color = Color(0xFF777777))
        }
    }
}

@Composable
private fun BugReportRow(webServerUrl: String) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("问题反馈 / 日志", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
        if (webServerUrl.isNotEmpty()) {
            Text("在手机上打开：", fontSize = 10.sp, color = Color(0xFF777777))
            Text("$webServerUrl/report", fontSize = 13.sp, color = Color(0xFF6BCB77),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text("下载包含应用版本、设备信息、最近崩溃和日志的文本文件。",
                fontSize = 10.sp, color = Color(0xFF777777))
        } else {
            Text("连接 Wi-Fi 以启用反馈页面。", fontSize = 10.sp, color = Color(0xFF777777))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdatesSection(initialUpdate: UpdateInfo? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf(initialUpdate) }
    var progress by remember { mutableStateOf(-1f) } // -1 = not downloading
    var beta by remember { mutableStateOf(com.applemusicktv.util.UpdatePreferences.betaEnabled(ctx)) }

    // Fire OS often RECREATES MainActivity when the unknown-sources settings screen returns, which
    // wipes all Compose state (the old in-memory parked file was lost, forcing a re-check + re-download).
    // Survive that: the downloaded APK already lives at cacheDir/updates/update.apk, and a persisted
    // flag records that an install is waiting on the grant. On resume we re-fire from disk, so
    // recreation costs nothing.
    fun prefs() = ctx.getSharedPreferences("update_state", android.content.Context.MODE_PRIVATE)
    fun cachedApk() = java.io.File(java.io.File(ctx.cacheDir, "updates"), "update.apk")
    fun hasGrant() = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        ctx.packageManager.canRequestPackageInstalls()

    fun installOrRequestGrant(apk: java.io.File) {
        if (hasGrant()) {
            prefs().edit().putBoolean("pending_install", false).apply()
            UpdateChecker.install(ctx, apk); return
        }
        // Park across a possible recreation: remember we owe an install, keep the APK on disk.
        prefs().edit().putBoolean("pending_install", true).apply()
        status = "请先允许本应用安装应用，然后返回"
        val pkgUri = android.net.Uri.parse("package:${ctx.packageName}")
        // The per-app screen isn't always present on Fire OS; fall back to the generic intent rather
        // than throwing ActivityNotFoundException at someone who was only trying to update.
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
        runCatching { ctx.startActivity(intent) }.onFailure {
            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)) }
        }
    }

    // On resume (including after a full recreation), if an install is owed, the grant is now held, and
    // the downloaded APK is still cached → install it. No re-check, no re-download.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                prefs().getBoolean("pending_install", false) && hasGrant()) {
                val apk = cachedApk()
                if (apk.exists() && apk.length() > 0) {
                    prefs().edit().putBoolean("pending_install", false).apply()
                    status = null
                    UpdateChecker.install(ctx, apk)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun runCheck() {
        if (checking) return
        checking = true; status = "正在检查 GitHub…"; update = null
        scope.launch {
            val res = UpdateChecker.check(beta)
            checking = false
            res.onSuccess { info ->
                if (info == null) status = "已是最新版本"
                else { update = info; status = null }
            }.onFailure { status = "检查失败 — 无网络连接？" }
        }
    }

    Column(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("应用版本", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    status ?: "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE} · ${BuildConfig.GIT_SHA})",
                    fontSize = 10.sp, color = Color(0xFF777777), modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (update == null && progress < 0f) {
                ActionBtn(if (checking) "检查中…" else "检查更新", Color(0xFF1C1C2E), small = true) { runCheck() }
            }
        }

        // Opt into prerelease builds. Re-checks immediately so the result reflects the channel.
        Toggle(
            label = "测试版更新", on = beta,
            sub = if (beta) "包含预发布版本" else "仅正式版",
            onToggle = {
                beta = !beta
                com.applemusicktv.util.UpdatePreferences.setBeta(ctx, beta)
                runCheck()
            },
        )

        // An update is available → show version, notes and a download button.
        update?.let { info ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("有可用更新：v${info.version}", fontSize = 13.sp, color = Color(0xFF6BCB77), fontWeight = FontWeight.SemiBold)
                if (info.notes.isNotEmpty())
                    Text(info.notes.take(400), fontSize = 10.sp, color = Color(0xFF999999), lineHeight = 14.sp)
                if (progress < 0f) {
                    val mb = if (info.sizeBytes > 0) " · %.1f MB".format(info.sizeBytes / 1024f / 1024f) else ""
                    ActionBtn("下载并安装$mb", Color(0xFF14351F)) {
                        progress = 0f
                        scope.launch {
                            UpdateChecker.download(ctx, info) { progress = it }
                                .onSuccess { apk -> progress = -1f; installOrRequestGrant(apk) }
                                .onFailure { progress = -1f; status = "下载失败"; update = null }
                        }
                    }
                } else {
                    Text("下载中… ${(progress * 100).toInt()}%", fontSize = 12.sp, color = Color.White)
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF2A2A2C), RoundedCornerShape(50))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp)
                            .background(Color(0xFFFA233B), RoundedCornerShape(50)))
                    }
                }
            }
        }
    }
}

/** Calm / Normal / Strong / Crazy for the beat-reaction multiplier. */
internal fun intensityLabel(f: Float): String = when {
    f < 0.8f -> "轻柔"
    f < 1.5f -> "标准"
    f < 2.5f -> "强烈"
    else     -> "狂热"
}

internal fun orbSpeedLabel(f: Float): String = when {
    f < 0.85f -> "慢"
    f < 1.3f  -> "标准"
    else      -> "快"
}

internal fun lyricsScaleLabel(f: Float): String = when {
    f < 1.1f  -> "小"
    f < 1.4f  -> "标准"
    f < 1.8f  -> "大"
    else      -> "特大"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Stepper(label: String, value: String, sub: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 10.sp, color = Color(0xFF777777), modifier = Modifier.padding(top = 2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepBtn("−", onDec)
            Text(value, fontSize = 15.sp, color = Color(0xFFFA233B), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepBtn("+", onInc)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StepBtn(sym: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A2C), focusedContainerColor = Color(0xFFFA233B)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        modifier = Modifier.size(38.dp),
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(sym, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Toggle(label: String, on: Boolean, sub: String, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF161618), focusedContainerColor = Color(0xFF232325)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(sub, fontSize = 10.sp, color = Color(0xFF777777), modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                Modifier.width(56.dp).height(30.dp)
                    .background(if (on) Color(0xFFFA233B) else Color(0xFF3A3A3C), RoundedCornerShape(50)),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(24.dp).background(Color.White, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, color = Color(0xFF666666),
        fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StatusChip(label: String, ok: Boolean, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(if (ok) Color(0xFF6BCB77) else Color(0xFFFF6B6B), RoundedCornerShape(50)))
        Column {
            Text(label, fontSize = 13.sp, color = Color(0xFFCCCCCC))
            if (sub != null) Text(sub, fontSize = 10.sp, color = Color(0xFF777777))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionBtn(label: String, color: Color, small: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color.copy(alpha = 0.7f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.height(if (small) 32.dp else 40.dp)) {
        Box(Modifier.padding(horizontal = if (small) 12.dp else 18.dp), Alignment.Center) {
            Text(label, fontSize = if (small) 11.sp else 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}
