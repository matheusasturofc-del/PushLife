package com.pushlife.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import com.pushlife.app.R
import com.pushlife.app.core.filter.Challenge
import com.pushlife.app.ui.theme.Orange
import kotlinx.coroutines.delay
import java.time.LocalDate

// Para desligar o filtro: reescrever 15 linhas sobre o mal da pornografia, em até 3 minutos e sem colar.
// Só 1 tentativa por dia (pela data do celular): conta ao tocar em "Começar".
@Composable
fun FilterChallengeDialog(onDisable: () -> Unit, onKeep: () -> Unit) {
    val context = LocalContext.current
    val lines = stringArrayResource(R.array.filter_challenge_lines).toList()
    var started by rememberSaveable { mutableStateOf(false) }
    // Já tentou hoje? (lido ao abrir; começar a tentativa grava o dia)
    val usedToday = rememberSaveable { !Challenge.canTry(lastTry(context), LocalDate.now()) }
    var typed by rememberSaveable { mutableStateOf("") }
    var secondsLeft by rememberSaveable { mutableIntStateOf(Challenge.SECONDS) }
    var pasted by remember { mutableStateOf(false) }
    val progress = remember(typed, lines) { Challenge.progress(typed, lines) }
    val timeUp = started && secondsLeft == 0 && !progress.done

    LaunchedEffect(started) {
        while (started && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    LaunchedEffect(progress.done) {
        if (progress.done && started && secondsLeft > 0) onDisable()
    }

    fun start() {
        markTried(context)
        typed = ""
        secondsLeft = Challenge.SECONDS
        pasted = false
        started = true
    }

    Dialog(
        onDismissRequest = onKeep,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        BackHandler(onBack = onKeep)
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.challenge_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (started) {
                    val urgent = secondsLeft <= 30
                    Text(
                        "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = if (urgent) MaterialTheme.colorScheme.error else Orange,
                    )
                }
            }
            Text(stringResource(R.string.challenge_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // O texto, com as linhas já reescritas apagadinhas.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lines.forEachIndexed { i, line ->
                    val done = i < progress.linesDone
                    Text(
                        "${i + 1}. $line",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (i == progress.linesDone && started) FontWeight.Bold else FontWeight.Normal,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            when {
                timeUp -> {
                    Text(stringResource(R.string.challenge_time_up), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onKeep, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.challenge_keep)) }
                }
                started -> {
                    LinearProgressIndicator(progress = { progress.correct.toFloat() / progress.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.challenge_lines, progress.linesDone, lines.size),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { new ->
                            if (Challenge.looksPasted(typed, new)) {
                                pasted = true
                            } else {
                                typed = new
                                pasted = false
                            }
                        },
                        placeholder = { Text(stringResource(R.string.challenge_hint)) },
                        isError = !progress.onTrack || pasted,
                        supportingText = {
                            when {
                                pasted -> Text(stringResource(R.string.challenge_no_paste))
                                !progress.onTrack -> Text(stringResource(R.string.challenge_mistake))
                                else -> {}
                            }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.challenge_keep)) }
                }
                usedToday -> {
                    Text(stringResource(R.string.challenge_used_today), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onKeep, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.challenge_keep)) }
                }
                else -> {
                    Button(onClick = ::start, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.challenge_start)) }
                    OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.challenge_keep)) }
                }
            }
        }
    }
}

private fun prefs(context: Context) = context.getSharedPreferences("filter_challenge", Context.MODE_PRIVATE)

private fun lastTry(context: Context): LocalDate? =
    prefs(context).getString("last_try", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun markTried(context: Context) = prefs(context).edit { putString("last_try", LocalDate.now().toString()) }
