package com.pushlife.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.core.Exercise
import com.pushlife.app.data.Accounts
import com.pushlife.app.data.AppLanguage
import com.pushlife.app.data.ProgressCenter
import com.pushlife.app.data.UpdateChecker
import com.pushlife.app.filter.DnsFilterService
import com.pushlife.app.reminder.DailyCheck
import com.pushlife.app.ui.ChooseUsernameScreen
import com.pushlife.app.ui.IntroAnimation
import com.pushlife.app.ui.PushLifeApp
import com.pushlife.app.ui.UpdateRequiredScreen
import com.pushlife.app.ui.accountErrorText
import com.pushlife.app.ui.onboarding.OnboardingScreen
import com.pushlife.app.ui.theme.PushLifeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Cada pedido para abrir a aba de treino (fim das boas-vindas ou tela de bloqueio) soma 1. */
    private var trainRequests by mutableIntStateOf(0)

    /** Exercício para abrir direto (fim das boas-vindas); null depois de usado. */
    private var pendingExercise by mutableStateOf<Exercise?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        DailyCheck.schedule(this)
        UpdateChecker.loadCached(this)
        setContent {
            PushLifeTheme {
              Box(Modifier.fillMaxSize()) {
                val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
                val update by UpdateChecker.required.collectAsStateWithLifecycle()
                var showIntro by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                if (showIntro) {
                    IntroAnimation(onDone = { showIntro = false })
                } else if (update != null) {
                    // Versão velha demais: nada do app abre até atualizar.
                    UpdateRequiredScreen(update!!)
                } else if (profile.onboardingDone) {
                    PushLifeApp(
                        trainRequests = trainRequests,
                        pendingExercise = pendingExercise,
                        onExerciseOpened = { pendingExercise = null },
                    )
                } else {
                    OnboardingScreen(
                        onFinished = { exercise ->
                            if (exercise != null) {
                                pendingExercise = exercise
                                trainRequests++
                            }
                        },
                    )
                }
                if (!showIntro) OAuthOverlay()
              }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Religa o bloqueio se o Android tiver fechado o serviço.
        BlockerService.syncWithSettings(this)
        DnsFilterService.syncWithSettings(this)
        ProgressCenter.adjustGoalIfDue()
        lifecycleScope.launch { UpdateChecker.refresh(this@MainActivity) }
    }

    override fun onStop() {
        super.onStop()
        // Com conta, o progresso vai para a nuvem quando o app sai da tela.
        Accounts.backup(this)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TRAIN, false) == true) trainRequests++
        val uri = intent?.data
        if (uri != null && uri.scheme == "pushlife" && uri.host == "auth-callback") {
            // O erro pode vir depois do "?" ou do "#", conforme o provedor.
            val fragment = uri.fragment.orEmpty().split('&').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }
                .associate { (k, v) -> k to Uri.decode(v) }
            val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
                ?: fragment["error_description"] ?: fragment["error"]
            lifecycleScope.launch { Accounts.completeOAuth(uri.getQueryParameter("code"), error) }
        }
    }

    companion object {
        const val EXTRA_OPEN_TRAIN = "open_train"
    }
}

/** Andamento do login com Google/Discord/Twitch por cima de qualquer tela. O resultado final é tratado por quem pediu. */
@Composable
private fun OAuthOverlay() {
    val oauth by Accounts.oauth.collectAsStateWithLifecycle()
    when (val state = oauth) {
        Accounts.OAuthState.Working -> Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.oauth_working), style = MaterialTheme.typography.titleMedium)
            }
        }
        is Accounts.OAuthState.NeedsUsername -> ChooseUsernameScreen(
            initialName = state.name,
            onCancel = {
                Accounts.signOut()
                Accounts.consumeOAuth()
            },
        )
        is Accounts.OAuthState.Failed -> AlertDialog(
            onDismissRequest = Accounts::consumeOAuth,
            title = { Text(stringResource(R.string.oauth_failed)) },
            text = { Text(stringResource(accountErrorText(state.error))) },
            confirmButton = { TextButton(onClick = Accounts::consumeOAuth) { Text(stringResource(android.R.string.ok)) } },
        )
        else -> {}
    }
}
