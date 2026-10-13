package com.pushlife.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.core.Profile
import com.pushlife.app.core.progress.Levels
import com.pushlife.app.data.Backup
import com.pushlife.app.data.ProgressCenter
import com.pushlife.app.filter.DnsFilterService
import com.pushlife.app.reminder.Reminders
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val PRIVACY_URL = "https://github.com/matheusasturofc-del/PushTrainer/blob/claude/great-pasteur-y7qbv5/PRIVACY.md"

/** Cores para o avatar (todas com bom contraste com texto escuro). */
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
                        .clickable { save { it.copy(avatarColor = (it.avatarColor + 1) % AVATAR_COLORS.size) } },
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
                        IconButton(onClick = { editName = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.profile_edit_name))
                        }
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
            SettingRow(stringResource(R.string.profile_language), stringResource(R.string.profile_language_hint)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    FilledTonalButton(onClick = {
                        context.startSafely(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }) { Text(stringResource(R.string.perm_open)) }
                }
            }
        }

        item { Section(R.string.profile_backup) }
        item {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { exportLauncher.launch("pushlife-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.backup_export))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.backup_import))
                    }
                }
                Text(stringResource(R.string.account_soon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item { Section(R.string.profile_about) }
        item {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
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
                    Backup.wipe(context)
                    afterDataChange(context)
                }) { Text(stringResource(R.string.profile_wipe_yes), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.disable_cancel_generic)) } },
        )
    }
}

/** Depois de restaurar ou apagar: ajusta serviços e lembretes e recria a tela com os dados novos. */
private fun afterDataChange(context: Context) {
    BlockerService.syncWithSettings(context)
    DnsFilterService.syncWithSettings(context)
    AppGraph.profile.state.value.reminder?.let { Reminders.schedule(context, it) } ?: Reminders.cancel(context)
    (context as? Activity)?.recreate()
}

private fun Context.toast(@StringRes text: Int) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

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
