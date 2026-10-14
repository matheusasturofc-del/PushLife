package com.pushlife.app.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.core.Username
import com.pushlife.app.data.Accounts
import com.pushlife.app.filter.DnsFilterService
import com.pushlife.app.reminder.Reminders
import com.pushlife.app.ui.theme.Orange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Situação do @ enquanto a pessoa digita. */
enum class HandleStatus { EMPTY, TOO_SHORT, BAD_START, CHECKING, FREE, TAKEN, OFFLINE }

/** Confere o @ no servidor meio segundo depois que a pessoa para de digitar. */
@Composable
fun rememberHandleStatus(handle: String): HandleStatus {
    // Guarda junto o @ conferido: um resultado antigo nunca vale para o @ novo.
    var checked by remember { mutableStateOf("" to HandleStatus.EMPTY) }
    LaunchedEffect(handle) {
        val local = when (Username.problem(handle)) {
            Username.Problem.TOO_SHORT -> if (handle.isEmpty()) HandleStatus.EMPTY else HandleStatus.TOO_SHORT
            Username.Problem.BAD_START -> HandleStatus.BAD_START
            null -> null
        }
        if (local != null) {
            checked = handle to local
            return@LaunchedEffect
        }
        delay(450)
        checked = handle to try {
            if (Accounts.isUsernameFree(handle)) HandleStatus.FREE else HandleStatus.TAKEN
        } catch (e: Accounts.AccountException) {
            HandleStatus.OFFLINE
        }
    }
    return if (checked.first == handle) checked.second else HandleStatus.CHECKING
}

