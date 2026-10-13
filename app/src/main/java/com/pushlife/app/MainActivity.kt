package com.pushlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.ui.PushLifeApp
import com.pushlife.app.ui.onboarding.OnboardingScreen
import com.pushlife.app.ui.theme.PushLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PushLifeTheme {
                val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
                var startInTraining by rememberSaveable { mutableStateOf(false) }
                if (profile.onboardingDone) {
                    PushLifeApp(startInTraining = startInTraining)
                } else {
                    OnboardingScreen(onFinished = { startInTraining = it })
                }
            }
        }
    }
}
