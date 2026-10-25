package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

// contas: e-mail + senha + código de 6 dígitos no e-mail (Supabase Auth)
// o @ já fica reservado no cadastro (gatilho no banco) — ver supabase/schema.sql e CONTAS.md
object Accounts {
    enum class Error {
        NOT_CONFIGURED, NETWORK, USERNAME_TAKEN, EMAIL_IN_USE, WEAK_PASSWORD, BAD_EMAIL, WRONG_CREDENTIALS,
        CODE_WRONG, CODE_EXPIRED, TOO_MANY, WAIT, EMAIL_FAILED, UNKNOWN,
    }

    class AccountException(val error: Error, cause: Throwable? = null) : Exception(error.name, cause)

    data class Account(val uid: String, val email: String)

    // backup vem null se a conta nunca salvou nada
    data class LoginResult(val name: String, val username: String, val backup: String?)

    private const val BACKUP_EVERY_MS = 5 * 60 * 1000L
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var api: Supabase? = null
    private var appContext: Context? = null
    private var lastBackupMs = 0L

    // "signup" quando a conta existe mas o e-mail nunca foi confirmado
    private var loginCodeType = "email"

    private val _current = MutableStateFlow<Account?>(null)
    val current: StateFlow<Account?> = _current.asStateFlow()

    val available: Boolean get() = api != null

    fun init(context: Context) {
        val s = Supabase(context)
        if (!s.configured) return
        api = s
        appContext = context.applicationContext
        _current.value = s.loadSession()?.let { Account(it.uid, it.email) }
    }

    // @ livre?
    suspend fun isUsernameFree(handle: String): Boolean = guard {
        api().request("POST", "/rest/v1/rpc/username_available", JSONObject().put("name", handle)).toString() == "true"
    }

    // ---------- criar conta ----------