/** Campo do @: já limpa o que é digitado e mostra se está livre. */
@Composable
fun UsernameField(value: String, onValueChange: (String) -> Unit, status: HandleStatus, modifier: Modifier = Modifier) {
    val bad = status == HandleStatus.TAKEN || status == HandleStatus.TOO_SHORT || status == HandleStatus.BAD_START
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(Username.clean(it)) },
        label = { Text(stringResource(R.string.username_label)) },
        placeholder = { Text(stringResource(R.string.username_placeholder)) },
        prefix = { Text("@") },
        singleLine = true,
        isError = bad,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
        trailingIcon = {
            when (status) {
                HandleStatus.CHECKING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                HandleStatus.FREE -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Orange)
                HandleStatus.TAKEN -> Icon(Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                else -> {}
            }
        },
        supportingText = {
            val text = when (status) {
                HandleStatus.EMPTY -> R.string.username_rules
                HandleStatus.TOO_SHORT -> R.string.username_short
                HandleStatus.BAD_START -> R.string.username_start
                HandleStatus.CHECKING -> R.string.username_checking
                HandleStatus.FREE -> R.string.username_free
                HandleStatus.TAKEN -> R.string.username_taken
                HandleStatus.OFFLINE -> R.string.username_offline
            }
            Text(stringResource(text))
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** E-mail e senha, com o olhinho para mostrar a senha. */
@Composable
fun EmailPasswordFields(email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit, onDone: () -> Unit) {
    var show by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = email,
        onValueChange = { onEmail(it.trim().take(120)) },
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { onPassword(it.take(64)) },
        label = { Text(stringResource(R.string.account_password)) },
        supportingText = { Text(stringResource(R.string.account_password_hint)) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = stringResource(R.string.account_show_password))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

fun emailPasswordValid(email: String, password: String) = email.contains('@') && email.contains('.') && password.length >= 6

@StringRes
fun accountErrorText(error: Accounts.Error): Int = when (error) {
    Accounts.Error.NOT_CONFIGURED -> R.string.account_soon
    Accounts.Error.NETWORK -> R.string.err_network
    Accounts.Error.USERNAME_TAKEN -> R.string.username_taken
    Accounts.Error.EMAIL_IN_USE -> R.string.err_email_in_use
    Accounts.Error.WEAK_PASSWORD -> R.string.err_weak_password
    Accounts.Error.BAD_EMAIL -> R.string.err_bad_email
    Accounts.Error.WRONG_CREDENTIALS -> R.string.err_wrong_credentials
    Accounts.Error.CODE_WRONG -> R.string.err_code_wrong
    Accounts.Error.CODE_EXPIRED -> R.string.err_code_expired
    Accounts.Error.TOO_MANY -> R.string.err_too_many
    Accounts.Error.WAIT -> R.string.err_wait
    Accounts.Error.EMAIL_FAILED -> R.string.err_email_failed
    Accounts.Error.UNKNOWN -> R.string.err_unknown
}

const val CODE_LENGTH = 6
private const val RESEND_SECONDS = 60

/**
 * Onde a pessoa digita o código que chegou no e-mail. Quando completa os 6 dígitos, chama [onComplete].
 * "Reenviar" só libera depois de 1 minuto (o servidor também exige isso).
 */
@Composable
fun CodeEntry(email: String, code: String, onCode: (String) -> Unit, onComplete: () -> Unit, onResend: () -> Unit, onChangeEmail: () -> Unit) {
    var wait by rememberSaveable { mutableIntStateOf(RESEND_SECONDS) }
    LaunchedEffect(wait > 0) {
        while (wait > 0) {
            delay(1000)
            wait--
        }
    }
    Text(stringResource(R.string.code_sent_to, email), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = code,
        onValueChange = { typed ->
            val digits = typed.filter { it.isDigit() }.take(CODE_LENGTH)
            onCode(digits)
            if (digits.length == CODE_LENGTH && digits != code) onComplete()
        },
        label = { Text(stringResource(R.string.code_label)) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (code.length == CODE_LENGTH) onComplete() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.code_spam_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = wait == 0, onClick = {
            wait = RESEND_SECONDS
            onResend()
        }) {
            Text(if (wait > 0) stringResource(R.string.code_resend_in, wait) else stringResource(R.string.code_resend))
        }
        TextButton(onClick = onChangeEmail) { Text(stringResource(R.string.code_change_email)) }
    }
}

/** Tela cheia com voltar, conteúdo rolável e o botão principal embaixo. */
@Composable
private fun AccountScaffold(
    onBack: () -> Unit,
    title: String,
    body: String,
    buttonText: String,
    buttonEnabled: Boolean,
    onButton: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ob_back))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(64.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            content()
        }
        Button(onClick = onButton, enabled = buttonEnabled, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(60.dp)) {
            Text(buttonText, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Mostra o erro da conta em vermelho, se houver. */
@Composable
fun AccountError(@StringRes error: Int?) {
    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

/**
 * "Já tem uma conta?": e-mail e senha → código no e-mail → entra e devolve o que estava salvo na nuvem.
 */
@Composable
fun LoginScreen(onBack: () -> Unit, onLoggedIn: (Accounts.LoginResult) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun attempt(action: suspend () -> Unit) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                action()
            } catch (e: Accounts.AccountException) {
                error = accountErrorText(e.error)
            } finally {
                loading = false
            }
        }
    }

    fun sendCode() = attempt {
        Accounts.requestLoginCode(email, password)
        code = ""
        codeSent = true
    }

    fun submit() {
        if (!codeSent) {
            if (emailPasswordValid(email, password)) sendCode()
        } else if (code.length == CODE_LENGTH) {
            attempt { onLoggedIn(Accounts.verifyLogin(email, code)) }
        }
    }

    AccountScaffold(
        onBack = { if (codeSent) codeSent = false else onBack() },
        title = stringResource(if (codeSent) R.string.code_title else R.string.login_title),
        body = stringResource(if (codeSent) R.string.code_body_login else R.string.login_body),
        buttonText = stringResource(
            when {
                loading -> R.string.login_loading
                codeSent -> R.string.login_button
                else -> R.string.code_send
            },
        ),
        buttonEnabled = !loading && if (codeSent) code.length == CODE_LENGTH else emailPasswordValid(email, password),
        onButton = ::submit,
    ) {
        if (codeSent) {
            CodeEntry(email, code, { code = it }, onComplete = ::submit, onResend = { sendCode() }, onChangeEmail = { codeSent = false })
            AccountError(error)
        } else {
            EmailPasswordFields(email, { email = it }, password, { password = it }, onDone = ::submit)
            AccountError(error)
            TextButton(onClick = {
                if (!email.contains('@')) {
                    error = R.string.login_need_email
                    return@TextButton
                }
                scope.launch {
                    try {
                        Accounts.sendPasswordReset(email)
                        error = null
                        Toast.makeText(context, context.getString(R.string.login_reset_sent, email), Toast.LENGTH_LONG).show()
                    } catch (e: Accounts.AccountException) {
                        error = accountErrorText(e.error)
                    }
                }
            }) { Text(stringResource(R.string.login_forgot)) }
        }
    }
}

/** Criar conta depois das boas-vindas (pelo Perfil): @, e-mail e senha → código no e-mail → conta criada. */
@Composable
fun SignUpScreen(initialName: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var handle by rememberSaveable { mutableStateOf(Username.suggest(initialName)) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val status = rememberHandleStatus(if (codeSent) "" else handle)
    val formReady = status == HandleStatus.FREE && emailPasswordValid(email, password)

    fun attempt(action: suspend () -> Unit) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                action()
            } catch (e: Accounts.AccountException) {
                error = accountErrorText(e.error)
                if (e.error == Accounts.Error.USERNAME_TAKEN || e.error == Accounts.Error.EMAIL_IN_USE) codeSent = false
            } finally {
                loading = false
            }
        }
    }

    fun sendCode() = attempt {
        Accounts.requestSignUpCode(email, handle)
        code = ""
        codeSent = true
    }

    fun submit() {
        if (!codeSent) {
            if (formReady) sendCode()
        } else if (code.length == CODE_LENGTH) {
            attempt {
                Accounts.verifySignUp(email, code, password, handle, initialName.trim())
                AppGraph.profile.save(AppGraph.profile.state.value.copy(username = handle))
                Accounts.backup(context, force = true)
                onDone()
            }
        }
    }

    AccountScaffold(
        onBack = { if (codeSent) codeSent = false else onBack() },
        title = stringResource(if (codeSent) R.string.code_title else R.string.signup_title),
        body = stringResource(if (codeSent) R.string.code_body_signup else R.string.ob_account_hint),
        buttonText = stringResource(
            when {
                loading -> R.string.account_creating
                codeSent -> R.string.account_create
                else -> R.string.code_send
            },
        ),
        buttonEnabled = !loading && if (codeSent) code.length == CODE_LENGTH else formReady,
        onButton = ::submit,
    ) {
        if (codeSent) {
            CodeEntry(email, code, { code = it }, onComplete = ::submit, onResend = { sendCode() }, onChangeEmail = { codeSent = false })
        } else {
            UsernameField(handle, { handle = it }, status)
            EmailPasswordFields(email, { email = it }, password, { password = it }, onDone = ::submit)
        }
        AccountError(error)
    }
}

/** Depois de restaurar, apagar ou trazer dados da conta: ajusta serviços e lembretes e recria a tela. */
fun reloadAfterDataChange(context: Context) {
    BlockerService.syncWithSettings(context)
    DnsFilterService.syncWithSettings(context)
    AppGraph.profile.state.value.reminder?.let { Reminders.schedule(context, it) } ?: Reminders.cancel(context)
    (context as? Activity)?.recreate()
}
