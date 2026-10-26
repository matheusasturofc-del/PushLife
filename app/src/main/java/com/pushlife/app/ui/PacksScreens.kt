package com.pushlife.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.pushlife.app.camera.Rig
import com.pushlife.app.camera.RigOverlay
import com.pushlife.app.core.packs.Feedback
import com.pushlife.app.core.packs.Finish
import com.pushlife.app.core.packs.Move
import com.pushlife.app.core.packs.MoveKind
import com.pushlife.app.core.packs.Pack
import com.pushlife.app.core.packs.PackCatalog
import com.pushlife.app.core.packs.PackCategory
import com.pushlife.app.core.packs.PackId
import com.pushlife.app.core.packs.PackPlanner
import com.pushlife.app.core.packs.PackProgress
import com.pushlife.app.core.packs.Phase
import com.pushlife.app.core.packs.TestType
import com.pushlife.app.core.packs.WorkoutStep
import com.pushlife.app.core.packs.Workouts
import com.pushlife.app.core.pose.PoseTracker
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// telas dos programas: "lista", "detalhe:ARMS", "treino:ARMS", "fim:ARMS", "teste:ARMS", "corpo"
@Composable
fun PacksFlow(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    // rota pra abrir direto (vindo da tela inicial), ex.: "treino:ARMS"
    openRoute: String? = null,
    onRouteOpened: () -> Unit = {},
) {
    var route by rememberSaveable { mutableStateOf(if (AppGraph.packs.learnSeen) "lista" else "corpo") }
    LaunchedEffect(openRoute) {
        openRoute?.let {
            route = it
            onRouteOpened()
        }
    }
    val screen = route.substringBefore(':')
    val id = route.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { runCatching { PackId.valueOf(it) }.getOrNull() }

    // voltar: do treino pergunta antes (o player cuida disso), o resto volta pro detalhe/lista
    BackHandler(enabled = screen in setOf("detalhe", "fim", "teste", "corpo")) {
        route = if (screen == "detalhe" || screen == "corpo" || id == null) "lista" else "detalhe:${id.name}"
    }

    when {
        screen == "corpo" -> LearnScreen(onDone = {
            AppGraph.packs.learnSeen = true
            route = "lista"
        }, modifier = modifier)
        screen == "detalhe" && id != null -> PackDetail(
            id,
            onBack = { route = "lista" },
            onTrain = { route = "treino:${id.name}" },
            onTest = { route = "teste:${id.name}" },
            modifier = modifier,
        )
        screen == "treino" && id != null -> PackWorkout(
            id,
            onExit = { route = "detalhe:${id.name}" },
            onDone = { route = "fim:${id.name}" },
            modifier = modifier,
        )
        screen == "fim" && id != null -> PackFinish(id, onDone = { route = "detalhe:${id.name}" }, onTest = { route = "teste:${id.name}" }, modifier = modifier)
        screen == "teste" && id != null -> PackTest(id, onDone = { route = "detalhe:${id.name}" }, modifier = modifier)
        else -> PacksList(
            header = header,
            onOpen = { route = "detalhe:${it.name}" },
            onTrain = { route = "treino:${it.name}" },
            onLearn = { route = "corpo" },
            modifier = modifier,
        )
    }
}

private val PackCategory.title: Int
    @StringRes get() = when (this) {
        PackCategory.STRENGTH -> R.string.packs_strength
        PackCategory.CHALLENGE -> R.string.packs_challenges
        PackCategory.STRETCH -> R.string.packs_stretch
    }

internal val PackCategory.icon: ImageVector
    get() = when (this) {
        PackCategory.STRENGTH -> Icons.Rounded.FitnessCenter
        PackCategory.CHALLENGE -> Icons.Rounded.EmojiEvents
        PackCategory.STRETCH -> Icons.Rounded.SelfImprovement
    }

