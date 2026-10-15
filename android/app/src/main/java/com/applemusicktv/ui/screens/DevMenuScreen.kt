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
    val pstate by playerVm.state.collectAsState()
    var pcIpDraft by remember(state.pcServerIp) { mutableStateOf(state.pcServerIp) }
    var showDev by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // PC IP is a press-to-edit button, not a live field: a focused BasicTextField pops the
    // Fire TV IME by itself the moment D-pad focus lands on it.
    var pcIpEditing by remember { mutableStateOf(false) }
    val pcIpFocus = remember { FocusRequester() }
    var lxImportEditing by remember { mutableStateOf(false) }
    var lxImportDraft by remember { mutableStateOf("") }
    val lxImportFocus = remember { FocusRequester() }
    val ctx = LocalContext.current
    val rootView = androidx.compose.ui.platform.LocalView.current
    fun hideIme() = runCatching {
        ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(rootView.windowToken, 0)
    }

    // Live network activity — polled from the shared NetworkLog buffer.
    var netLogs by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(showDev) {
        while (showDev) { netLogs = vm.networkLogs(); kotlinx.coroutines.delay(1000) }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("设置", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)

            // ── SOFTWARE ──────────────────────────────────────────────────
            SectionLabel("软件")
            UpdatesSection(initialUpdate)
            PhoneServerRow(state.webServerUrl, playerVm)

            // ── PLAYBACK ──────────────────────────────────────────────────
            SectionLabel("播放")
            Stepper(
                label = "淡入淡出", value = "%.1fs".format(state.crossfadeMs / 1000f),
                sub = "一首歌的结尾与下一首重叠衔接",
                onDec = { vm.setCrossfade(state.crossfadeMs - 500) },
                onInc = { vm.setCrossfade(state.crossfadeMs + 500) },
            )
            Toggle(
                label = "独立播放", on = state.standaloneOn,
                sub = if (state.standaloneOn) "在本机解码 — 无需电脑" else "通过电脑服务器串流",
                onToggle = { vm.toggleStandalone() },
            )
            Toggle(
                label = "音量均衡", on = pstate.volumeLeveling,
                sub = if (pstate.volumeLeveling) "让各曲目响度一致" else "每首曲目按原始音量播放",
                onToggle = { playerVm.toggleVolumeLeveling() },
            )
            Toggle(
                label = "后台播放", on = pstate.backgroundPlayEnabled,
                sub = if (pstate.backgroundPlayEnabled) "离开应用后继续播放" else "应用切到后台时暂停",
                onToggle = { playerVm.toggleBackgroundPlay() },
            )

            // ── NOW PLAYING ───────────────────────────────────────────────
            SectionLabel("正在播放")
            Stepper(
                label = "背景",
                value = pstate.nowPlayingBackground.label,
                sub = "彩色光球、投影光晕或纯黑",
                onDec = { playerVm.stepNowPlayingBackground(-1) },
                onInc = { playerVm.stepNowPlayingBackground(1) },
            )
            Toggle(
                label = "显示信息", on = pstate.showNowPlayingInfo,
                sub = if (pstate.showNowPlayingInfo) "显示时钟和面板提示" else "隐藏它们，只看封面",
                onToggle = { playerVm.toggleNowPlayingInfo() },
            )
            // Intensity is the orb SIZE/reactivity; hidden on Black (no orbs). Remembered per mode.
            if (pstate.nowPlayingBackground.label != "纯黑") Stepper(
                label = "强度",
                value = intensityLabel(pstate.beatIntensity),
                sub = "背景随节拍联动的强度",
                onDec = { playerVm.stepBeatIntensity(-1) },
                onInc = { playerVm.stepBeatIntensity(1) },
            )
            // Orb drift speed — only meaningful in Projector.
            if (pstate.nowPlayingBackground.label == "投影") Stepper(
                label = "光球速度",
                value = orbSpeedLabel(pstate.orbSpeed),
                sub = "光球漂移的速度",
                onDec = { playerVm.stepOrbSpeed(-1) },
                onInc = { playerVm.stepOrbSpeed(1) },
            )
            Stepper(
                label = "歌词大小",
                value = lyricsScaleLabel(pstate.lyricsScale),
                sub = "歌词面板的文字大小",
                onDec = { playerVm.stepLyricsScale(-1) },
                onInc = { playerVm.stepLyricsScale(1) },
            )
            Toggle(
                label = "圆角封面", on = pstate.artworkRounded,
                sub = if (pstate.artworkRounded) "柔和的圆角专辑封面" else "直角专辑封面",
                onToggle = { playerVm.toggleArtworkRounded() },
            )
            Toggle(
                label = "动态封面", on = pstate.motionArtworkEnabled,
                sub = if (pstate.motionArtworkEnabled) "可用时播放动画专辑封面" else "关闭 — 减轻设备负担",
                onToggle = { playerVm.toggleMotionArtwork() },
            )
            Toggle(
                label = "减弱动态效果", on = pstate.reduceMotion,
                sub = if (pstate.reduceMotion) "背景保持静止" else "背景漂移和脉动",
                onToggle = { playerVm.toggleReduceMotion() },
            )
            Toggle(
                label = "省电模式", on = pstate.lowPowerMode,
                sub = if (pstate.lowPowerMode) "简化视觉效果 + 离开正在播放时释放视频解码器（返回时略有停顿）"
                      else "完整视觉效果 + 跨页面保持视频就绪（无缝返回）",
                onToggle = { playerVm.toggleLowPowerMode() },
            )
            Stepper(
                label = "屏幕保护",
                value = screensaverLabel(pstate.screensaverTimeoutMin),
                sub = "多久无操作后调暗屏幕",
                onDec = { playerVm.stepScreensaverTimeout(-1) },
                onInc = { playerVm.stepScreensaverTimeout(1) },
            )
            Toggle(
                label = "氛围屏保", on = pstate.screensaverKeepBackground,
                sub = if (pstate.screensaverKeepBackground) "调暗时保留动态背景" else "调暗时渐隐为纯黑",
                onToggle = { playerVm.toggleScreensaverKeepBackground() },
            )

            SectionLabel("遥控器")
            var remote by remember { mutableStateOf(playerVm.remoteOverride()) }
            val remoteOrder = listOf(
                com.applemusicktv.data.OnboardingPreferences.REMOTE_AUTO,
                com.applemusicktv.data.OnboardingPreferences.REMOTE_FIRE,
                com.applemusicktv.data.OnboardingPreferences.REMOTE_GOOGLE,
            )
            fun remoteLabel(v: String) = when (v) {
                com.applemusicktv.data.OnboardingPreferences.REMOTE_FIRE -> "Fire TV"
                com.applemusicktv.data.OnboardingPreferences.REMOTE_GOOGLE -> "Google TV"
                else -> "自动"
            }
            fun stepRemote(dir: Int) {
                val i = (remoteOrder.indexOf(remote).coerceAtLeast(0) + dir + remoteOrder.size) % remoteOrder.size
                remote = remoteOrder[i]; playerVm.setRemoteOverride(remote)
            }
            Stepper(
                label = "遥控器类型",
                value = remoteLabel(remote),
                sub = "Google TV 显示屏幕媒体和队列控制；Fire TV 使用 Menu 键",
                onDec = { stepRemote(-1) },
                onInc = { stepRemote(1) },
            )

            // ── A/V SYNC ──────────────────────────────────────────────────
            // One delay drives both the beat visuals and the lyric clock. A ~200 ms display baseline is
            // built in (so this normally reads 0); Automatic adds a Bluetooth estimate on top when a BT
            // output is live. "Extra" is your own nudge on top of all that.
            SectionLabel("音画同步")
            Text(
                "输出：${pstate.avOutputLabel}",
                fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium,
            )
            Text(
                if (pstate.avSyncAuto && pstate.avOnBluetooth) "已应用蓝牙延迟：+350 ms（估算）"
                else "标准时序",
                fontSize = 10.sp, color = Color(0xFF999999), modifier = Modifier.padding(bottom = 4.dp),
            )
            Toggle(
                label = "自动",
                on = pstate.avSyncAuto,
                sub = if (pstate.avSyncAuto) "自动附加蓝牙延迟估算"
                      else "手动 — 仅在基线上附加下方的额外偏移",
                onToggle = { playerVm.setAvSyncAuto(!pstate.avSyncAuto) },
            )
            Stepper(
                label = "额外偏移", value = "${state.lyricsOffsetMs}ms",
                sub = "作用于歌词、节拍和 MV 对口型。负值 = 提前，正值 = 延后",
                onDec = { vm.setLyricsOffset(state.lyricsOffsetMs - 50) },
                onInc = { vm.setLyricsOffset(state.lyricsOffsetMs + 50) },
            )

            Spacer(Modifier.height(6.dp))
            // ── Reveal dev menu ───────────────────────────────────────────
            ActionBtn(if (showDev) "▾ 收起开发者菜单" else "▸ 打开开发者菜单", Color(0xFF1C1C1E)) { showDev = !showDev }

            if (showDev) {
                SectionLabel("账号")
                StatusChip("Bearer 令牌", state.hasBearer, if (!state.hasBearer) "刷新后抓取" else "正常")
                StatusChip("Music-User-Token", state.hasMUT, state.mutSetAt?.let { "设置于 $it" } ?: if (!state.hasMUT) "通过手机网页服务器设置" else "正常")
                StatusChip("服务器", state.serverOk, if (state.serverOk) "可连接" else "无法连接 — 独立模式")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionBtn("重新检测服务器", Color(0xFF1A2A1A), small = true) { vm.recheckServer(playerVm, onDone = onDataRefresh) }
                    ActionBtn("刷新", Color(0xFF2A2A2A), small = true) { vm.refresh(onDone = onDataRefresh) }
                    ActionBtn("重新运行设置", Color(0xFF2A2A1A), small = true) { playerVm.resetOnboarding() }
                    ActionBtn("清除令牌", Color(0xFF3A1A1A), small = true) { vm.clearMUT() }
                    // Hard quit: stop playback + kill the process so the next launch is a true cold start.
                    ActionBtn("强制退出", Color(0xFF3A1A1A), small = true) {
                        playerVm.stopPlayback()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }

                SectionLabel("连接")
                if (state.webServerUrl.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFF0D1F0D), RoundedCornerShape(10.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("手机网页服务器", fontSize = 9.sp, color = Color(0xFF6BCB77), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text(state.webServerUrl, fontSize = 15.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text("在手机上打开 → 粘贴 Music-User-Token", fontSize = 11.sp, color = Color(0xFF557755))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("电脑服务器", fontSize = 9.sp, color = Color(0xFF888888), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (pcIpEditing) {
                            // Editing: OK opened this, so the field may hold focus + IME. Done/Back closes.
                            LaunchedEffect(Unit) { runCatching { pcIpFocus.requestFocus() } }
                            androidx.activity.compose.BackHandler(enabled = true) { pcIpEditing = false; hideIme() }
                            Box(
                                Modifier.weight(1f).background(Color(0xFF0D0D0D), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                BasicTextField(
                                    value = pcIpDraft, onValueChange = { pcIpDraft = it }, singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                    cursorBrush = SolidColor(Color(0xFFFA233B)),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { vm.setPcServerIp(pcIpDraft); pcIpEditing = false; hideIme() }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(pcIpFocus),
                                    decorationBox = { inner ->
                                        if (pcIpDraft.isEmpty()) Text("192.168.x.x", fontSize = 13.sp, color = Color(0xFF444444), fontFamily = FontFamily.Monospace)
                                        inner()
                                    },
                                )
                            }
                            ActionBtn("完成", Color(0xFF2A2A2A), small = true) { vm.setPcServerIp(pcIpDraft); pcIpEditing = false; hideIme() }
                        } else {
                            // Idle: a plain button. Focus can land here with no IME. OK opens the editor.
                            Surface(
                                onClick = { pcIpEditing = true },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF0D0D0D), focusedContainerColor = Color(0xFF1E1E1E)),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    pcIpDraft.ifEmpty { "192.168.x.x — 按确定键编辑" },
                                    fontSize = 13.sp, color = if (pcIpDraft.isEmpty()) Color(0xFF666666) else Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                )
                            }
                        }
                        if (pcIpDraft.isNotEmpty()) ActionBtn("清除", Color(0xFF3A1A1A), small = true) { pcIpDraft = ""; vm.setPcServerIp("") }
                    }
                    if (state.pcServerIp.isNotEmpty())
                        Text("当前使用：${state.pcServerIp}:3000", fontSize = 10.sp, color = Color(0xFF6BCB77), fontFamily = FontFamily.Monospace)
                }

                // ── 自定义音源（LX 引擎）────────────────────────────────
                SectionLabel("自定义音源")
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("搜索与播放走 LX 引擎（酷我 + 导入的音源脚本，洛雪 userApi 格式）", fontSize = 11.sp, color = Color(0xFF777777))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ActionBtn(
                            if (state.lxEnabled) "已启用 · 点击关闭" else "已关闭 · 点击启用",
                            if (state.lxEnabled) Color(0xFF1A2A1A) else Color(0xFF2A2A2A),
                            small = true,
                        ) { vm.setLxEnabled(!state.lxEnabled) }
                    }
                    if (state.lxEnabled) {
                        Text("已导入音源（${state.lxScripts.size}）", fontSize = 9.sp, color = Color(0xFF888888), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        if (state.lxScripts.isEmpty()) {
                            Text("还没有导入音源——没有脚本时仅酷我内置搜索可用，取链可能失败；建议先导入一个音源脚本", fontSize = 11.sp, color = Color(0xFFAA7744))
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
                        // 导入（URL）
                        if (lxImportEditing) {
                            LaunchedEffect(Unit) { runCatching { lxImportFocus.requestFocus() } }
                            androidx.activity.compose.BackHandler(enabled = true) { lxImportEditing = false; hideIme() }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.weight(1f).background(Color(0xFF0D0D0D), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    BasicTextField(
                                        value = lxImportDraft, onValueChange = { lxImportDraft = it }, singleLine = true,
                                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                        cursorBrush = SolidColor(Color(0xFFFA233B)),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { if (lxImportDraft.isNotBlank()) vm.importLxUrl(lxImportDraft); lxImportEditing = false; hideIme() }),
                                        modifier = Modifier.fillMaxWidth().focusRequester(lxImportFocus),
                                        decorationBox = { inner ->
                                            if (lxImportDraft.isEmpty()) Text("https://…/latest.js", fontSize = 13.sp, color = Color(0xFF444444), fontFamily = FontFamily.Monospace)
                                            inner()
                                        },
                                    )
                                }
                                ActionBtn("导入", Color(0xFF1A2A1A), small = true) { if (lxImportDraft.isNotBlank()) vm.importLxUrl(lxImportDraft); lxImportEditing = false; hideIme() }
                            }
                        } else {
                            ActionBtn("从 URL 导入音源脚本", Color(0xFF2A2A2A), small = true) { lxImportEditing = true }
                        }
                    }
                }

                // ── LOGS (app + network) ──────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("日志")
                    ActionBtn("清除", Color(0xFF2A2A2A), small = true) { vm.clearLogs() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp)).padding(12.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(netLogs.reversed()) { line ->
                        Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF6C8CD5), lineHeight = 13.sp)
                    }
                    items(state.logs.reversed()) { log ->
                        Text(log.message, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = when (log.level) {
                                "ERROR" -> Color(0xFFFF6B6B); "WARN" -> Color(0xFFFFD93D)
                                "OK"    -> Color(0xFF6BCB77); else  -> Color(0xFFAAAAAA)
                            }, lineHeight = 13.sp)
                    }
                }

                SectionLabel("问题反馈")
                BugReportRow(state.webServerUrl)

                SectionLabel("危险区域")
                ActionBtn("重置应用", Color(0xFF4A1414)) { confirmReset = true }
                Text("清除令牌、置顶和所有设置，然后重启。", fontSize = 10.sp, color = Color(0xFF775555))
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (confirmReset) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(360.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(14.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("重置应用？", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("这将清除你的令牌、置顶和所有设置，然后关闭应用。", fontSize = 12.sp, color = Color(0xFF999999))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionBtn("取消", Color(0xFF2A2A2A)) { confirmReset = false }
                    ActionBtn("重置", Color(0xFFB22222)) {
                        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        am.clearApplicationUserData()
                    }
                }
            }
        }
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
