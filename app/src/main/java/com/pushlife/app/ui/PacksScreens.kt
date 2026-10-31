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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.pushlife.app.core.packs.Workouts
import com.pushlife.app.core.pose.PackTrackers
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import com.pushlife.app.core.packs.PackRules
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
    val parts = route.split(':')
    val screen = parts[0]
    val id = parts.getOrNull(1)?.let { runCatching { PackId.valueOf(it) }.getOrNull() }

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
            onDone = { completion -> route = "fim:${id.name}:$completion" },
            modifier = modifier,
        )
        screen == "fim" && id != null -> PackFinish(
            id,
            completion = parts.getOrNull(2)?.toFloatOrNull() ?: 0f,
            onDone = { route = "detalhe:${id.name}" },
            onTest = { route = "teste:${id.name}" },
            onRetry = { route = "treino:${id.name}" },
            modifier = modifier,
        )
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
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Box(Modifier.padding(horizontal = 20.dp)) { header() } }
        items(due) { id -> TodayCard(id, onTrain = { onTrain(id) }, onOpen = { onOpen(id) }, modifier = Modifier.padding(horizontal = 20.dp)) }
        PackCategory.entries.forEach { category ->
            item {
                Text(
                    stringResource(category.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp),
                )
            }
            item {
                // carrossel: cada programa é um cartão alto com o bonequinho
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PackCatalog.byCategory(category)) { pack ->
                        PackTile(pack, all[pack.id] ?: PackProgress(), today, onClick = { onOpen(pack.id) })
                    }
                }
            }
        }
        item {
            TextButton(onClick = onLearn, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.learn_title))
            }
        }
    }
}

// fundo do bonequinho de cada categoria
@Composable
private fun categoryBrush(category: PackCategory): Brush = when (category) {
    PackCategory.STRENGTH -> Brush.linearGradient(listOf(Orange.copy(alpha = 0.22f), Yellow.copy(alpha = 0.18f)))
    PackCategory.CHALLENGE -> Brush.linearGradient(listOf(Yellow.copy(alpha = 0.30f), Orange.copy(alpha = 0.14f)))
    PackCategory.STRETCH -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, Yellow.copy(alpha = 0.14f)))
}

// cartão "treino de hoje" (também usado na tela inicial)
@Composable
internal fun TodayCard(id: PackId, onTrain: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val pack = PackCatalog.get(id)
    val progress = AppGraph.packs.progress(id)
    val steps = remember(progress.level) { Workouts.build(pack, progress.level) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Orange, Yellow)))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoveAnimation(
            steps.first { it.phase == Phase.MAIN }.move,
            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.85f)),
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.packs_today).uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f))
            Text(stringResource(id.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
            Text(stringResource(R.string.pack_minutes, Workouts.minutes(steps)), style = MaterialTheme.typography.bodySmall, color = Ink)
        }
        Button(
            onClick = onTrain,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        ) { Text(stringResource(R.string.pack_go)) }
    }
}