    // cadastro, passo 1: cria a conta e o Supabase manda o código
    suspend fun requestSignUpCode(email: String, password: String, handle: String, name: String) {
        if (!isUsernameFree(handle)) throw AccountException(Error.USERNAME_TAKEN)
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject().put("username", handle).put("name", name))
        val res = guard { api().request("POST", "/auth/v1/signup", body) as? JSONObject }
        // e-mail já confirmado: o Supabase finge que deu certo, mas vem sem "identities"
        val identities = (res?.optJSONObject("user") ?: res)?.optJSONArray("identities")
        if (identities != null && identities.length() == 0) throw AccountException(Error.EMAIL_IN_USE)
    }

    suspend fun resendSignUpCode(email: String) {
        guard { api().request("POST", "/auth/v1/resend", JSONObject().put("type", "signup").put("email", email.trim())) }
    }

    // passo 2: código certo, conta confirmada e já entra
    suspend fun verifySignUp(email: String, code: String) {
        verify("signup", email, code)
    }

    // ---------- entrar ----------

    // login, passo 1: confere a senha e manda o código
    suspend fun requestLoginCode(email: String, password: String) {
        val mail = email.trim()
        try {
            guard { api().request("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", mail).put("password", password)) }
        } catch (e: AccountException) {
            // nunca digitou o código do cadastro: manda de novo
            if ((e.cause as? Supabase.ApiError)?.code == "email_not_confirmed") {
                resendSignUpCode(mail)
                loginCodeType = "signup"
                return
            }
            throw e
        }
        guard { api().request("POST", "/auth/v1/otp", JSONObject().put("email", mail).put("create_user", false)) }
        loginCodeType = "email"
    }

    // passo 2: entra e puxa o backup da conta
    suspend fun verifyLogin(email: String, code: String): LoginResult {
        verify(loginCodeType, email, code)
        return loadProfile()
    }

    // ---------- esqueci a senha ----------

    suspend fun requestPasswordReset(email: String) {
        guard { api().request("POST", "/auth/v1/recover", JSONObject().put("email", email.trim())) }
    }

    // código + senha nova e já entra
    suspend fun resetPassword(email: String, code: String, newPassword: String): LoginResult {
        verify("recovery", email, code)
        guard {
            api().request("PUT", "/auth/v1/user", JSONObject().put("password", newPassword), token = api().accessToken())
        }
        return loadProfile()
    }

    // apagar conta, passo 1: manda o código
    suspend fun requestDeleteCode() {
        val email = api().loadSession()?.email ?: throw AccountException(Error.UNKNOWN)
        guard { api().request("POST", "/auth/v1/otp", JSONObject().put("email", email).put("create_user", false)) }
    }

    // passo 2: apaga tudo de vez (conta, @ e backup)
    suspend fun deleteAccount(code: String) {
        val email = api().loadSession()?.email ?: throw AccountException(Error.UNKNOWN)
        verify("email", email, code)
        guard { api().request("POST", "/rest/v1/rpc/delete_my_account", JSONObject(), token = api().accessToken()) }
        api().clearSession()
        _current.value = null
        lastBackupMs = 0L
    }

    fun signOut() {
        val s = api ?: return
        val token = s.loadSession()?.accessToken
        s.clearSession()
        _current.value = null
        lastBackupMs = 0L
        if (token != null) io.launch { runCatching { s.request("POST", "/auth/v1/logout", token = token) } }
    }

    // ---------- backup ----------

    // sobe o backup no máx. a cada 5 min (menos com force); se falhar, vai na próxima
    fun backup(context: Context, force: Boolean = false) {
        val s = api ?: return
        val uid = s.loadSession()?.uid ?: return
        if (!AppGraph.profile.state.value.onboardingDone) return // nunca sobrescreve a nuvem com um app vazio
        val now = System.currentTimeMillis()
        if (!force && now - lastBackupMs < BACKUP_EVERY_MS) return
        val json = Backup.export(context, pretty = false)
        lastBackupMs = now
        io.launch {
            val ok = runCatching {
                s.request(
                    "PATCH", "/rest/v1/profiles?id=eq.$uid",
                    JSONObject().put("backup", json).put("backup_at", Instant.now().toString()),
                    token = s.accessToken(),
                    prefer = "return=minimal",
                )
            }.isSuccess
            if (!ok) lastBackupMs = 0L
        }
    }

    // ---------- Google, Discord e Twitch ----------

    // logins sociais (nome igual ao do Supabase)
    enum class Provider(val id: String) { GOOGLE("google"), DISCORD("discord"), TWITCH("twitch") }

    // estado do login social (vai pro navegador e volta)
    sealed interface OAuthState {
        data object Idle : OAuthState
        data object Working : OAuthState
        // conta nova: falta escolher o @ (nome vem do provedor, se tiver)
        data class NeedsUsername(val name: String) : OAuthState
        data class Done(val result: LoginResult) : OAuthState
        data class Failed(val error: Error) : OAuthState
    }

    const val OAUTH_REDIRECT = "pushlife://auth-callback"

    private val _oauth = MutableStateFlow<OAuthState>(OAuthState.Idle)
    val oauth: StateFlow<OAuthState> = _oauth.asStateFlow()

    // link pro navegador; PKCE: só quem tem o segredo troca o código por sessão
    fun oauthUrl(provider: Provider): String {
        val base = api().url
        val verifier = Pkce.newVerifier()
        oauthPrefs()?.edit { putString("verifier", verifier) }
        val redirect = java.net.URLEncoder.encode(OAUTH_REDIRECT, "UTF-8")
        return "$base/auth/v1/authorize?provider=${provider.id}&redirect_to=$redirect" +
            "&code_challenge=${Pkce.challenge(verifier)}&code_challenge_method=s256"
    }

    // volta do navegador (pushlife://auth-callback?code=...)
    suspend fun completeOAuth(code: String?, error: String?) {
        if (code == null) {
            _oauth.value = OAuthState.Failed(if (error == null) Error.UNKNOWN else Error.WRONG_CREDENTIALS)
            return
        }
        _oauth.value = OAuthState.Working
        try {
            val verifier = oauthPrefs()?.getString("verifier", null) ?: throw AccountException(Error.UNKNOWN)
            val json = guard {
                api().request("POST", "/auth/v1/token?grant_type=pkce", JSONObject().put("auth_code", code).put("code_verifier", verifier)) as? JSONObject
            } ?: throw AccountException(Error.UNKNOWN)
            oauthPrefs()?.edit { remove("verifier") }
            val session = api().saveSession(json)
            _current.value = Account(session.uid, session.email)
            val profile = loadProfileOrNull()
            _oauth.value = if (profile != null) {
                OAuthState.Done(profile)
            } else {
                val meta = json.optJSONObject("user")?.optJSONObject("user_metadata")
                val name = listOf("full_name", "name", "preferred_username", "nickname")
                    .map { meta?.optString(it).orEmpty() }.firstOrNull { it.isNotBlank() }.orEmpty()
                OAuthState.NeedsUsername(name.take(30))
            }
        } catch (e: AccountException) {
            _oauth.value = OAuthState.Failed(e.error)
        }
    }

    // conta social nova: cria o perfil com o @ escolhido
    suspend fun createProfile(handle: String, name: String) {
        val uid = api().loadSession()?.uid ?: throw AccountException(Error.UNKNOWN)
        guard {
            api().request(
                "POST", "/rest/v1/profiles",
                JSONObject().put("id", uid).put("username", handle).put("name", name),
                token = api().accessToken(),
                prefer = "return=minimal",
            )
        }
        _oauth.value = OAuthState.Done(LoginResult(name = name, username = handle, backup = null))
    }

    // reseta o estado depois de tratar (ou desistir)
    fun consumeOAuth() {
        _oauth.value = OAuthState.Idle
    }

    private fun oauthPrefs() = appContext?.getSharedPreferences("oauth", Context.MODE_PRIVATE)

    // ---------- interno ----------

    private fun api() = api ?: throw AccountException(Error.NOT_CONFIGURED)

    private suspend fun verify(type: String, email: String, code: String) {
        val json = guard {
            api().request("POST", "/auth/v1/verify", JSONObject().put("type", type).put("email", email.trim()).put("token", code)) as? JSONObject
        } ?: throw AccountException(Error.UNKNOWN)
        val session = api().saveSession(json)
        _current.value = Account(session.uid, session.email)
    }

    private suspend fun loadProfile(): LoginResult = loadProfileOrNull() ?: LoginResult(name = "", username = "", backup = null)

    // perfil da conta, null se ainda não tem (login social novo)
    private suspend fun loadProfileOrNull(): LoginResult? = guard {
        val s = api().loadSession() ?: throw AccountException(Error.UNKNOWN)
        val rows = api().request("GET", "/rest/v1/profiles?id=eq.${s.uid}&select=username,name,backup", token = api().accessToken()) as? JSONArray
        val row = rows?.optJSONObject(0) ?: return@guard null
        LoginResult(
            name = row.optString("name"),
            username = row.optString("username"),
            backup = row.takeUnless { it.isNull("backup") }?.optString("backup"),
        )
    }

    // erros do Supabase -> os nossos
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: AccountException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw AccountException(Error.NETWORK, e)
    } catch (e: Supabase.ApiError) {
        throw AccountException(map(e), e)
    } catch (e: Exception) {
        throw AccountException(Error.UNKNOWN, e)
    }

    private fun map(e: Supabase.ApiError): Error {
        val msg = e.message.lowercase()
        return when {
            e.code == "weak_password" || "password should" in msg -> Error.WEAK_PASSWORD
            e.code == "email_address_invalid" || e.code == "validation_failed" || "invalid format" in msg -> Error.BAD_EMAIL
            e.code == "invalid_credentials" || "invalid login" in msg -> Error.WRONG_CREDENTIALS
            e.code == "otp_expired" || "token has expired" in msg || "otp" in msg -> Error.CODE_WRONG
            e.code == "user_already_exists" || e.code == "email_exists" -> Error.EMAIL_IN_USE
            e.code.startsWith("over_") || e.status == 429 || "security purposes" in msg -> Error.WAIT
            "sending" in msg && "email" in msg -> Error.EMAIL_FAILED
            // gatilho do perfil falha se pegaram o @ no meio do caminho
            "database error saving new user" in msg || e.code == "23505" -> Error.USERNAME_TAKEN
            e.code == "email_not_confirmed" -> Error.WRONG_CREDENTIALS
            else -> Error.UNKNOWN
        }
    }
}
