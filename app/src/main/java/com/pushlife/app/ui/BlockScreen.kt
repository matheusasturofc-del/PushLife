package com.pushlife.app.ui

import android.Manifest
import android.app.Activity
import android.net.VpnService
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.blocking.AppEntry
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.blocking.InstalledApps
import com.pushlife.app.blocking.Permissions
import com.pushlife.app.core.Rules
import com.pushlife.app.filter.DnsFilterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val DISABLE_WAIT_SECONDS = 30

private data class PermissionState(
    val usage: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val battery: Boolean = false,
) {
    val required get() = usage && overlay && notifications

    companion object {
        fun read(context: Context) = PermissionState(
            usage = Permissions.hasUsageAccess(context),
            overlay = Permissions.canDrawOverlays(context),
            notifications = Permissions.hasNotifications(context),
            battery = Permissions.ignoresBatteryOptimizations(context),
        )
    }
}

@Composable
fun BlockScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by AppGraph.blocking.state.collectAsStateWithLifecycle()
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    var permissions by remember { mutableStateOf(PermissionState.read(context)) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var askDisable by remember { mutableStateOf(false) }
    var askDisableFilter by remember { mutableStateOf(false) }
    val filterRunning by DnsFilterService.running.collectAsStateWithLifecycle()
    // O Android pede autorização uma única vez para o app criar a VPN local do filtro.
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppGraph.blocking.setAdultFilter(true)
            DnsFilterService.syncWithSettings(context)
        }
    }

    // Ao voltar das configurações do Android, relê as permissões e recarrega as médias de uso.
    LifecycleResumeEffect(Unit) {
        val before = permissions.usage
        permissions = PermissionState.read(context)
        if (permissions.usage != before) apps = null
        BlockerService.syncWithSettings(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(apps == null) {
        if (apps == null) apps = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = PermissionState.read(context)
    }

    val canEnable = permissions.required && settings.blockedPackages.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.block_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.block_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            ProtectionCard(
                enabled = settings.enabled,
                canEnable = canEnable,
                onToggle = { on ->
                    if (on) {
                        AppGraph.blocking.setEnabled(true)
                        BlockerService.syncWithSettings(context)
                    } else {
                        askDisable = true
                    }
                },
            )
        }

        item {
            AdultFilterCard(
                enabled = settings.adultFilter,
                running = filterRunning,
                onToggle = { on ->
                    if (on) {
                        val consent = VpnService.prepare(context)
                        if (consent == null) {
                            AppGraph.blocking.setAdultFilter(true)
                            DnsFilterService.syncWithSettings(context)
                        } else {
                            vpnConsent.launch(consent)
                        }
                    } else {
                        askDisableFilter = true
                    }
                },
            )
        }

        if (bank.debtSeconds > 0 || bank.emergenciesLeft(LocalDate.now()) < Rules.MAX_EMERGENCIES_PER_DAY) {
            item {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (bank.debtSeconds > 0) {
                        Text(stringResource(R.string.debt_label, bank.debtPushups), color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        stringResource(R.string.emergencies_left, bank.emergenciesLeft(LocalDate.now()), Rules.MAX_EMERGENCIES_PER_DAY),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.perm_section)) }
        item {
            PermissionRow(R.string.perm_usage_title, R.string.perm_usage_body, permissions.usage, R.string.perm_allow) {
                context.startSafely(Permissions.usageAccessIntent())
            }
        }
        item {
            PermissionRow(R.string.perm_overlay_title, R.string.perm_overlay_body, permissions.overlay, R.string.perm_allow) {
                context.startSafely(Permissions.overlayIntent(context))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                PermissionRow(R.string.perm_notif_title, R.string.perm_notif_body, permissions.notifications, R.string.perm_allow) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        item {
            PermissionRow(R.string.perm_battery_title, R.string.perm_battery_body, permissions.battery, R.string.perm_allow) {
                Permissions.requestBatteryExemption(context)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.apps_section), Modifier.weight(1f))
                Text(
                    stringResource(R.string.apps_count, settings.blockedPackages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        val list = apps
        if (list == null) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.apps_loading), Modifier.padding(start = 12.dp))
                }
            }
        } else {
            items(list, key = { it.packageName }) { app ->
                AppRow(app, blocked = app.packageName in settings.blockedPackages) {
                    AppGraph.blocking.toggle(app.packageName)
                    BlockerService.syncWithSettings(context)
                }
            }
        }
    }

    if (askDisable) {
        DisableDialog(
            title = R.string.disable_title,
            onConfirm = {
                askDisable = false
                AppGraph.blocking.setEnabled(false)
                BlockerService.syncWithSettings(context)
            },
            onDismiss = { askDisable = false },
        )
    }
    if (askDisableFilter) {
        DisableDialog(
            title = R.string.filter_disable_title,
            onConfirm = {
                askDisableFilter = false
                AppGraph.blocking.setAdultFilter(false)
                DnsFilterService.syncWithSettings(context)
            },
            onDismiss = { askDisableFilter = false },
        )
    }
}

@Composable
private fun AdultFilterCard(enabled: Boolean, running: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (enabled) colors.primaryContainer else colors.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = colors.primary)
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        Text(
            stringResource(
                when {
                    enabled && running -> R.string.filter_on
                    enabled -> R.string.filter_paused
                    else -> R.string.filter_off
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        if (enabled) {
            Text(stringResource(R.string.filter_tips), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent) }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ProtectionCard(enabled: Boolean, canEnable: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (enabled) colors.primaryContainer else colors.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.protection_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(
                    when {
                        enabled -> R.string.protection_on
                        canEnable -> R.string.protection_off
                        else -> R.string.protection_needs
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle, enabled = enabled || canEnable)
    }
}

@Composable
private fun PermissionRow(@StringRes title: Int, @StringRes body: Int, granted: Boolean, @StringRes action: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.perm_ok), tint = MaterialTheme.colorScheme.primary)
        } else {
            FilledTonalButton(onClick = onClick) { Text(stringResource(action)) }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, blocked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(44.dp))
        } else {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            Text(
                if (app.dailyAverageMs >= 60_000) {
                    stringResource(R.string.app_average, formatDuration(app.dailyAverageMs / 1000))
                } else {
                    stringResource(R.string.app_little_use)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = blocked, onCheckedChange = { onToggle() })
    }
}

/** Desligar a proteção exige esperar 30 s: um freio para o impulso de burlar. */
@Composable
private fun DisableDialog(@StringRes title: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(DISABLE_WAIT_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(R.string.disable_body, secondsLeft)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = secondsLeft == 0) {
                Text(stringResource(R.string.disable_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.disable_cancel)) }
        },
    )
}
