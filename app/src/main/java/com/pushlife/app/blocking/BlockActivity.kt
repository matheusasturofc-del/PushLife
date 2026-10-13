package com.pushlife.app.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.AppGraph
import com.pushlife.app.MainActivity
import com.pushlife.app.R
import com.pushlife.app.core.Rules
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.PushLifeTheme
import com.pushlife.app.ui.theme.Yellow
import java.time.LocalDate

/** Tela que cobre um app bloqueado quando o saldo acaba. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val appLabel = InstalledApps.label(this, packageName)
        // Voltar = ir para a tela inicial, nunca de volta ao app bloqueado.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            PushLifeTheme {
                BlockContent(
                    appLabel = appLabel,
                    onTrain = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_TRAIN, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                        finish()
                    },
                    onEmergency = {
                        if (AppGraph.bank.emergency()) {
                            packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
                            finish()
                        }
                    },
                    onExit = ::goHome,
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun open(context: Context, packageName: String) {
            context.startActivity(
                Intent(context, BlockActivity::class.java)
                    .putExtra(EXTRA_PACKAGE, packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }
}

@Composable
private fun BlockContent(appLabel: String, onTrain: () -> Unit, onEmergency: () -> Unit, onExit: () -> Unit) {
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    val emergenciesLeft = bank.emergenciesLeft(LocalDate.now())
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(112.dp).background(Brush.linearGradient(listOf(Orange, Yellow)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Ink, modifier = Modifier.size(56.dp))
        }
        Text(stringResource(R.string.blocked_title).uppercase(), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.blocked_body, appLabel),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (bank.debtReps > 0) {
            Text(
                stringResource(R.string.debt_label, bank.debtReps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onTrain, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.blocked_train), style = MaterialTheme.typography.titleLarge)
        }
        if (emergenciesLeft > 0) {
            OutlinedButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.blocked_emergency), textAlign = TextAlign.Center)
            }
            Text(
                stringResource(R.string.emergencies_left, emergenciesLeft, Rules.MAX_EMERGENCIES_PER_DAY),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.blocked_no_emergency), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onExit) { Text(stringResource(R.string.blocked_exit)) }
    }
}
