package com.pushlife.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pushlife.app.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Contas com e-mail, senha e um código de 6 dígitos mandado por e-mail (para criar a conta e para entrar).
 *
 * Quem gera, manda e confere o código é o servidor (pasta functions, Cloud Functions). Ele também cria a
 * conta e reserva o @; no fim devolve um token e o app entra com ele.
 *
 * No Firestore:
 * - `usernames/{arroba}` → { uid }: existir o documento = @ ocupado.
 * - `users/{uid}` → { username, name, email, backup, backupAt }: perfil e backup de cada conta.
 *
 * Sem o arquivo google-services.json no projeto o Firebase não existe: [available] fica false
 * e o app esconde tudo de conta.
 */
object Accounts {
    enum class Error {
        NOT_CONFIGURED, NETWORK, USERNAME_TAKEN, EMAIL_IN_USE, WEAK_PASSWORD, BAD_EMAIL, WRONG_CREDENTIALS,
        CODE_WRONG, CODE_EXPIRED, TOO_MANY, WAIT, EMAIL_FAILED, UNKNOWN,
    }

    class AccountException(val error: Error, cause: Throwable? = null) : Exception(error.name, cause)

    data class Account(val uid: String, val email: String)

    /** O que vem da nuvem ao entrar: o backup é null se a conta ainda não tinha salvo nada. */
    data class LoginResult(val name: String, val username: String, val backup: String?)

    private const val REGION = "southamerica-east1"
    private const val BACKUP_EVERY_MS = 5 * 60 * 1000L
    private const val MAX_BACKUP_CHARS = 900_000 // documento do Firestore aceita até 1 MB

    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private var functions: FirebaseFunctions? = null
    private var lastBackupMs = 0L

    private val _current = MutableStateFlow<Account?>(null)
    val current: StateFlow<Account?> = _current.asStateFlow()

    val available: Boolean get() = auth != null

    fun init(context: Context) {
        val app = runCatching { FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context) }.getOrNull() ?: return
        val a = FirebaseAuth.getInstance(app)
        auth = a
        db = FirebaseFirestore.getInstance(app)
        functions = FirebaseFunctions.getInstance(app, REGION)
        a.addAuthStateListener { fa -> _current.value = fa.currentUser?.let { Account(it.uid, it.email.orEmpty()) } }
    }

    /** true se ninguém usa esse @ (pergunta direto ao servidor, nunca ao cache). */
    suspend fun isUsernameFree(handle: String): Boolean = guard {
        !usernames().document(handle).get(Source.SERVER).await().exists()
    }

    /** Passo 1 do cadastro: confere e-mail e @ e manda o código para o e-mail. */
    suspend fun requestSignUpCode(email: String, handle: String) {
        call("requestCode", mapOf("purpose" to "signup", "email" to email.trim(), "username" to handle, "lang" to lang()))
    }

    /** Passo 2 do cadastro: com o código certo, o servidor cria a conta e o app já entra nela. */
    suspend fun verifySignUp(email: String, code: String, password: String, handle: String, name: String) {
        val data = call(
            "verifyCode",
            mapOf("purpose" to "signup", "email" to email.trim(), "code" to code, "password" to password, "username" to handle, "name" to name),
        )
        signInWithToken(data)
    }

    /** Passo 1 do login: confere a senha e manda o código para o e-mail. */
    suspend fun requestLoginCode(email: String, password: String) {
        call("requestCode", mapOf("purpose" to "login", "email" to email.trim(), "password" to password, "lang" to lang()))
    }

    /** Passo 2 do login: entra e devolve o que estava salvo na conta. */
    suspend fun verifyLogin(email: String, code: String): LoginResult {
        signInWithToken(call("verifyCode", mapOf("purpose" to "login", "email" to email.trim(), "code" to code)))
        val uid = auth?.currentUser?.uid ?: throw AccountException(Error.UNKNOWN)
        return guard {
            val doc = users().document(uid).get(Source.SERVER).await()
            LoginResult(
                name = doc.getString("name").orEmpty(),
                username = doc.getString("username").orEmpty(),
                backup = doc.getString("backup"),
            )
        }
    }

    suspend fun sendPasswordReset(email: String) = guard { auth!!.sendPasswordResetEmail(email.trim()).await() }

    fun signOut() {
        auth?.signOut()
        lastBackupMs = 0L
    }

    /** Envia o backup para a conta (no máximo a cada 5 min, a não ser que [force]). Funciona offline: o Firestore manda depois. */
    fun backup(context: Context, force: Boolean = false) {
        val user = auth?.currentUser ?: return
        if (!AppGraph.profile.state.value.onboardingDone) return // nunca sobrescreve a nuvem com um app vazio
        val now = System.currentTimeMillis()
        if (!force && now - lastBackupMs < BACKUP_EVERY_MS) return
        val json = Backup.export(context, pretty = false)
        if (json.length > MAX_BACKUP_CHARS) return
        lastBackupMs = now
        users().document(user.uid).set(mapOf("backup" to json, "backupAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    private suspend fun signInWithToken(data: Map<*, *>) {
        val token = data["token"] as? String ?: throw AccountException(Error.UNKNOWN)
        guard { auth!!.signInWithCustomToken(token).await() }
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Map<*, *> = guard {
        val f = functions ?: throw AccountException(Error.NOT_CONFIGURED)
        f.getHttpsCallable(name).call(payload).await().getData() as? Map<*, *> ?: emptyMap<String, Any>()
    }

    private fun lang() = Locale.getDefault().language

    private fun usernames() = db?.collection("usernames") ?: throw AccountException(Error.NOT_CONFIGURED)
    private fun users() = db?.collection("users") ?: throw AccountException(Error.NOT_CONFIGURED)

    /** Traduz os erros do Firebase e do servidor para os nossos. */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: AccountException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFunctionsException) {
        // O servidor manda o nome do erro na mensagem (ex.: "USERNAME_TAKEN").
        val known = Error.entries.firstOrNull { it.name == e.message }
        throw AccountException(
            known ?: when (e.code) {
                FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> Error.NETWORK
                else -> Error.UNKNOWN
            },
            e,
        )
    } catch (e: FirebaseNetworkException) {
        throw AccountException(Error.NETWORK, e)
    } catch (e: FirebaseFirestoreException) {
        throw AccountException(if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) Error.NETWORK else Error.UNKNOWN, e)
    } catch (e: Exception) {
        throw AccountException(Error.UNKNOWN, e)
    }
}
