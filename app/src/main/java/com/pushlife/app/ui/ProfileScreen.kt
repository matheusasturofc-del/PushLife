package com.pushlife.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.pm.PackageInfoCompat
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.core.Profile
import com.pushlife.app.core.progress.Levels
import com.pushlife.app.data.Accounts
import com.pushlife.app.data.AppLanguage
import com.pushlife.app.data.Backup
import com.pushlife.app.data.ProgressCenter
import com.pushlife.app.reminder.Reminders
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val PRIVACY_URL = "https://github.com/matheusasturofc-del/PushLife/blob/claude/great-pasteur-y7qbv5/PRIVACY.md"

// cores do avatar (todas legíveis com texto escuro)
private val AVATAR_COLORS = listOf(Orange, Yellow, Color(0xFF4CD964), Color(0xFF5AC8FA), Color(0xFFFF6B8B), Color(0xFFB98BFF))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    val progress by AppGraph.progress.state.collectAsStateWithLifecycle()
    val stats = remember(bank, progress) { ProgressCenter.stats() }
    var editName by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var showSignUp by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Accounts.LoginResult?>(null) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val account by Accounts.current.collectAsStateWithLifecycle()

    fun save(change: (Profile) -> Profile) = AppGraph.profile.save(change(AppGraph.profile.state.value))

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(context).toByteArray()) } != null
        }.getOrDefault(false)
        context.toast(if (ok) R.string.backup_saved else R.string.backup_error)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val color = AVATAR_COLORS[profile.avatarColor.mod(AVATAR_COLORS.size)]
                Box(
                    Modifier
                        .size(80.dp)
                        .background(color, CircleShape)
                        // convidado não troca a cor do avatar
                        .clickable(enabled = !profile.guest) { save { it.copy(avatarColor = (it.avatarColor + 1) % AVATAR_COLORS.size) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.name.trim().take(1).uppercase().ifEmpty { "?" },
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name.ifBlank { "—" }, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f, fill = false))
                        if (!profile.guest) {
                            IconButton(onClick = { editName = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.profile_edit_name))
                            }
                        }
                    }
                    if (profile.username.isNotEmpty()) {
                        Text("@${profile.username}", style = MaterialTheme.typography.titleMedium, color = Orange)
                    } else if (profile.guest) {
                        Text(stringResource(R.string.guest_badge), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        stringResource(R.string.progress_level, stats.level) + " · " + stringResource(Levels.rank(stats.level).title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.profile_avatar_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Section(R.string.profile_goals) }
        item {
            SettingRow(stringResource(R.string.profile_daily_goal), stringResource(R.string.profile_daily_goal_value, profile.dailyGoal)) {
                FilledTonalIconButton(onClick = { save { it.copy(dailyGoal = (it.dailyGoal - 5).coerceAtLeast(10)) } }) {
                    Icon(Icons.Rounded.Remove, contentDescription = "−5")
                }
                FilledTonalIconButton(onClick = { save { it.copy(dailyGoal = (it.dailyGoal + 5).coerceAtMost(300)) } }) {
                    Icon(Icons.Rounded.Add, contentDescription = "+5")
                }
            }
        }
        item {
            val time = profile.reminder
            SettingRow(
                stringResource(R.string.profile_reminder),
                time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) ?: stringResource(R.string.profile_reminder_off),
            ) {
                if (time != null) TextButton(onClick = { pickTime = true }) { Text(stringResource(R.string.profile_change)) }
                Switch(
                    checked = time != null,
                    onCheckedChange = { on ->
                        if (on) {
                            pickTime = true
                        } else {
                            Reminders.cancel(context)
                            save { it.copy(reminder = null) }
                        }
                    },
                )
            }
        }

        item { Section(R.string.profile_notifications) }
        item { ToggleRow(R.string.profile_notify_streak, profile.notifyStreak) { v -> save { it.copy(notifyStreak = v) } } }
        item { ToggleRow(R.string.profile_notify_weekly, profile.notifyWeekly) { v -> save { it.copy(notifyWeekly = v) } } }
        item { ToggleRow(R.string.profile_notify_low, profile.notifyLowTime) { v -> save { it.copy(notifyLowTime = v) } } }

        item { Section(R.string.profile_language) }
        item {
            // segue o celular, ou fixa um idioma e o app reabre traduzido
            val current = remember { AppLanguage.get(context) }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.profile_language_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                AppLanguage.TAGS.forEach { tag ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = tag != current) { (context as? Activity)?.let { AppLanguage.set(it, tag) } }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tag == current, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(languageName(tag), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        item { Section(R.string.profile_backup) }
        item {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (profile.guest) {
                    // convidado não tem backup (nem em arquivo), precisa criar conta
                    Text(stringResource(R.string.guest_no_backup), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { exportLauncher.launch("pushlife-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_export))
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_import))
                        }
                    }
                }
            }
        }

        item { Section(R.string.account_title) }
        item {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val current = account
                when {
                    !Accounts.available -> Text(stringResource(R.string.account_soon), style = MaterialTheme.typography.bodyMedium)
                    current != null -> {
                        Text(
                            stringResource(R.string.account_signed_in, profile.username.ifEmpty { current.email }),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(current.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.account_cloud_backup), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            Accounts.backup(context, force = true)
                            Accounts.signOut()
                            save { it.copy(username = "") }
                        }) { Text(stringResource(R.string.account_sign_out)) }
                        TextButton(onClick = { confirmDeleteAccount = true }) {
                            Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.account_none), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { showSignUp = true }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.account_create))
                            }
                            OutlinedButton(onClick = { showLogin = true }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.login_button))
                            }
                        }
                    }
                }
            }
        }

        item { Section(R.string.profile_about) }
        item {
            val version = remember {
                // nome e versão (o número é o mesmo do update.json)
                runCatching {
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
                }.getOrNull().orEmpty()
            }
            SettingRow(stringResource(R.string.app_name), stringResource(R.string.profile_version, version)) {
                TextButton(onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }) {
                    Text(stringResource(R.string.profile_privacy))
                }
            }
        }
        item {
            TextButton(onClick = { confirmWipe = true }) {
                Text(stringResource(R.string.profile_wipe), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (editName) {
        var text by remember { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text(stringResource(R.string.profile_edit_name)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(30) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    save { it.copy(name = text.trim()) }
                    editName = false
                }) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text(stringResource(R.string.disable_cancel_generic)) } },
        )
    }

    if (pickTime) {
        val initial = profile.reminder ?: LocalTime.of(18, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(state.hour, state.minute)
                    Reminders.schedule(context, time)
                    save { it.copy(reminder = time) }
                    pickTime = false
                }) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.disable_cancel_generic)) } },
        )
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_restore_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }.getOrNull()
                    if (text != null && Backup.import(context, text)) {
                        context.toast(R.string.backup_restored)
                        afterDataChange(context)
                    } else {
                        context.toast(R.string.backup_invalid)
                    }
                }) { Text(stringResource(R.string.backup_restore_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.disable_cancel_generic)) } },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.profile_wipe)) },
            text = { Text(stringResource(R.string.profile_wipe_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    Reminders.cancel(context)
                    Accounts.signOut() // senão o app vazio sobrescreveria o backup da conta
                    Backup.wipe(context)
                    afterDataChange(context)
                }) { Text(stringResource(R.string.profile_wipe_yes), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.disable_cancel_generic)) } },
        )
    }

    if (showSignUp) {
        FullScreenDialog(onDismiss = { showSignUp = false }) {
            SignUpScreen(if (profile.guest) "" else profile.name, onBack = { showSignUp = false }, onDone = {
                showSignUp = false
                save { it.copy(guest = false) }
                context.toast(R.string.account_created)
            })
        }
    }

    if (showLogin) {
        FullScreenDialog(onDismiss = { showLogin = false }) {
            LoginScreen(onBack = { showLogin = false }, onLoggedIn = { result ->
                showLogin = false
                save { it.copy(username = result.username, guest = false) }
                if (result.backup != null) pendingRestore = result else Accounts.backup(context, force = true)
            })
        }
    }

    // volta do login Google/Discord/Twitch pelo Perfil
    val oauth by Accounts.oauth.collectAsStateWithLifecycle()
    LaunchedEffect(oauth) {
        val done = oauth as? Accounts.OAuthState.Done ?: return@LaunchedEffect
        Accounts.consumeOAuth()
        showLogin = false
        showSignUp = false
        val result = done.result
        save { it.copy(username = result.username, guest = false, name = if (it.guest && result.name.isNotBlank()) result.name else it.name) }
        if (result.backup != null) pendingRestore = result else Accounts.backup(context, force = true)
    }

    if (confirmDeleteAccount) {
        // segurança: só apaga com o código do e-mail
        var codeSent by remember { mutableStateOf(false) }
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<Int?>(null) }
        fun attempt(action: suspend () -> Unit) {
            if (deletingAccount) return
            scope.launch {
                deletingAccount = true
                error = null
                try {
                    action()
                } catch (e: Accounts.AccountException) {
                    error = accountErrorText(e.error)
                } finally {
                    deletingAccount = false
                }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!deletingAccount) confirmDeleteAccount = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.account_delete_body))
                    if (codeSent) {
                        Text(stringResource(R.string.code_sent_to, account?.email.orEmpty()), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter { c -> c.isDigit() }.take(CODE_LENGTH) },
                            label = { Text(stringResource(R.string.code_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                    }
                    AccountError(error)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingAccount && (!codeSent || code.length == CODE_LENGTH),
                    onClick = {
                        if (!codeSent) {
                            attempt {
                                Accounts.requestDeleteCode()
                                codeSent = true
                            }
                        } else {
                            attempt {
                                Accounts.deleteAccount(code)
                                confirmDeleteAccount = false
                                Reminders.cancel(context)
                                Backup.wipe(context)
                                context.toast(R.string.account_deleted)
                                afterDataChange(context)
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            when {
                                deletingAccount -> R.string.account_deleting
                                codeSent -> R.string.account_delete_yes
                                else -> R.string.code_send
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !deletingAccount, onClick = { confirmDeleteAccount = false }) {
                    Text(stringResource(R.string.disable_cancel_generic))
                }
            },
        )
    }

    pendingRestore?.let { result ->
        // já tem dados aqui: a pessoa escolhe quais ficam
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.account_restore_title)) },
            text = { Text(stringResource(R.string.account_restore_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    if (Backup.import(context, result.backup.orEmpty())) {
                        save { it.copy(username = result.username) }
                        afterDataChange(context)
                    } else {
                        context.toast(R.string.backup_invalid)
                    }
                }) { Text(stringResource(R.string.account_restore_yes)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    Accounts.backup(context, force = true)
                }) { Text(stringResource(R.string.account_restore_no)) }
            },
        )
    }
}

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        content()
    }
}

// depois de restaurar/apagar: ajusta serviços e lembretes e recria a tela
private fun afterDataChange(context: Context) = reloadAfterDataChange(context)

private fun Context.toast(@StringRes text: Int) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

// nome de cada idioma na própria língua
@Composable
private fun languageName(tag: String): String = when (tag) {
    "pt" -> "Português"
    "en" -> "English"
    "es" -> "Español"
    else -> stringResource(R.string.language_auto)
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent) }
}

@Composable
private fun Section(@StringRes title: Int) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SettingRow(title: String, value: String, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
private fun ToggleRow(@StringRes title: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