@Composable
internal fun packMeta(pack: Pack, level: Float): String {
    val minutes = Workouts.minutes(Workouts.build(pack, level))
    return if (pack.daysPerWeek >= 7) {
        stringResource(R.string.pack_meta_daily, pack.weeks, minutes)
    } else {
        stringResource(R.string.pack_meta, pack.weeks, pack.daysPerWeek, minutes)
    }
}

@Composable
private fun PacksList(
    header: @Composable () -> Unit,
    onOpen: (PackId) -> Unit,
    onTrain: (PackId) -> Unit,
    onLearn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all by AppGraph.packs.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val due = remember(all, today) { PackPlanner.dueToday(all, today) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { header() }
        item {
            // explicação curta + atalho pra "Como o corpo muda"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onLearn)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.learn_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.packs_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (due.isNotEmpty()) {
            item { Text(stringResource(R.string.packs_today), style = MaterialTheme.typography.titleLarge) }
            items(due) { id -> TodayCard(id, onTrain = { onTrain(id) }, onOpen = { onOpen(id) }) }
        }
        PackCategory.entries.forEach { category ->
            item { Text(stringResource(category.title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            items(PackCatalog.byCategory(category)) { pack -> PackCard(pack, all[pack.id] ?: PackProgress(), today, onClick = { onOpen(pack.id) }) }
        }
    }
}

// cartão "treino de hoje" (também usado na tela inicial)
@Composable
internal fun TodayCard(id: PackId, onTrain: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val pack = PackCatalog.get(id)
    val progress = AppGraph.packs.progress(id)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(pack.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(id.nameRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
            Text(
                stringResource(R.string.pack_week_of, progress.week(LocalDate.now()), pack.weeks) + " · " + packMeta(pack, progress.level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        FilledTonalButton(onClick = onTrain) { Text(stringResource(R.string.pack_train_now)) }
    }
}

@Composable
private fun PackCard(pack: Pack, progress: PackProgress, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // primeiro movimento do treino principal, animado
        MoveAnimation(
            Workouts.build(pack, progress.level).first { it.phase == Phase.MAIN }.move,
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(pack.id.nameRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(pack.id.descRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            if (progress.started && !PackPlanner.finishedProgram(pack, progress, today)) {
                val week = progress.week(today)
                Text(stringResource(R.string.pack_week_of, week, pack.weeks), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(
                    progress = { (week - 1 + progress.sessionsInWeek(week).toFloat() / pack.daysPerWeek.coerceAtMost(progress.days.size.coerceAtLeast(1))) / pack.weeks },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                )
            } else {
                Text(packMeta(pack, progress.level), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackDetail(id: PackId, onBack: () -> Unit, onTrain: () -> Unit, onTest: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pack = PackCatalog.get(id)
    val all by AppGraph.packs.state.collectAsStateWithLifecycle()
    val progress = all[id] ?: PackProgress()
    val today = LocalDate.now()
    val steps = remember(progress.level) { Workouts.build(pack, progress.level) }
    val finished = PackPlanner.finishedProgram(pack, progress, today)
    var confirmStop by remember { mutableStateOf(false) }
    val spotifyQuery = stringResource(id.spotifyRes)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ob_back)) }
        MoveAnimation(
            steps.first { it.phase == Phase.MAIN }.move,
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(8.dp),
        )
        Text(stringResource(id.nameRes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(id.descRes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(packMeta(pack, progress.level), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

        if (progress.started) {
            InfoCard {
                Text(
                    if (finished) stringResource(R.string.pack_finished) else stringResource(R.string.pack_week_of, progress.week(today), pack.weeks),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.pack_level, progress.level.toInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                progress.evolutionPercent()?.let { pct ->
                    Text(
                        stringResource(if (pack.category == PackCategory.STRETCH) R.string.pack_evolution_flex else R.string.pack_evolution, if (pct >= 0) "+$pct" else "$pct"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (progress.assessments.size >= 2) EvolutionChart(progress, Modifier.fillMaxWidth().height(90.dp))
                if (progress.testDue(today)) {
                    Text(stringResource(R.string.pack_test_due), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onTest) { Text(stringResource(R.string.pack_do_test)) }
                }
            }
        }

        // agenda: em quais dias treina
        Text(stringResource(R.string.pack_days_title), style = MaterialTheme.typography.titleMedium)
        val days = progress.days.ifEmpty { PackPlanner.defaultDays(pack.daysPerWeek) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in days,
                    onClick = {
                        val next = if (day in days) days - day else days + day
                        if (next.isNotEmpty()) AppGraph.packs.setDays(id, next)
                    },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        val sorted = days.map { it.value }.sorted()
        val backToBack = pack.category != PackCategory.STRETCH && sorted.zipWithNext().any { (a, b) -> b - a == 1 }
        Text(
            stringResource(if (backToBack) R.string.pack_days_rest_warning else R.string.pack_days_hint, pack.daysPerWeek),
            style = MaterialTheme.typography.bodySmall,
            color = if (backToBack) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = {
                if (!progress.started || finished) AppGraph.packs.start(id)
                onTrain()
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) {
            Text(
                stringResource(
                    when {
                        finished -> R.string.pack_restart
                        progress.started -> R.string.pack_train_now
                        else -> R.string.pack_start
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        OutlinedButton(onClick = { openSpotify(context, spotifyQuery) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pack_spotify))
        }

        // o que tem no treino de hoje
        Text(stringResource(R.string.pack_workout_title), style = MaterialTheme.typography.titleMedium)
        Phase.entries.forEach { phase ->
            val inPhase = steps.filter { it.phase == phase }
            if (inPhase.isNotEmpty()) {
                Text(stringResource(phase.title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                // agrupa séries iguais: "Flexão na parede · 3 × 9"
                inPhase.groupBy { it.move to it.amount }.forEach { (key, list) ->
                    val (move, amount) = key
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MoveAnimation(move, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(move.nameRes), style = MaterialTheme.typography.bodyLarge)
                            Text(amountText(move, amount, list.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.pack_safety), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress.started) {
            TextButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.pack_stop), color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.pack_stop)) },
            text = { Text(stringResource(R.string.pack_stop_body)) },
            confirmButton = {
                TextButton(onClick = {
                    AppGraph.packs.stop(id)
                    confirmStop = false
                }) { Text(stringResource(R.string.pack_stop)) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

private val Phase.title: Int
    @StringRes get() = when (this) {
        Phase.WARMUP -> R.string.phase_warmup
        Phase.MAIN -> R.string.phase_main
        Phase.COOLDOWN -> R.string.phase_cooldown
    }

// "3 × 12" / "3 × 20 s" / "12 (cada lado)"
@Composable
private fun amountText(move: Move, amount: Int, sets: Int): String {
    val one = if (move.kind == MoveKind.HOLD) stringResource(R.string.amount_seconds, amount) else stringResource(R.string.amount_reps, amount)
    val withSets = if (sets > 1) "$sets × $one" else one
    return if (move.perSide) withSets + " " + stringResource(R.string.amount_per_side) else withSets
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

// linha dos testes: cada ponto é um teste
@Composable
private fun EvolutionChart(progress: PackProgress, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val scores = progress.assessments.sortedBy { it.date }.map { it.score }
    Canvas(modifier) {
        val max = scores.max().coerceAtLeast(0.1f)
        val min = minOf(scores.min(), max * 0.5f)
        val pad = 8.dp.toPx()
        fun at(i: Int, v: Float) = Offset(
            pad + (size.width - 2 * pad) * (if (scores.size == 1) 0.5f else i / (scores.size - 1f)),
            size.height - pad - (size.height - 2 * pad) * ((v - min) / (max - min).coerceAtLeast(0.1f)),
        )
        drawLine(grid, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 2.dp.toPx())
        for (i in 1 until scores.size) drawLine(color, at(i - 1, scores[i - 1]), at(i, scores[i]), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        scores.forEachIndexed { i, v -> drawCircle(color, 6.dp.toPx(), at(i, v)) }
    }
}

internal fun openSpotify(context: Context, query: String) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query)))
    try {
        context.startActivity(app)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/" + Uri.encode(query)))) }
    }
}

// ---------- treino guiado ----------

@Composable
private fun PackWorkout(id: PackId, onExit: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pack = PackCatalog.get(id)
    val steps = remember(id) { Workouts.build(pack, AppGraph.packs.progress(id).level) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var resting by rememberSaveable { mutableStateOf(false) }
    var restLeft by rememberSaveable { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            tone?.release()
        }
    }
    BackHandler { confirmExit = true }

    val step = steps[index.coerceIn(0, steps.lastIndex)]
    fun advance() {
        resting = false
        if (index >= steps.lastIndex) onDone() else index++
    }
    fun next() {
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        if (step.restAfter > 0) {
            restLeft = step.restAfter
            resting = true
        } else {
            advance()
        }
    }
    // contagem do descanso
    LaunchedEffect(resting, index) {
        while (resting && restLeft > 0) {
            delay(1000)
            restLeft--
            if (restLeft in 1..3) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        }
        if (resting) advance()
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.pack_exit)) }
            LinearProgressIndicator(
                progress = { (index + if (resting) 1f else 0f) / steps.size },
                modifier = Modifier.weight(1f).height(8.dp),
                strokeCap = StrokeCap.Round,
            )
            Text("${index + 1}/${steps.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp))
        }
        if (resting) {
            RestView(restLeft, steps[(index + 1).coerceAtMost(steps.lastIndex)], onMore = { restLeft += 15 }, onSkip = { advance() }, modifier = Modifier.weight(1f))
        } else {
            StepView(step, onDone = { next() }, tone = tone, modifier = Modifier.weight(1f))
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.pack_exit)) },
            text = { Text(stringResource(R.string.pack_exit_body)) },
            confirmButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.pack_exit)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.pack_keep_going)) } },
        )
    }
}

@Composable
private fun StepView(step: WorkoutStep, onDone: () -> Unit, tone: ToneGenerator?, modifier: Modifier = Modifier) {
    val move = step.move
    val context = LocalContext.current
    // segurar: conta os dois lados seguidos quando é "cada lado"
    val holdTotal = if (move.perSide) step.amount * 2 else step.amount
    var holdLeft by remember(step) { mutableIntStateOf(holdTotal) }
    var running by remember(step) { mutableStateOf(false) }
    var camera by remember(step) { mutableStateOf(false) }
    var counted by remember(step) { mutableIntStateOf(0) }
    val canCamera = move.camera != null && move.kind == MoveKind.REPS
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> camera = ok }

    LaunchedEffect(running, step) {
        while (running && holdLeft > 0) {
            delay(1000)
            holdLeft--
            if (move.perSide && holdLeft == step.amount) tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
            if (holdLeft in 1..3) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        }
        if (running && holdLeft == 0) onDone()
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(step.phase.title) + if (step.sets > 1) " · " + stringResource(R.string.pack_set_of, step.set, step.sets) else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(move.nameRes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (camera && move.camera != null) {
            CameraCounter(move, onCount = {
                counted += it
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                if (counted >= step.amount) onDone()
            }, count = counted, target = step.amount)
        } else {
            MoveAnimation(
                move,
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(8.dp),
            )
        }
        Text(stringResource(move.cueRes), style = MaterialTheme.typography.bodyLarge)
        // número grande: repetições ou segundos que faltam
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(
                if (move.kind == MoveKind.HOLD) "$holdLeft" else "${step.amount}",
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(if (move.kind == MoveKind.HOLD) R.string.unit_seconds else R.string.unit_reps) +
                    if (move.perSide) " " + stringResource(R.string.amount_per_side) else "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
        }
        if (move.kind == MoveKind.HOLD && move.perSide && running) {
            Text(
                stringResource(if (holdLeft > step.amount) R.string.pack_side_one else R.string.pack_side_two),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            move.kind == MoveKind.HOLD && !running -> Button(onClick = { running = true }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Icon(Icons.Rounded.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pack_start_timer), style = MaterialTheme.typography.titleLarge)
            }
            move.kind == MoveKind.REPS -> Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(stringResource(R.string.pack_done_set), style = MaterialTheme.typography.titleLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canCamera && !camera) {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
                    else askCamera.launch(Manifest.permission.CAMERA)
                }) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pack_use_camera))
                }
            }
            TextButton(onClick = onDone) { Text(stringResource(R.string.pack_skip)) }
        }
    }
}

// câmera contando as reps do movimento (soma no saldo como no treino livre)
@Composable
private fun CameraCounter(move: Move, onCount: (Int) -> Unit, count: Int, target: Int) {
    val exercise = move.camera ?: return
    val tracker = remember(move) { PoseTracker.forExercise(exercise) }
    var rig by remember { mutableStateOf<Rig?>(null) }
    var cue by remember { mutableStateOf(com.pushlife.app.core.pose.Cue.GET_READY) }
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(Color.Black)
            .border(3.dp, MaterialTheme.colorScheme.primary, shape),
    ) {
        PoseCamera(
            onPose = { reading ->
                val update = tracker.update(reading?.frame, SystemClock.elapsedRealtime())
                rig = reading?.rig
                cue = update.cue
                if (update.counted > 0) {
                    AppGraph.bank.addExercise(exercise, update.counted, LocalDate.now())
                    onCount(update.counted)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        RigOverlay(rig, Modifier.fillMaxSize())
        Text(
            stringResource(cue.text),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Text(
            "$count / $target",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
}

@Composable
private fun RestView(left: Int, next: WorkoutStep, onMore: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.pack_rest), style = MaterialTheme.typography.headlineMedium)
        Text("$left", fontSize = 96.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.pack_rest_why), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.pack_next, stringResource(next.move.nameRes)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        MoveAnimation(next.move, Modifier.size(140.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onMore) { Text(stringResource(R.string.pack_rest_more)) }
            Button(onClick = onSkip) { Text(stringResource(R.string.pack_rest_skip)) }
        }
    }
}

// ---------- fim do treino ----------

@Composable
private fun PackFinish(id: PackId, onDone: () -> Unit, onTest: () -> Unit, modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf<Finish?>(null) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    BackHandler(enabled = result == null) {}
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Text(stringResource(R.string.pack_finish_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        val done = result
        if (done == null) {
            Text(stringResource(R.string.pack_feedback_question), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            listOf(
                Triple(Feedback.EASY, R.string.feedback_easy, R.string.feedback_easy_hint),
                Triple(Feedback.OK, R.string.feedback_ok, R.string.feedback_ok_hint),
                Triple(Feedback.HARD, R.string.feedback_hard, R.string.feedback_hard_hint),
            ).forEach { (fb, title, hint) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable {
                            feedback = fb
                            result = AppGraph.packs.finish(id, fb)
                        }
                        .padding(16.dp),
                ) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            if (done.workoutBonus > 0) {
                Text(
                    stringResource(R.string.pack_bonus, formatDuration(done.workoutBonus)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(stringResource(R.string.pack_bonus_already), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (done.weekBonus > 0) {
                Text(
                    stringResource(R.string.pack_week_bonus, formatDuration(done.weekBonus)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(
                    when (feedback) {
                        Feedback.EASY -> R.string.pack_next_harder
                        Feedback.HARD -> R.string.pack_next_easier
                        else -> R.string.pack_next_bit_harder
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (done.progress.testDue(LocalDate.now())) {
                OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pack_do_test)) }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.pack_done)) }
        }
    }
}

// ---------- teste de evolução ----------

@Composable
private fun PackTest(id: PackId, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pack = PackCatalog.get(id)
    val progress = AppGraph.packs.progress(id)
    val move = pack.test.slot.rungAt(progress.level).move
    var value by rememberSaveable { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var timeLeft by remember { mutableIntStateOf(60) }
    val type = pack.test.type

    // cronômetro: 60 s pro "quantas em 1 minuto", sobe livre pro "quanto tempo segura"
    LaunchedEffect(running) {
        while (running) {
            delay(250)
            val elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            if (type == TestType.MAX_HOLD) value = elapsed
            if (type == TestType.REPS_IN_60) {
                timeLeft = (60 - elapsed).coerceAtLeast(0)
                if (timeLeft == 0) running = false
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ob_back)) }
        Text(stringResource(R.string.test_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.test_why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoveAnimation(
            move,
            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
        )
        Text(
            when (type) {
                TestType.MAX_REPS -> stringResource(R.string.test_max_reps, stringResource(move.nameRes))
                TestType.REPS_IN_60 -> stringResource(R.string.test_reps_60, stringResource(move.nameRes))
                TestType.MAX_HOLD -> stringResource(R.string.test_max_hold, stringResource(move.nameRes))
                TestType.REACH -> stringResource(R.string.test_reach)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        when (type) {
            TestType.REACH -> {
                val options = listOf(R.string.reach_knees, R.string.reach_shins, R.string.reach_ankles, R.string.reach_toes, R.string.reach_floor)
                options.forEachIndexed { i, label ->
                    FilterChip(selected = value == i + 1, onClick = { value = i + 1 }, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
                }
            }
            TestType.MAX_HOLD -> {
                Text("$value s", fontSize = 64.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
                Button(
                    onClick = {
                        if (running) {
                            running = false
                        } else {
                            value = 0
                            startedAt = SystemClock.elapsedRealtime()
                            running = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(if (running) R.string.test_stop else R.string.test_start)) }
            }
            else -> {
                if (type == TestType.REPS_IN_60) {
                    Text(
                        "$timeLeft s",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    if (!running && timeLeft == 60) {
                        OutlinedButton(onClick = {
                            startedAt = SystemClock.elapsedRealtime()
                            running = true
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.test_start_minute)) }
                    }
                }
                Text(stringResource(R.string.test_how_many), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    FilledTonalIconButton(onClick = { value = (value - 1).coerceAtLeast(0) }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Text("$value", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    FilledTonalIconButton(onClick = { value++ }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
        Button(
            onClick = {
                AppGraph.packs.recordTest(id, value)
                onDone()
            },
            enabled = value > 0 && !running,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.test_save)) }
    }
}

// ---------- como o corpo muda ----------

private val LEARN = listOf(
    Triple(Icons.AutoMirrored.Rounded.TrendingUp, R.string.learn_progress_title, R.string.learn_progress_body),
    Triple(Icons.Rounded.FitnessCenter, R.string.learn_frequency_title, R.string.learn_frequency_body),
    Triple(Icons.Rounded.Bedtime, R.string.learn_rest_title, R.string.learn_rest_body),
    Triple(Icons.Rounded.Update, R.string.learn_time_title, R.string.learn_time_body),
    Triple(Icons.Rounded.SelfImprovement, R.string.learn_stretch_title, R.string.learn_stretch_body),
    Triple(Icons.Rounded.Restaurant, R.string.learn_food_title, R.string.learn_food_body),
    Triple(Icons.Rounded.LocalDrink, R.string.learn_water_title, R.string.learn_water_body),
    Triple(Icons.Rounded.Timer, R.string.learn_free_title, R.string.learn_free_body),
    Triple(Icons.Rounded.Shield, R.string.learn_safety_title, R.string.learn_safety_body),
)

@Composable
private fun LearnScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.learn_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.learn_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(LEARN) { (icon, title, body) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(stringResource(R.string.learn_sources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.learn_done)) }
        }
    }
}
