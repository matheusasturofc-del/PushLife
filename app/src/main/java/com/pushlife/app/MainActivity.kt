package com.pushlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pushlife.app.ui.PushLifeApp
import com.pushlife.app.ui.theme.PushLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PushLifeTheme {
                PushLifeApp()
            }
        }
    }
}
