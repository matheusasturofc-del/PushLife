package com.pushlife.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.fragment.app.FragmentActivity
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
import com.pushlife.app.reminder.PackReminder
import com.pushlife.app.security.AppLock
import com.pushlife.app.ui.ChooseUsernameScreen
import com.pushlife.app.ui.CrashReportDialog
import com.pushlife.app.ui.IntroAnimation
import com.pushlife.app.ui.LockScreen
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

    // muda a cada login/logout/restauração: a tela do app começa de novo, na casinha
    private var session by mutableIntStateOf(0)

    private enum class Screen { LOCK, INTRO, PREPARE, TERMS, UPDATE, APP, WELCOME }

    // chamado depois de entrar/sair da conta ou trocar os dados (no lugar de recriar a activity,
    // que piscava a tela e voltava na aba onde a pessoa estava)
    fun newSession() {
        // sem pedido de treino pendente, senão a sessão nova abriria na aba Treinar
        trainRequests = 0
        pendingExercise = null
        session++
    }

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
        PackReminder.schedule(this)
        UpdateChecker.loadCached(this)
        setContent {
            PushLifeTheme {
              Box(Modifier.fillMaxSize()) {
                val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
                val update by UpdateChecker.required.collectAsStateWithLifecycle()
                var showIntro by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                var needsPrepare by rememberSaveable { mutableStateOf(Legal.needsPrepare(this@MainActivity)) }
                var needsTerms by rememberSaveable { mutableStateOf(!Legal.termsAccepted(this@MainActivity)) }
                val screen = when {
                    locked -> Screen.LOCK
                    showIntro -> Screen.INTRO
                    needsPrepare -> Screen.PREPARE
                    needsTerms -> Screen.TERMS
                    update != null -> Screen.UPDATE
                    profile.onboardingDone -> Screen.APP
                    else -> Screen.WELCOME
                }
                // cada login/logout é uma "sessão" nova: o app remonta do zero (volta pra casinha)
                // e entra com animação; trocar de tela também anima
                AnimatedContent(
                    targetState = screen to session,
                    transitionSpec = {
                        val (from, oldSession) = initialState
                        val (to, newSession) = targetState
                        if (newSession != oldSession || (from == Screen.APP) != (to == Screen.APP)) {
                            // entrou ou saiu da conta: a tela velha encolhe e some, a nova cresce
                            (fadeIn(tween(420, delayMillis = 120)) + scaleIn(tween(420, delayMillis = 120), initialScale = 0.9f)) togetherWith
                                (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.06f))
                        } else {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                        }
                    },
                    label = "screen",
                ) { (current, _) ->
                    when (current) {
                        Screen.LOCK -> LockScreen(onUnlock = { unlock() })
                        Screen.INTRO -> IntroAnimation(onDone = { showIntro = false })
                        Screen.PREPARE -> PreparingScreen(onDone = { needsPrepare = false })
                        Screen.TERMS -> TermsScreen(onAccept = { needsTerms = false })
                        // versão velha demais: trava tudo até atualizar
                        Screen.UPDATE -> update?.let { UpdateRequiredScreen(it) }
                        Screen.APP -> PushLifeApp(
                            trainRequests = trainRequests,
                            pendingExercise = pendingExercise,
                            onExerciseOpened = { pendingExercise = null },
                        )
                        Screen.WELCOME -> OnboardingScreen(
                            onFinished = { exercise ->
                                if (exercise != null) {
                                    pendingExercise = exercise
                                    trainRequests++
                                }
                            },
                        )
                    }
                }
                if (!locked && !showIntro && !needsPrepare && !needsTerms) OAuthOverlay()
                if (!locked && !showIntro) CrashReportDialog()
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
