package com.pushlife.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.data.ProgressCenter
import com.pushlife.app.reminder.DailyCheck
import com.pushlife.app.ui.PushLifeApp
import com.pushlife.app.ui.onboarding.OnboardingScreen
import com.pushlife.app.ui.theme.PushLifeTheme

class MainActivity : ComponentActivity() {
    /** Cada pedido para abrir a aba de treino (fim das boas-vindas ou tela de bloqueio) soma 1. */
    private var trainRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        DailyCheck.schedule(this)
        setContent {
            PushLifeTheme {
                val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
                if (profile.onboardingDone) {
                    PushLifeApp(trainRequests = trainRequests)
                } else {
                    OnboardingScreen(onFinished = { startWorkout -> if (startWorkout) trainRequests++ })
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
        ProgressCenter.adjustGoalIfDue()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TRAIN, false) == true) trainRequests++
    }

    companion object {
        const val EXTRA_OPEN_TRAIN = "open_train"
    }
}
