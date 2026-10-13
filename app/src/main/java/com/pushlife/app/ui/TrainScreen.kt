package com.pushlife.app.ui

import android.Manifest
import androidx.annotation.StringRes
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.camera.FaceCamera
import com.pushlife.app.core.FacePushupCounter
import java.time.LocalDate

private enum class Stage { Intro, Active, Summary }

private data class SessionResult(val reps: Int, val earnedSeconds: Long, val hitCap: Boolean)

@Composable
fun TrainScreen(onBackHome: () -> Unit, modifier: Modifier = Modifier) {
    var stage by rememberSaveable { mutableStateOf(Stage.Intro) }
    var result by remember { mutableStateOf(SessionResult(0, 0, false)) }

    when (stage) {
        Stage.Intro -> IntroStage(onStart = { stage = Stage.Active }, modifier = modifier)
        Stage.Active -> ActiveStage(
            onFinish = {
                result = it
                stage = Stage.Summary
            },
            modifier = modifier,
        )
        Stage.Summary -> SummaryStage(
            result = result,
            onAgain = { stage = Stage.Active },
            onBackHome = {
                stage = Stage.Intro
                onBackHome()
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun IntroStage(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        denied = !granted
        if (granted) onStart()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.train_title), style = MaterialTheme.typography.displayMedium)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.train_how_title), style = MaterialTheme.typography.headlineSmall)
                Step(1, R.string.train_step_1)
                Step(2, R.string.train_step_2)
                Step(3, R.string.train_step_3)
                Text(
                    stringResource(R.string.train_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!hasCamera) {
            Text(stringResource(R.string.camera_permission_needed), style = MaterialTheme.typography.bodyLarge)
        }
        if (denied) {
            Text(
                stringResource(R.string.camera_permission_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.open_settings)) }
        }

        Button(
            onClick = { if (hasCamera) onStart() else askCamera.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text(
                stringResource(if (hasCamera) R.string.train_start else R.string.camera_permission_button),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun Step(number: Int, @StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActiveStage(onFinish: (SessionResult) -> Unit, modifier: Modifier = Modifier) {
    val counter = remember { FacePushupCounter() }
    var reps by remember { mutableIntStateOf(0) }
    var depth by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf(FacePushupCounter.Phase.WAITING) }
    var faceVisible by remember { mutableStateOf(false) }
    var earned by remember { mutableLongStateOf(0L) }
    var hitCap by remember { mutableStateOf(false) }
    val phrases = stringArrayResource(R.array.motivation)
    var phrase by remember { mutableStateOf("") }

    // Tela sempre acesa e bip a cada repetição.
    val view = LocalView.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            tone?.release()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FaceCamera(
            onFaceSize = { size ->
                faceVisible = size != null
                if (counter.update(size, SystemClock.elapsedRealtime())) {
                    reps = counter.count
                    val credit = AppGraph.bank.addReps(1, LocalDate.now())
                    earned += credit.creditedSeconds
                    hitCap = hitCap || credit.hitCap
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    if (reps % 5 == 0) phrase = phrases[(reps / 5 - 1) % phrases.size]
                }
                depth = counter.depth
                phase = counter.phase
            },
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(3.dp, if (faceVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
        )

        val status = when {
            phase == FacePushupCounter.Phase.WAITING -> R.string.status_waiting
            !faceVisible && phase != FacePushupCounter.Phase.DOWN -> R.string.status_no_face
            phase == FacePushupCounter.Phase.UP -> R.string.status_go_down
            else -> R.string.status_go_up
        }
        Text(stringResource(status), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                "$reps",
                fontSize = 160.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LinearProgressIndicator(
            progress = { depth },
            modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
        )
        Text(phrase, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text(
            stringResource(R.string.session_earned, formatDuration(earned)),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = { onFinish(SessionResult(reps, earned, hitCap)) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text(stringResource(R.string.finish), style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun SummaryStage(result: SessionResult, onAgain: () -> Unit, onBackHome: () -> Unit, modifier: Modifier = Modifier) {
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (result.reps == 0) {
            Text(stringResource(R.string.summary_zero), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.summary_reps, result.reps), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.session_earned, formatDuration(result.earnedSeconds)),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            stringResource(R.string.summary_balance, formatDuration(bank.available(LocalDate.now()))),
            style = MaterialTheme.typography.titleMedium,
        )
        if (result.hitCap) {
            Text(
                stringResource(R.string.summary_cap),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.train_again)) }
        OutlinedButton(onClick = onBackHome, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.back_home)) }
    }
}
