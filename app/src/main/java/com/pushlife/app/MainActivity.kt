package com.pushlife.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.core.Exercise
import com.pushlife.app.data.ProgressCenter
import com.pushlife.app.data.UpdateChecker
import com.pushlife.app.filter.DnsFilterService
import com.pushlife.app.reminder.DailyCheck
import com.pushlife.app.ui.IntroAnimation
import com.pushlife.app.ui.PushLifeApp
import com.pushlife.app.ui.UpdateRequiredScreen
import com.pushlife.app.ui.onboarding.OnboardingScreen
import com.pushlife.app.ui.theme.PushLifeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Cada pedido para abrir a aba de treino (fim das boas-vindas ou tela de bloqueio) soma 1. */
    private var trainRequests by mutableIntStateOf(0)

    /** Exercício para abrir direto (fim das boas-vindas); null depois de usado. */
    private var pendingExercise by mutableStateOf<Exercise?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        DailyCheck.schedule(this)
        UpdateChecker.loadCached(this)
        setContent {
            PushLifeTheme {
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

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TRAIN, false) == true) trainRequests++
    }

    companion object {
        const val EXTRA_OPEN_TRAIN = "open_train"
    }
}
