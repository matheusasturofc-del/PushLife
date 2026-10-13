package com.pushlife.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.core.BankState
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.ExerciseUnit
import com.pushlife.app.core.progress.Achievement
import com.pushlife.app.core.progress.Calories
import com.pushlife.app.core.progress.Levels
import com.pushlife.app.core.progress.Stats
import com.pushlife.app.core.progress.WeeklyChallenge
import com.pushlife.app.data.ProgressCenter
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProgressScreen(modifier: Modifier = Modifier) {
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    val progress by AppGraph.progress.state.collectAsStateWithLifecycle()
    val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val stats = remember(bank, progress) { ProgressCenter.stats(today) }
    val unlocked = remember(stats) { Achievement.unlocked(stats) }
    var range by rememberSaveable { mutableIntStateOf(7) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.tab_progress), style = MaterialTheme.typography.displayMedium) }
        item { LevelCard(stats) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(
                    stringResource(R.string.progress_streak),
                    stringResource(R.string.progress_days, stats.currentStreak),
                    stringResource(R.string.progress_best, stats.bestStreak),
                    Modifier.weight(1f),
                )
                Tile(
                    stringResource(R.string.progress_today),
                    "${bank.effortMinutesOn(today)}/${profile.dailyGoal}",
                    stringResource(R.string.progress_goal_unit),
                    Modifier.weight(1f),
                )
                Tile(
                    stringResource(R.string.progress_kcal),
                    "≈ ${stats.calories}",
                    stringResource(R.string.progress_kcal_total),
                    Modifier.weight(1f),
                )
            }
        }
        item {
            val weekStart = WeeklyChallenge.weekStart(today)
            val challenge = WeeklyChallenge.forWeek(weekStart)
            ChallengeCard(
                challenge = challenge,
                done = challenge.progress(bank, weekStart),
                claimed = weekStart in progress.claimedWeeks,
                onClaim = { ProgressCenter.claimChallenge(today) },
            )
        }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.progress_chart), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(selected = range == 7, onClick = { range = 7 }, label = { Text(stringResource(R.string.progress_7_days)) })
                    FilterChip(selected = range == 30, onClick = { range = 30 }, label = { Text(stringResource(R.string.progress_30_days)) })
                }
                MinutesChart(bank, today, range, profile.dailyGoal)
            }
        }
        item {
            Card {
                Text(stringResource(R.string.progress_totals), style = MaterialTheme.typography.titleMedium)
                Exercise.entries.forEach { ex -> TotalRow(ex, stats.totals[ex] ?: 0) }
            }
        }
        item {
            Text(
                stringResource(R.string.progress_achievements, unlocked.size, Achievement.entries.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Achievement.entries.chunked(2).forEach { pair ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { AchievementCard(it, it in unlocked, Modifier.weight(1f)) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun LevelCard(stats: Stats) {
    val level = stats.level
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Orange, Yellow)), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.progress_level, level).uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink)
        Text(stringResource(Levels.rank(level).title), style = MaterialTheme.typography.displayMedium, color = Ink)
        LinearProgressIndicator(
            progress = { Levels.progressInLevel(stats.xp) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = Ink,
            trackColor = Ink.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round,
        )
        Text(
            stringResource(R.string.progress_xp, stats.xp, Levels.xpToReach(level + 1)),
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun ChallengeCard(challenge: WeeklyChallenge, done: Int, claimed: Boolean, onClaim: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.progress_challenge), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.challenge_reward),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(stringResource(challenge.title, challenge.target), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(
            progress = { (done.toFloat() / challenge.target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            strokeCap = StrokeCap.Round,
        )
        Text(
            when {
                claimed -> stringResource(R.string.challenge_claimed)
                else -> stringResource(R.string.challenge_progress, done.coerceAtMost(challenge.target), challenge.target)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!claimed && done >= challenge.target) {
            Button(onClick = onClaim, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.challenge_claim))
            }
        }
    }
}

/** Barras de minutos ganhos por dia, com a linha tracejada da meta. */
@Composable
private fun MinutesChart(bank: BankState, today: LocalDate, days: Int, goal: Int) {
    val values = (days - 1 downTo 0).map { bank.effortMinutesOn(today.minusDays(it.toLong())) }
    val max = maxOf(goal, values.maxOrNull() ?: 0, 1)
    val barColor = MaterialTheme.colorScheme.primary
    val metColor = Yellow
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val slot = size.width / days
        val barWidth = slot * 0.6f
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barWidth) / 2
            val h = (v.toFloat() / max) * size.height
            drawRoundRect(trackColor, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 3))
            if (h > 0f) {
                drawRoundRect(
                    if (v >= goal) metColor else barColor,
                    Offset(x, size.height - h),
                    Size(barWidth, h),
                    CornerRadius(barWidth / 3),
                )
            }
        }
        val goalY = size.height - (goal.toFloat() / max) * size.height
        drawLine(goalColor, Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
    // Rótulos: dia da semana (7 dias) ou dia do mês a cada 5 (30 dias).
    Row(Modifier.fillMaxWidth()) {
        (days - 1 downTo 0).forEach { back ->
            val day = today.minusDays(back.toLong())
            val label = when {
                days == 7 -> day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                back % 5 == 0 -> day.dayOfMonth.toString()
                else -> ""
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text(stringResource(R.string.progress_chart_legend, goal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TotalRow(exercise: Exercise, total: Int) {
    val ui = exercise.ui
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(ui.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(stringResource(ui.name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val unit = stringResource(
            when (exercise.unit) {
                ExerciseUnit.REPS -> R.string.unit_reps
                ExerciseUnit.SECONDS -> R.string.unit_seconds
                ExerciseUnit.STEPS -> R.string.unit_steps
            },
        )
        Text("$total $unit", style = MaterialTheme.typography.titleMedium)
        Text(
            "≈ ${Calories.of(mapOf(exercise to total))} kcal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AchievementCard(achievement: Achievement, unlocked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .background(if (unlocked) colors.primaryContainer else colors.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(if (unlocked) Brush.linearGradient(listOf(Orange, Yellow)) else Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceVariant)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (unlocked) Ink else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(stringResource(achievement.title), style = MaterialTheme.typography.titleMedium, color = if (unlocked) colors.onPrimaryContainer else colors.onSurface)
        Text(stringResource(achievement.description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@get:StringRes
val Levels.Rank.title: Int
    get() = when (this) {
        Levels.Rank.ROOKIE -> R.string.rank_rookie
        Levels.Rank.WARMING_UP -> R.string.rank_warming_up
        Levels.Rank.FIT -> R.string.rank_fit
        Levels.Rank.ATHLETE -> R.string.rank_athlete
        Levels.Rank.BEAST -> R.string.rank_beast
        Levels.Rank.LEGEND -> R.string.rank_legend
    }

@get:StringRes
val WeeklyChallenge.title: Int
    get() = when (this) {
        WeeklyChallenge.PUSHUPS_300 -> R.string.challenge_pushups
        WeeklyChallenge.ACTIVE_5_DAYS -> R.string.challenge_active_days
        WeeklyChallenge.MINUTES_180 -> R.string.challenge_minutes
        WeeklyChallenge.SQUATS_200 -> R.string.challenge_squats
        WeeklyChallenge.PLANK_600 -> R.string.challenge_plank
        WeeklyChallenge.STEPS_50K -> R.string.challenge_steps
    }

@get:StringRes
val Achievement.title: Int
    get() = when (this) {
        Achievement.FIRST_WORKOUT -> R.string.ach_first
        Achievement.PUSHUPS_100 -> R.string.ach_pushups_100
        Achievement.PUSHUPS_1000 -> R.string.ach_pushups_1000
        Achievement.DAY_50_PUSHUPS -> R.string.ach_day_50
        Achievement.STREAK_3 -> R.string.ach_streak_3
        Achievement.STREAK_7 -> R.string.ach_streak_7
        Achievement.STREAK_30 -> R.string.ach_streak_30
        Achievement.GOAL_7_DAYS -> R.string.ach_goal_7
        Achievement.ALL_EXERCISES -> R.string.ach_all
        Achievement.PLANK_60 -> R.string.ach_plank_60
        Achievement.STEPS_10K -> R.string.ach_steps_10k
        Achievement.LEVEL_5 -> R.string.ach_level_5
        Achievement.LEVEL_10 -> R.string.ach_level_10
        Achievement.HOURS_10 -> R.string.ach_hours_10
    }

@get:StringRes
val Achievement.description: Int
    get() = when (this) {
        Achievement.FIRST_WORKOUT -> R.string.ach_first_desc
        Achievement.PUSHUPS_100 -> R.string.ach_pushups_100_desc
        Achievement.PUSHUPS_1000 -> R.string.ach_pushups_1000_desc
        Achievement.DAY_50_PUSHUPS -> R.string.ach_day_50_desc
        Achievement.STREAK_3 -> R.string.ach_streak_3_desc
        Achievement.STREAK_7 -> R.string.ach_streak_7_desc
        Achievement.STREAK_30 -> R.string.ach_streak_30_desc
        Achievement.GOAL_7_DAYS -> R.string.ach_goal_7_desc
        Achievement.ALL_EXERCISES -> R.string.ach_all_desc
        Achievement.PLANK_60 -> R.string.ach_plank_60_desc
        Achievement.STEPS_10K -> R.string.ach_steps_10k_desc
        Achievement.LEVEL_5 -> R.string.ach_level_5_desc
        Achievement.LEVEL_10 -> R.string.ach_level_10_desc
        Achievement.HOURS_10 -> R.string.ach_hours_10_desc
    }
