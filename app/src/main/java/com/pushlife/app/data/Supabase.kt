package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Conversa direto com o Supabase (login em /auth/v1 e banco em /rest/v1), sem biblioteca extra.
 * O endereço e a chave pública vêm de app/supabase.properties (veja CONTAS.md).
 */
internal class Supabase(context: Context) {
    val url: String = context.getString(R.string.supabase_url).trimEnd('/')
    private val key: String = context.getString(R.string.supabase_key)
    private val prefs = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    val configured: Boolean get() = url.startsWith("https://") && key.isNotBlank()

    /** Erro devolvido pelo Supabase: [code] é o código dele (ex.: "otp_expired"), [message] o texto. */
    class ApiError(val status: Int, val code: String, override val message: String) : Exception(message)

    data class Session(val uid: String, val email: String, val accessToken: String, val refreshToken: String, val expiresAt: Long)

    // ---------- sessão guardada no celular ----------

    fun loadSession(): Session? {
        val uid = prefs.getString("uid", null) ?: return null
        return Session(
            uid = uid,
            email = prefs.getString("email", "").orEmpty(),
            accessToken = prefs.getString("access", "").orEmpty(),
            refreshToken = prefs.getString("refresh", "").orEmpty(),
            expiresAt = prefs.getLong("expires_at", 0L),
        )
    }

    fun saveSession(json: JSONObject): Session {
        val user = json.getJSONObject("user")
        val session = Session(
            uid = user.getString("id"),
            email = user.optString("email"),
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
        )
        prefs.edit {
            putString("uid", session.uid)
            putString("email", session.email)
            putString("access", session.accessToken)
            putString("refresh", session.refreshToken)
            putLong("expires_at", session.expiresAt)
        }
        return session
    }

    fun clearSession() = prefs.edit { clear() }

    /** Token válido para falar com o banco; renova sozinho quando está para vencer. */
    suspend fun accessToken(): String? {
        val s = loadSession() ?: return null
        if (System.currentTimeMillis() < s.expiresAt - 60_000) return s.accessToken
        val json = request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", s.refreshToken)) as JSONObject
        return saveSession(json).accessToken
    }

    // ---------- HTTP ----------

    /** Faz a chamada e devolve o JSON (objeto, lista, texto solto ou null). Lança [ApiError] ou [IOException]. */
    suspend fun request(method: String, path: String, body: Any? = null, token: String? = null, prefer: String? = null): Any? =
        withContext(Dispatchers.IO) {
            val conn = URL(url + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("apikey", key)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                prefer?.let { conn.setRequestProperty("Prefer", it) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw parseError(status, text)
                val trimmed = text.trim()
                when {
                    trimmed.isEmpty() -> null
                    trimmed.startsWith("[") -> JSONArray(trimmed)
                    trimmed.startsWith("{") -> JSONObject(trimmed)
                    else -> trimmed // valores soltos, como o true/false de uma função do banco
                }
            } finally {
                conn.disconnect()
            }
        }

    private fun parseError(status: Int, text: String): ApiError {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return ApiError(status, "", text)
        val code = json.optString("error_code").ifEmpty { json.optString("code") }.ifEmpty { json.optString("error") }
        val message = json.optString("msg").ifEmpty { json.optString("message") }.ifEmpty { json.optString("error_description") }
        return ApiError(status, code, message)
    }
}
