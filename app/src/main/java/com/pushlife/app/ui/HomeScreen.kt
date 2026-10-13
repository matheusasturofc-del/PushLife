package com.pushlife.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pushlife.app.AppGraph
import com.pushlife.app.core.progress.Levels
import com.pushlife.app.data.ProgressCenter
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.runtime.remember
import com.pushlife.app.R
import com.pushlife.app.core.BankState
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow

// Fases do plano; CURRENT_PHASE é a última concluída.
private const val CURRENT_PHASE = 5
private val PHASES = listOf(
    R.string.phase_1, R.string.phase_2, R.string.phase_3, R.string.phase_4,
    R.string.phase_5, R.string.phase_6, R.string.phase_7,
)

@Composable
fun HomeScreen(onStartTraining: () -> Unit, modifier: Modifier = Modifier) {
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
    val progress by AppGraph.progress.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val stats = remember(bank, progress) { ProgressCenter.stats(today) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Logo()
        Text(
            if (profile.name.isNotBlank()) stringResource(R.string.home_hello, profile.name) else stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.LocalFireDepartment, stringResource(R.string.progress_days, stats.currentStreak))
            Chip(Icons.Rounded.EmojiEvents, stringResource(R.string.progress_level, stats.level) + " · " + stringResource(Levels.rank(stats.level).title))
        }
        BalanceCard(bank, today, profile.dailyGoal)
        Button(onClick = onStartTraining, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Icon(Icons.Rounded.FitnessCenter, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.start_training), style = MaterialTheme.typography.titleLarge)
        }
        Roadmap()
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Logo() {
    Text(
        buildAnnotatedString {
            append("PUSH")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("LIFE") }
        },
        style = MaterialTheme.typography.displayMedium,
    )
}

@Composable
private fun BalanceCard(bank: BankState, today: LocalDate, dailyGoal: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Orange, Yellow)), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.balance_label).uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink)
            Text(formatDuration(bank.available(today)), style = MaterialTheme.typography.displayMedium, color = Ink)
            Text(
                stringResource(R.string.balance_free_today, formatDuration(bank.freeRemaining(today))) + " · " +
                    stringResource(R.string.balance_earned, formatDuration(bank.earnedSeconds)),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Text(stringResource(R.string.home_daily_goal, bank.effortMinutesOn(today), dailyGoal), style = MaterialTheme.typography.titleMedium, color = Ink)
            LinearProgressIndicator(
                progress = { (bank.effortMinutesOn(today).toFloat() / dailyGoal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Ink,
                trackColor = Ink.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
            )
            if (bank.debtSeconds > 0) {
                Text(stringResource(R.string.debt_label, bank.debtPushups), style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
            Text(stringResource(R.string.balance_rules), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Roadmap() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.roadmap_title), style = MaterialTheme.typography.headlineSmall)
            PHASES.forEachIndexed { index, name -> PhaseRow(number = index + 1, name = name) }
        }
    }
}

@Composable
private fun PhaseRow(number: Int, @StringRes name: Int) {
    val done = number <= CURRENT_PHASE
    val next = number == CURRENT_PHASE + 1
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(if (done) colors.primary else colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.phase_done), tint = colors.onPrimary)
            } else {
                Text("$number", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.phase_number, number).uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
        }
        when {
            done -> Tag(stringResource(R.string.phase_done))
            next -> Tag(stringResource(R.string.phase_next))
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
