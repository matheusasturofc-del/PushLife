package com.pushlife.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.pushlife.app.camera.RigOverlay
import com.pushlife.app.camera.Rig
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import com.pushlife.app.camera.PoseCamera
import com.pushlife.app.camera.PoseModels
import com.pushlife.app.camera.StepCounterEffect
import com.pushlife.app.camera.StepSensor
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.ExerciseUnit
import com.pushlife.app.core.Tracking
import com.pushlife.app.core.pose.Cue
import com.pushlife.app.core.pose.PoseTracker
import com.pushlife.app.core.progress.Achievement
import com.pushlife.app.core.progress.Levels
import com.pushlife.app.data.ProgressCenter
import java.time.LocalDate

private enum class Stage { Pick, Intro, Active, Summary }

private data class SessionResult(val exercise: Exercise, val amount: Int, val earnedSeconds: Long, val hitCap: Boolean)

@Composable
fun TrainScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    pendingExercise: Exercise? = null,
    onExerciseOpened: () -> Unit = {},
) {
    var stage by rememberSaveable { mutableStateOf(Stage.Pick) }
    var exercise by rememberSaveable { mutableStateOf(Exercise.PUSHUP) }
    var result by remember { mutableStateOf(SessionResult(Exercise.PUSHUP, 0, 0, false)) }

    // Pedido para abrir um exercício direto (ex.: "testar agora" nas boas-vindas).
    LaunchedEffect(pendingExercise) {
        pendingExercise?.let {
            exercise = it
            stage = Stage.Intro
            onExerciseOpened()
        }
    }

    BackHandler(enabled = stage == Stage.Intro) { stage = Stage.Pick }

    when (stage) {
        Stage.Pick -> PickStage(
            onPick = {
                exercise = it
                stage = Stage.Intro
            },
            modifier = modifier,
        )
        Stage.Intro -> IntroStage(exercise, onBack = { stage = Stage.Pick }, onStart = { stage = Stage.Active }, modifier = modifier)
        Stage.Active -> ActiveStage(
            exercise,
            onFinish = {
                result = it
                stage = Stage.Summary
            },
            modifier = modifier,
        )
        Stage.Summary -> SummaryStage(
            result = result,
            onAgain = { stage = Stage.Active },
            onOther = { stage = Stage.Pick },
            onBackHome = {
                stage = Stage.Pick
                onBackHome()
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun PickStage(onPick: (Exercise) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Text(stringResource(R.string.tab_train), style = MaterialTheme.typography.displayMedium)
        }
        items(Exercise.entries) { ex ->
            val ui = ex.ui
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onPick(ex) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ui.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Text(stringResource(ui.name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(ui.value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IntroStage(exercise: Exercise, onBack: () -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ui = exercise.ui
    val permission = when {
        exercise.tracking != Tracking.STEPS -> Manifest.permission.CAMERA
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.ACTIVITY_RECOGNITION
        else -> null
    }
    fun granted() = permission == null ||
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember(exercise) { mutableStateOf(granted()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPermission = ok
        denied = !ok
        if (ok) onStart()
    }
    val sensorMissing = exercise.tracking == Tracking.STEPS && !StepSensor.isAvailable(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ob_back))
        }
        Text(stringResource(ui.name), style = MaterialTheme.typography.displayMedium)
        Text(stringResource(ui.value), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        // O personagem mostrando o movimento.
        ExerciseAnimation(
            exercise,
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(8.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.train_how_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(ui.setup), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                ui.steps.forEachIndexed { i, step -> Step(i + 1, step) }
                if (exercise.tracking != Tracking.STEPS) {
                    Text(
                        stringResource(R.string.train_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (sensorMissing) {
            Text(stringResource(R.string.walk_no_sensor), color = MaterialTheme.colorScheme.error)
        }
        if (denied) {
            Text(
                stringResource(if (exercise.tracking == Tracking.STEPS) R.string.activity_permission_denied else R.string.camera_permission_denied),
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
            onClick = { if (hasPermission) onStart() else permission?.let { ask.launch(it) } },
            enabled = !sensorMissing,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text(
                stringResource(
                    when {
                        hasPermission -> R.string.train_start
                        exercise.tracking == Tracking.STEPS -> R.string.activity_permission_button
                        else -> R.string.camera_permission_button
                    },
                ),
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

/** Estado de uma sessão de treino, atualizado pela câmera ou pelo sensor de passos. */
private class Session(val exercise: Exercise) {
    var amount by mutableIntStateOf(0)
    var progress by mutableFloatStateOf(0f)
    var cue by mutableStateOf(Cue.GET_READY)
    var tracking by mutableStateOf(false)
    var earned by mutableLongStateOf(0L)
    var hitCap by mutableStateOf(false)

    /** Credita [units] unidades no saldo (a dívida de emergência é paga primeiro). */
    fun credit(units: Int) {
        if (units <= 0) return
        val credit = AppGraph.bank.addExercise(exercise, units, LocalDate.now())
        earned += credit.creditedSeconds
        hitCap = hitCap || credit.hitCap
        ProgressCenter.afterExercise()
    }
}

@Composable
private fun ActiveStage(exercise: Exercise, onFinish: (SessionResult) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session = remember(exercise) { Session(exercise) }
    val phrases = stringArrayResource(R.array.motivation)
    var phrase by remember { mutableStateOf("") }

    // Tela sempre acesa e bip a cada repetição.
    val view = LocalView.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) {
        view.keepScreenOn = exercise.tracking != Tracking.STEPS
        onDispose {
            view.keepScreenOn = false
            tone?.release()
        }
    }
    // Uma frase de incentivo a cada 5 repetições (ou a cada 15 s de prancha).
    val phraseEvery = if (exercise.unit == ExerciseUnit.SECONDS) 15 else 5
    fun onCounted(units: Int) {
        if (units <= 0) return
        val before = session.amount
        session.amount += units
        session.credit(units)
        if (exercise.unit == ExerciseUnit.REPS) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        if (session.amount / phraseEvery > before / phraseEvery) {
            phrase = phrases[(session.amount / phraseEvery - 1) % phrases.size]
        }
    }

    var rig by remember { mutableStateOf<Rig?>(null) }
    val cueLabel = if (exercise.tracking == Tracking.STEPS) stringResource(R.string.walk_status) else stringResource(cueText(exercise, session.cue))

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (exercise.tracking == Tracking.STEPS) {
            StepCounterEffect { steps ->
                session.tracking = true
                session.amount = steps // passos são creditados de uma vez ao finalizar
            }
            Text(cueLabel, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${session.amount}",
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(unitLabel(exercise)), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            // Câmera grande e quadrada, com o "rig" do corpo (esqueleto) desenhado por cima.
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(Color.Black)
                    .border(3.dp, if (session.tracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape),
            ) {
                LaunchedEffect(Unit) { PoseModels.load(context) }
                val tracker = remember(exercise) { PoseTracker.forExercise(exercise) { PoseModels.classifier } }
                PoseCamera(
                    // Exercícios no chão ou lentos usam o modelo preciso; os rápidos (polichinelo, burpee, barra), o rápido.
                    accurate = exercise in ACCURATE_EXERCISES,
                    onPose = { reading ->
                        val update = tracker.update(reading?.frame, SystemClock.elapsedRealtime())
                        session.tracking = update.cue != Cue.NOT_VISIBLE
                        rig = reading?.rig
                        onCounted(update.counted)
                        session.progress = update.progress
                        session.cue = update.cue
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                RigOverlay(rig, Modifier.fillMaxSize())
                Text(
                    cueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${session.amount}",
                        style = TextStyle(
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            color = Color.White,
                            shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 4f), 16f),
                        ),
                    )
                    Text(
                        stringResource(unitLabel(exercise)),
                        style = MaterialTheme.typography.titleMedium.copy(shadow = Shadow(Color.Black, Offset(0f, 2f), 8f)),
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp, bottom = 20.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { session.progress },
                modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
            )
        }

        Text(phrase, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        val earnedNow = if (exercise.tracking == Tracking.STEPS) exercise.valueOf(session.amount) else session.earned
        Text(stringResource(R.string.session_earned, formatDuration(earnedNow)), style = MaterialTheme.typography.titleMedium)
        if (exercise.tracking != Tracking.STEPS) Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (exercise.tracking == Tracking.STEPS) session.credit(session.amount)
                onFinish(SessionResult(exercise, session.amount, session.earned, session.hitCap))
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) { Text(stringResource(R.string.finish), style = MaterialTheme.typography.titleLarge) }
    }
}

private val ACCURATE_EXERCISES = setOf(
    Exercise.PUSHUP, Exercise.PLANK, Exercise.SITUP, Exercise.SQUAT, Exercise.LUNGE, Exercise.WALL_SIT,
)

/** No abdominal "Desça mais" vira "Suba mais": a mesma meia repetição, na direção certa. */
@StringRes
private fun cueText(exercise: Exercise, cue: Cue): Int =
    if (exercise == Exercise.SITUP && cue == Cue.GO_LOWER) R.string.cue_go_higher else cue.text

@StringRes
private fun unitLabel(exercise: Exercise): Int = when (exercise.unit) {
    ExerciseUnit.REPS -> R.string.unit_reps
    ExerciseUnit.SECONDS -> R.string.unit_seconds
    ExerciseUnit.STEPS -> R.string.unit_steps
}

@Composable
private fun SummaryStage(
    result: SessionResult,
    onAgain: () -> Unit,
    onOther: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    // Conquistas e nível novos desde a última vez que a pessoa viu; depois de mostrar, marca como vistos.
    val (newAchievements, newLevel) = remember {
        val seen = AppGraph.progress.state.value
        val stats = ProgressCenter.stats()
        (Achievement.unlocked(stats) - seen.seenAchievements) to stats.level.takeIf { it > seen.seenLevel }
    }
    LaunchedEffect(Unit) {
        val stats = ProgressCenter.stats()
        AppGraph.progress.markSeen(Achievement.unlocked(stats), stats.level)
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (result.amount == 0) {
            Text(stringResource(R.string.summary_zero), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text(
                "${stringResource(result.exercise.ui.name)}: ${result.amount} ${stringResource(unitLabel(result.exercise))}",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
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
        if (bank.debtSeconds > 0) {
            Text(stringResource(R.string.debt_label, bank.debtPushups), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        newLevel?.let {
            Text(
                stringResource(R.string.summary_level_up, it, stringResource(Levels.rank(it).title)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }
        if (newAchievements.isNotEmpty()) {
            Text(stringResource(R.string.summary_new_achievements), style = MaterialTheme.typography.titleMedium)
            newAchievements.forEach { AchievementCard(it, unlocked = true, modifier = Modifier.fillMaxWidth()) }
        }
        if (result.hitCap) {
            Text(
                stringResource(R.string.summary_cap),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.train_again)) }
        OutlinedButton(onClick = onOther, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.train_other)) }
        OutlinedButton(onClick = onBackHome, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.back_home)) }
    }
}
