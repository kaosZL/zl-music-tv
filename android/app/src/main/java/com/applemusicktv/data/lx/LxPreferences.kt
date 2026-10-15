package com.applemusicktv.data.lx

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 自定义音源的已导入脚本条目 */
data class LxScript(
    val id: String,        // 文件名去扩展名
    val fileName: String,
    val name: String,      // 脚本 @name 或文件名
    val enabled: Boolean,
)

/**
 * 自定义音源偏好：总开关 + 脚本列表（文件本体存 filesDir/lx_sources/，由 LxScriptStore 管）。
 * 脚本启停变更通过 scriptsFlow 广播，LxEngine 监听并热重载。
 */
@Singleton
class LxPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("zl_lx_source", Context.MODE_PRIVATE)

    private val _enabledFlow = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabledFlow: StateFlow<Boolean> = _enabledFlow

    private val _scriptsFlow = MutableStateFlow(loadScripts())
    val scriptsFlow: StateFlow<List<LxScript>> = _scriptsFlow

    val isEnabled: Boolean get() = _enabledFlow.value

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabledFlow.value = value
    }

    fun loadScripts(): List<LxScript> {
        val raw = prefs.getString(KEY_SCRIPTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LxScript(
                    id = o.getString("id"),
                    fileName = o.getString("fileName"),
                    name = o.getString("name"),
                    enabled = o.getBoolean("enabled"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveScripts(scripts: List<LxScript>) {
        val arr = JSONArray()
        scripts.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("fileName", s.fileName)
                put("name", s.name)
                put("enabled", s.enabled)
            })
        }
        prefs.edit { putString(KEY_SCRIPTS, arr.toString()) }
        _scriptsFlow.value = scripts
    }

    fun setEnabled(id: String, enabled: Boolean) {
        saveScripts(loadScripts().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun remove(id: String) {
        saveScripts(loadScripts().filter { it.id != id })
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCRIPTS = "scripts"
    }
}
