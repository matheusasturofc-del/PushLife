package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.pushlife.app.core.UpdateInfo
import com.pushlife.app.core.UpdatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// atualização obrigatória: versão abaixo do minVersionCode do update.json = só tela de atualizar
// guarda a última resposta (tirar a internet não destrava); sem nada guardado, segue normal
object UpdateChecker {
    private const val SOURCE_URL = "https://raw.githubusercontent.com/matheusasturofc-del/PushLife/HEAD/update.json"
    private const val CHECK_EVERY_MS = 15 * 60 * 1000L

    private val _required = MutableStateFlow<UpdateInfo?>(null)
    val required: StateFlow<UpdateInfo?> = _required.asStateFlow()

    private var lastCheckMs = 0L

    fun installedVersionCode(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(Long.MAX_VALUE)

    // usa a última resposta guardada (sem internet)
    fun loadCached(context: Context) {
        val json = prefs(context).getString(KEY_JSON, null) ?: return
        runCatching { parse(json) }.onSuccess { apply(context, it) }
    }

    // baixa de novo, no máximo a cada 15 min (a não ser com force)
    suspend fun refresh(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckMs < CHECK_EVERY_MS) return
        lastCheckMs = now
        val json = withContext(Dispatchers.IO) { runCatching { download() }.getOrNull() } ?: return
        val info = runCatching { parse(json) }.getOrNull() ?: return
        prefs(context).edit { putString(KEY_JSON, json) }
        apply(context, info)
    }

    private fun apply(context: Context, info: UpdateInfo) {
        _required.value = info.takeIf { UpdatePolicy.mustUpdate(installedVersionCode(context), it) }
    }

    private fun download(): String {
        // "t" muda a cada minuto pra fugir de cache velho
        val conn = URL("$SOURCE_URL?t=${System.currentTimeMillis() / 60_000}").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.useCaches = false
            check(conn.responseCode == HttpURLConnection.HTTP_OK)
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): UpdateInfo {
        val obj = JSONObject(json)
        val notes = obj.optJSONObject("notes")?.let { n -> n.keys().asSequence().associateWith { n.optString(it) } }
        return UpdateInfo(
            minVersionCode = obj.optLong("minVersionCode", 0),
            url = obj.optString("url"),
            notes = notes.orEmpty(),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    private const val KEY_JSON = "last_json"
}
