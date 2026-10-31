package com.pushlife.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import com.pushlife.app.security.AppLock
import com.pushlife.app.ui.LockScreen
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
import com.pushlife.app.data.Legal
import com.pushlife.app.data.ProgressCenter
import com.pushlife.app.data.UpdateChecker
import com.pushlife.app.filter.DnsFilterService
import com.pushlife.app.reminder.DailyCheck
import com.pushlife.app.ui.ChooseUsernameScreen
import com.pushlife.app.ui.IntroAnimation
import com.pushlife.app.ui.PreparingScreen
import com.pushlife.app.ui.PushLifeApp
import com.pushlife.app.ui.TermsScreen
import com.pushlife.app.ui.UpdateRequiredScreen
import com.pushlife.app.ui.accountErrorText
import com.pushlife.app.ui.onboarding.OnboardingScreen
import com.pushlife.app.ui.theme.PushLifeTheme
import kotlinx.coroutines.launch

// FragmentActivity (e não ComponentActivity) porque a tela da digital precisa dela
class MainActivity : FragmentActivity() {
    // trava com digital/senha: ligada no Perfil; trava ao abrir e ao voltar depois de 30 s fora
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L

    // cada pedido pra abrir o treino soma 1 (fim das boas-vindas ou tela de bloqueio)
    private var trainRequests by mutableIntStateOf(0)

    // exercício pra abrir direto no fim das boas-vindas; null depois de usar
    private var pendingExercise by mutableStateOf<Exercise?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locked = savedInstanceState?.getBoolean(STATE_LOCKED) ?: AppLock.isEnabled(this)
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
                var needsPrepare by rememberSaveable { mutableStateOf(Legal.needsPrepare(this@MainActivity)) }
                var needsTerms by rememberSaveable { mutableStateOf(!Legal.termsAccepted(this@MainActivity)) }
                if (locked) {
                    LockScreen(onUnlock = { unlock() })
                } else if (showIntro) {
                    IntroAnimation(onDone = { showIntro = false })
                } else if (needsPrepare) {
                    PreparingScreen(onDone = { needsPrepare = false })
                } else if (needsTerms) {
                    TermsScreen(onAccept = { needsTerms = false })
                } else if (update != null) {
                    // versão velha demais: trava tudo até atualizar
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
                if (!locked && !showIntro && !needsPrepare && !needsTerms) OAuthOverlay()
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
        // religa o bloqueio se o Android matou o serviço
        BlockerService.syncWithSettings(this)
        DnsFilterService.syncWithSettings(this)
        ProgressCenter.adjustGoalIfDue()
        lifecycleScope.launch { UpdateChecker.refresh(this@MainActivity) }
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.isEnabled(this) && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > AppLock.GRACE_MS) locked = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, locked)
    }

    private fun unlock() {
        AppLock.authenticate(this, getString(R.string.lock_title), getString(R.string.lock_subtitle)) { locked = false }
    }

    @Deprecated("tela de senha do Android 8")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!AppLock.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        // com conta, sobe o progresso pra nuvem ao sair do app
        Accounts.backup(this)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TRAIN, false) == true) trainRequests++
        val uri = intent?.data
        if (uri != null && uri.scheme == "pushlife" && uri.host == "auth-callback") {
            // o erro pode vir depois do "?" ou do "#", depende do provedor
            val fragment = uri.fragment.orEmpty().split('&').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }
                .associate { (k, v) -> k to Uri.decode(v) }
            val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
                ?: fragment["error_description"] ?: fragment["error"]
            lifecycleScope.launch { Accounts.completeOAuth(uri.getQueryParameter("code"), error) }
        }
    }

    companion object {
        const val EXTRA_OPEN_TRAIN = "open_train"
        private const val STATE_LOCKED = "locked"
    }
}

// login Google/Discord/Twitch rolando, por cima de qualquer tela
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
