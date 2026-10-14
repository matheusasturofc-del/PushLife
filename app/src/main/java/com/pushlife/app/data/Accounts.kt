package com.pushlife.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.pushlife.app.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Contas com e-mail e senha (Firebase Authentication) e o @ único de cada pessoa (Firestore).
 *
 * No Firestore:
 * - `usernames/{arroba}` → { uid }: existir o documento = @ ocupado. As regras (firestore.rules) só
 *   deixam criar, nunca sobrescrever, então dois celulares nunca ficam com o mesmo @.
 * - `users/{uid}` → { username, name, email, backup, backupAt }: perfil e backup de cada conta.
 *
 * Sem o arquivo google-services.json no projeto o Firebase não existe: [available] fica false
 * e o app esconde tudo de conta.
 */
object Accounts {
    enum class Error { NOT_CONFIGURED, NETWORK, USERNAME_TAKEN, EMAIL_IN_USE, WEAK_PASSWORD, BAD_EMAIL, WRONG_CREDENTIALS, UNKNOWN }

    class AccountException(val error: Error, cause: Throwable? = null) : Exception(error.name, cause)

    data class Account(val uid: String, val email: String)

    /** O que vem da nuvem ao entrar: o backup é null se a conta ainda não tinha salvo nada. */
    data class LoginResult(val name: String, val username: String, val backup: String?)

    private const val BACKUP_EVERY_MS = 5 * 60 * 1000L
    private const val MAX_BACKUP_CHARS = 900_000 // documento do Firestore aceita até 1 MB

    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private var lastBackupMs = 0L

    private val _current = MutableStateFlow<Account?>(null)
    val current: StateFlow<Account?> = _current.asStateFlow()

    val available: Boolean get() = auth != null

    fun init(context: Context) {
        val app = runCatching { FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context) }.getOrNull() ?: return
        val a = FirebaseAuth.getInstance(app)
        auth = a
        db = FirebaseFirestore.getInstance(app)
        a.addAuthStateListener { fa -> _current.value = fa.currentUser?.let { Account(it.uid, it.email.orEmpty()) } }
    }

    /** true se ninguém usa esse @ (pergunta direto ao servidor, nunca ao cache). */
    suspend fun isUsernameFree(handle: String): Boolean = guard {
        !usernames().document(handle).get(Source.SERVER).await().exists()
    }

    /** Cria a conta e reserva o @ numa transação; se o @ for pego no meio do caminho, desfaz a conta. */
    suspend fun signUp(email: String, password: String, handle: String, name: String) = guard {
        if (!isUsernameFree(handle)) throw AccountException(Error.USERNAME_TAKEN)
        val a = auth ?: throw AccountException(Error.NOT_CONFIGURED)
        val user = a.createUserWithEmailAndPassword(email.trim(), password).await().user ?: throw AccountException(Error.UNKNOWN)
        try {
            val firestore = db!!
            firestore.runTransaction(Transaction.Function<Unit> { tx ->
                val ref = usernames().document(handle)
                if (tx.get(ref).exists()) throw FirebaseFirestoreException("taken", FirebaseFirestoreException.Code.ABORTED)
                tx.set(ref, mapOf("uid" to user.uid))
                tx.set(
                    users().document(user.uid),
                    mapOf("username" to handle, "name" to name, "email" to email.trim(), "createdAt" to FieldValue.serverTimestamp()),
                )
                Unit
            }).await()
        } catch (e: Exception) {
            runCatching { user.delete().await() }
            a.signOut()
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ABORTED) throw AccountException(Error.USERNAME_TAKEN, e)
            throw e
        }
    }

    suspend fun signIn(email: String, password: String): LoginResult = guard {
        val a = auth ?: throw AccountException(Error.NOT_CONFIGURED)
        val user = a.signInWithEmailAndPassword(email.trim(), password).await().user ?: throw AccountException(Error.UNKNOWN)
        val doc = users().document(user.uid).get(Source.SERVER).await()
        LoginResult(
            name = doc.getString("name").orEmpty(),
            username = doc.getString("username").orEmpty(),
            backup = doc.getString("backup"),
        )
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

    private fun usernames() = db?.collection("usernames") ?: throw AccountException(Error.NOT_CONFIGURED)
    private fun users() = db?.collection("users") ?: throw AccountException(Error.NOT_CONFIGURED)

    /** Traduz os erros do Firebase para os nossos. */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: AccountException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseNetworkException) {
        throw AccountException(Error.NETWORK, e)
    } catch (e: FirebaseAuthUserCollisionException) {
        throw AccountException(Error.EMAIL_IN_USE, e)
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw AccountException(Error.WEAK_PASSWORD, e)
    } catch (e: FirebaseAuthInvalidUserException) {
        throw AccountException(Error.WRONG_CREDENTIALS, e)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        // No cadastro é e-mail mal escrito; no login é e-mail ou senha errados.
        throw AccountException(if (e.errorCode == "ERROR_INVALID_EMAIL") Error.BAD_EMAIL else Error.WRONG_CREDENTIALS, e)
    } catch (e: FirebaseFirestoreException) {
        throw AccountException(if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) Error.NETWORK else Error.UNKNOWN, e)
    } catch (e: Exception) {
        throw AccountException(Error.UNKNOWN, e)
    }
}
