package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.AppGraph
import org.json.JSONArray
import org.json.JSONObject

// backup de tudo num arquivo (dá pra mandar pro Drive pelo seletor do Android)
object Backup {
    private const val APP = "PushLife"
    private const val VERSION = 1
    private val FILES = listOf("bank", "profile", "blocking", "progress", "packs")

    fun export(context: Context, pretty: Boolean = true): String {
        val prefs = JSONObject()
        for (file in FILES) {
            val values = JSONObject()
            context.getSharedPreferences(file, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val (type, json) = when (value) {
                    is Boolean -> "b" to value
                    is Int -> "i" to value
                    is Long -> "l" to value
                    is Float -> "f" to value.toDouble()
                    is String -> "s" to value
                    is Set<*> -> "set" to JSONArray(value.filterIsInstance<String>())
                    else -> return@forEach
                }
                values.put(key, JSONObject().put("t", type).put("v", json))
            }
            prefs.put(file, values)
        }
        return JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("prefs", prefs)
            .let { if (pretty) it.toString(2) else it.toString() }
    }

    // troca os dados pelos do backup; false se não for backup do PushLife
    // o filtro de sites adultos ligado continua ligado, mesmo que o backup seja de antes dele
    fun import(context: Context, text: String): Boolean {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (root.optString("app") != APP) return false
        val prefs = root.optJSONObject("prefs") ?: return false
        keepingAdultFilter(context) { restore(context, prefs) }
        AppGraph.load(context)
        return true
    }

    private fun restore(context: Context, prefs: JSONObject) {
        for (file in FILES) {
            val values = prefs.optJSONObject(file) ?: JSONObject()
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit(commit = true) {
                clear()
                for (key in values.keys()) {
                    val entry = values.optJSONObject(key) ?: continue
                    when (entry.optString("t")) {
                        "b" -> putBoolean(key, entry.optBoolean("v"))
                        "i" -> putInt(key, entry.optInt("v"))
                        "l" -> putLong(key, entry.optLong("v"))
                        "f" -> putFloat(key, entry.optDouble("v").toFloat())
                        "s" -> putString(key, entry.optString("v"))
                        "set" -> {
                            val array = entry.optJSONArray("v") ?: JSONArray()
                            putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
                        }
                    }
                }
            }
        }
    }

    // apaga tudo e volta pras boas-vindas; o filtro de sites adultos só sai desinstalando o app
    fun wipe(context: Context) {
        keepingAdultFilter(context) {
            for (file in FILES) context.getSharedPreferences(file, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        }
        AppGraph.load(context)
    }

    // se o filtro tava ligado antes, liga de novo depois (desligar só pelo desafio da tela de bloqueio)
    private fun keepingAdultFilter(context: Context, change: () -> Unit) {
        val blocking = context.getSharedPreferences("blocking", Context.MODE_PRIVATE)
        val wasOn = blocking.getBoolean(KEY_ADULT_FILTER, false)
        change()
        if (wasOn) blocking.edit(commit = true) { putBoolean(KEY_ADULT_FILTER, true) }
    }

    private const val KEY_ADULT_FILTER = "adult_filter" // mesmo nome do BlockSettingsRepository
}