@Composable
private fun PackTile(pack: Pack, progress: PackProgress, today: LocalDate, onClick: () -> Unit) {
    val steps = remember(progress.level) { Workouts.build(pack, progress.level) }
    Column(
        Modifier
            .width(158.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        MoveAnimation(
            steps.first { it.phase == Phase.MAIN }.move,
            Modifier.fillMaxWidth().height(112.dp).background(categoryBrush(pack.category)).padding(6.dp),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(pack.id.nameRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2)
            if (progress.started && !PackPlanner.finishedProgram(pack, progress, today)) {
                val week = progress.week(today)
                Text(stringResource(R.string.pack_week_short, week, pack.weeks), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(
                    progress = { week.toFloat() / pack.weeks },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    strokeCap = StrokeCap.Round,
                )
            } else {
                Text(stringResource(R.string.pack_minutes, Workouts.minutes(steps)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

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
    var showMoves by rememberSaveable { mutableStateOf(false) }
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
                .height(190.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(categoryBrush(pack.category))
                .padding(10.dp),
        )
        Text(stringResource(id.nameRes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(id.descRes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // 3 números do programa
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val week = progress.week(today)
            Pill(if (progress.started && !finished) stringResource(R.string.pack_week_short, week, pack.weeks) else stringResource(R.string.pack_weeks, pack.weeks))
            Pill(stringResource(R.string.pack_minutes, Workouts.minutes(steps)))
            progress.evolutionPercent()?.let { pct ->
                Pill(stringResource(if (pack.category == PackCategory.STRETCH) R.string.pack_evolution_flex else R.string.pack_evolution, if (pct >= 0) "+$pct" else "$pct"), highlight = true)
            }
        }

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

        if (progress.testDue(today) && progress.sessions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.pack_test_due), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onTest) { Text(stringResource(R.string.pack_test_short)) }
            }
        }
        if (progress.assessments.size >= 2) EvolutionChart(progress, Modifier.fillMaxWidth().height(80.dp))

        // dias da semana: bolinhas com a letra do dia
        Text(stringResource(R.string.pack_days_title), style = MaterialTheme.typography.titleMedium)
        val days = progress.days.ifEmpty { PackPlanner.defaultDays(pack.daysPerWeek) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                val on = day in days
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable {
                            val next = if (on) days - day else days + day
                            if (next.isNotEmpty()) AppGraph.packs.setDays(id, next)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val sorted = days.map { it.value }.sorted()
        if (pack.category != PackCategory.STRETCH && sorted.zipWithNext().any { (a, b) -> b - a == 1 }) {
            Text(stringResource(R.string.pack_days_rest_warning, pack.daysPerWeek), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        // lista dos exercícios: fechada por padrão pra tela ficar limpa
        TextButton(onClick = { showMoves = !showMoves }) {
            Text(stringResource(if (showMoves) R.string.pack_hide_moves else R.string.pack_show_moves, steps.distinctBy { it.move }.size))
        }
        if (showMoves) {
            steps.groupBy { it.move to it.amount }.forEach { (key, list) ->
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { openSpotify(context, spotifyQuery) }) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pack_spotify))
            }
            Spacer(Modifier.weight(1f))
            if (progress.started) {
                TextButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.pack_stop), color = MaterialTheme.colorScheme.error) }
            }
        }
        Text(stringResource(R.string.pack_safety), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun Pill(text: String, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
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
// cada exercício tem um tempo mínimo (o ritmo): o app conta as repetições junto com você.
// pular é possível, mas o que foi pulado não conta e abaixo de 60% do treino não tem bônus.

private enum class PlayStage { GET_READY, WORK, REST }

@Composable
private fun PackWorkout(id: PackId, onExit: () -> Unit, onDone: (Float) -> Unit, modifier: Modifier = Modifier) {
    val pack = PackCatalog.get(id)
    val steps = remember(id) { Workouts.build(pack, AppGraph.packs.progress(id).level) }
    val totalWork = remember(steps) { steps.sumOf { it.move.secondsFor(it.amount) }.coerceAtLeast(1) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var stage by rememberSaveable { mutableStateOf(PlayStage.GET_READY) }
    // segundos de exercício feitos de verdade (pra saber quanto do treino foi feito)
    var doneSeconds by rememberSaveable { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // câmera: escolha guardada (padrão ligada) + permissão; só liga nos movimentos que ela confere
    var cameraWanted by remember { mutableStateOf(AppGraph.packs.useCamera) }
    var cameraAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        cameraAllowed = ok
        if (ok) {
            cameraWanted = true
            AppGraph.packs.useCamera = true
        }
    }
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
    val next = steps.getOrNull(index + 1)
    // quanto dura a etapa atual (ms)
    val stageMs = when (stage) {
        PlayStage.GET_READY -> 5_000L
        PlayStage.WORK -> step.move.secondsFor(step.amount) * 1000L
        PlayStage.REST -> (if (step.restAfter > 0) step.restAfter else 5) * 1000L
    }
    var elapsed by remember(index, stage) { mutableLongStateOf(0L) }
    var extraRest by remember(index, stage) { mutableLongStateOf(0L) }
    var cameraCount by remember(index) { mutableIntStateOf(0) }
    val canCamera = remember(step.move) { PackTrackers.forMove(step.move) != null }
    val camera = stage == PlayStage.WORK && canCamera && cameraWanted && cameraAllowed
    // segurar: segundos (os dois lados somados); repetição: reps (cada lado conta)
    val cameraTarget = when {
        step.move.kind == MoveKind.HOLD -> step.move.secondsFor(step.amount)
        step.move.perSide -> step.amount * 2
        else -> step.amount
    }

    fun goNext(completedWork: Boolean) {
        if (stage == PlayStage.WORK && completedWork) doneSeconds += step.move.secondsFor(step.amount)
        when (stage) {
            PlayStage.GET_READY -> stage = PlayStage.WORK
            PlayStage.WORK -> if (index >= steps.lastIndex) onDone(doneSeconds.toFloat() / totalWork) else stage = PlayStage.REST
            PlayStage.REST -> {
                index++
                stage = PlayStage.WORK
            }
        }
    }

    // relógio: anda de 100 em 100 ms; com a câmera, quem termina a etapa é a contagem
    LaunchedEffect(index, stage, paused, camera) {
        var last = SystemClock.elapsedRealtime()
        while (!paused && !(camera && stage == PlayStage.WORK)) {
            delay(100)
            val now = SystemClock.elapsedRealtime()
            val before = elapsed
            elapsed += now - last
            last = now
            val limit = stageMs + extraRest
            // bip a cada repetição no ritmo e nos 3 últimos segundos
            if (stage == PlayStage.WORK && step.move.kind == MoveKind.REPS) {
                val paceMs = (step.move.pace * 1000).toLong()
                if (elapsed / paceMs > before / paceMs && elapsed < limit) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            }
            val left = (limit - elapsed) / 1000
            if (left != (limit - before) / 1000 && left in 0..2 && stage != PlayStage.WORK) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            if (stage == PlayStage.WORK && step.move.kind == MoveKind.HOLD && step.move.perSide && before < limit / 2 && elapsed >= limit / 2) {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            }
            if (elapsed >= limit) {
                tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                goNext(completedWork = true)
                break
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.pack_exit)) }
            LinearProgressIndicator(
                progress = { (index + if (stage == PlayStage.REST) 1f else 0f) / steps.size },
                modifier = Modifier.weight(1f).height(8.dp),
                strokeCap = StrokeCap.Round,
            )
            Text("${index + 1}/${steps.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp))
        }

        val shown = if (stage == PlayStage.REST) next ?: step else step
        Text(
            when (stage) {
                PlayStage.GET_READY -> stringResource(R.string.pack_get_ready)
                PlayStage.REST -> if (step.restAfter > 0) stringResource(R.string.pack_rest) else stringResource(R.string.pack_next_up)
                PlayStage.WORK -> stringResource(step.phase.title) + if (step.sets > 1) " · " + stringResource(R.string.pack_set_of, step.set, step.sets) else ""
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            stringResource(shown.move.nameRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (camera && stage == PlayStage.WORK) {
                CameraCounter(
                    step.move,
                    onCount = {
                        cameraCount += it
                        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                        if (cameraCount >= cameraTarget) goNext(completedWork = true)
                    },
                    count = cameraCount,
                    target = cameraTarget,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MoveAnimation(
                    shown.move,
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Orange.copy(alpha = 0.16f), Yellow.copy(alpha = 0.14f))))
                        .padding(10.dp),
                )
            }
        }
        if (stage != PlayStage.REST || step.restAfter > 0) {
            Text(
                stringResource(shown.move.cueRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // anel com o número grande no meio
        if (!(camera && stage == PlayStage.WORK)) {
            val limit = stageMs + extraRest
            val (big, small) = when {
                stage != PlayStage.WORK -> "${((limit - elapsed + 999) / 1000).coerceAtLeast(0)}" to stringResource(R.string.unit_seconds)
                step.move.kind == MoveKind.HOLD -> "${((limit - elapsed + 999) / 1000).coerceAtLeast(0)}" to
                    stringResource(if (step.move.perSide && elapsed >= limit / 2) R.string.pack_side_two else R.string.unit_seconds)
                else -> {
                    // repetição atual no ritmo; "cada lado" conta de novo na 2ª metade
                    val rep = (elapsed / (step.move.pace * 1000)).toInt() % step.amount + 1
                    "$rep" to stringResource(R.string.pack_of_reps, step.amount) + if (step.move.perSide) " " + stringResource(R.string.amount_per_side) else ""
                }
            }
            Ring(
                progress = (elapsed.toFloat() / limit).coerceIn(0f, 1f),
                big = big,
                small = small,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(150.dp),
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { goNext(completedWork = false) }) {
                Text(stringResource(if (stage == PlayStage.WORK) R.string.pack_skip else R.string.pack_rest_skip))
            }
            FilledTonalIconButton(onClick = { paused = !paused }, modifier = Modifier.size(64.dp)) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = stringResource(if (paused) R.string.pack_resume else R.string.pack_pause), modifier = Modifier.size(32.dp))
            }
            when {
                stage == PlayStage.REST && step.restAfter > 0 -> TextButton(onClick = { extraRest += 15_000 }) { Text(stringResource(R.string.pack_rest_more)) }
                stage == PlayStage.WORK && canCamera -> IconButton(onClick = {
                    if (camera) {
                        cameraWanted = false
                        AppGraph.packs.useCamera = false
                    } else if (cameraAllowed) {
                        cameraWanted = true
                        AppGraph.packs.useCamera = true
                    } else {
                        askCamera.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = stringResource(R.string.pack_use_camera), tint = if (camera) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Spacer(Modifier.size(48.dp))
            }
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

// anel de progresso com o número no meio
@Composable
private fun Ring(progress: Float, big: String, small: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                brush = Brush.sweepGradient(listOf(Yellow, Orange, Yellow)),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, fontSize = 48.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
            Text(small, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

// câmera contando o movimento: reps ou segundos segurando a pose
// os que também existem no treino livre somam no saldo como lá
@Composable
private fun CameraCounter(move: Move, onCount: (Int) -> Unit, count: Int, target: Int, modifier: Modifier = Modifier) {
    val tracker = remember(move) { PackTrackers.forMove(move) } ?: return
    val exercise = move.camera
    var rig by remember { mutableStateOf<Rig?>(null) }
    var cue by remember { mutableStateOf(com.pushlife.app.core.pose.Cue.GET_READY) }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
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
                    exercise?.let { AppGraph.bank.addExercise(it, update.counted, LocalDate.now()) }
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
private fun PackFinish(id: PackId, completion: Float, onDone: () -> Unit, onTest: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf<Finish?>(null) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    val percent = (completion * 100).toInt().coerceIn(0, 100)
    val counts = completion >= PackRules.MIN_COMPLETION
    BackHandler(enabled = counts && result == null) {}
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Ring(progress = completion.coerceIn(0f, 1f), big = "$percent%", small = stringResource(R.string.pack_done_part), modifier = Modifier.size(150.dp))
        if (!counts) {
            // pulou demais: não conta, sem bônus
            Text(stringResource(R.string.pack_incomplete_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.pack_incomplete_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.pack_retry)) }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.pack_done)) }
        } else {
            FinishFeedback(id, completion, result, feedback, onPick = { fb, r -> feedback = fb; result = r }, onDone = onDone, onTest = onTest)
        }
    }
}

@Composable
private fun FinishFeedback(
    id: PackId,
    completion: Float,
    result: Finish?,
    feedback: Feedback?,
    onPick: (Feedback, Finish) -> Unit,
    onDone: () -> Unit,
    onTest: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pack_finish_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        val done = result
        if (done == null) {
            Text(stringResource(R.string.pack_feedback_question), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(Feedback.HARD, "😮‍💨", R.string.feedback_hard),
                    Triple(Feedback.OK, "💪", R.string.feedback_ok),
                    Triple(Feedback.EASY, "😎", R.string.feedback_easy),
                ).forEach { (fb, emoji, title) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onPick(fb, AppGraph.packs.finish(id, fb, completion)) }
                            .padding(vertical = 18.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(emoji, fontSize = 32.sp)
                        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            Text(
                if (done.workoutBonus > 0) stringResource(R.string.pack_bonus, formatDuration(done.workoutBonus)) else stringResource(R.string.pack_bonus_already),
                style = if (done.workoutBonus > 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                color = if (done.workoutBonus > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
