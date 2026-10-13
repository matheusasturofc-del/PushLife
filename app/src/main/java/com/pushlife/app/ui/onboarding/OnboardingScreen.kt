package com.pushlife.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.core.ExerciseFrequency
import com.pushlife.app.core.Goal
import com.pushlife.app.core.Profile
import com.pushlife.app.core.Projection
import com.pushlife.app.reminder.Reminders
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import java.text.NumberFormat
import java.time.LocalTime

private enum class Step { WELCOME, NAME, GOALS, USAGE, TARGET, REALITY, HOW, FREQUENCY, REMINDER, FIRST }

private const val MAX_GOALS = 3

private val GOAL_OPTIONS = listOf(
    Triple(Goal.LESS_PHONE, R.string.goal_less_phone, Icons.Rounded.Smartphone),
    Triple(Goal.NIGHT_SCROLL, R.string.goal_night, Icons.Rounded.Bedtime),
    Triple(Goal.DISCIPLINE, R.string.goal_discipline, Icons.Rounded.LocalFireDepartment),
    Triple(Goal.STUDY, R.string.goal_study, Icons.Rounded.School),
    Triple(Goal.WORK, R.string.goal_work, Icons.Rounded.Work),
    Triple(Goal.FITNESS, R.string.goal_fit, Icons.AutoMirrored.Rounded.DirectionsRun),
)

private val FREQUENCY_OPTIONS = listOf(
    Triple(ExerciseFrequency.NEVER, R.string.freq_never, Icons.Rounded.Weekend),
    Triple(ExerciseFrequency.ONE_TO_THREE, R.string.freq_1_3, Icons.AutoMirrored.Rounded.DirectionsWalk),
    Triple(ExerciseFrequency.THREE_TO_FIVE, R.string.freq_3_5, Icons.AutoMirrored.Rounded.DirectionsRun),
    Triple(ExerciseFrequency.DAILY, R.string.freq_daily, Icons.Rounded.Bolt),
)

/**
 * Boas-vindas da primeira abertura. Ao terminar, salva o perfil e chama [onFinished]
 * com true se a pessoa quer ir direto para o primeiro treino.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: (startWorkout: Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var name by rememberSaveable { mutableStateOf("") }
    var goals by rememberSaveable { mutableStateOf(emptyList<Goal>()) }
    var phoneHours by rememberSaveable { mutableFloatStateOf(5f) }
    var targetHours by rememberSaveable { mutableFloatStateOf(Projection.suggestedTarget(5f)) }
    var frequency by rememberSaveable { mutableStateOf<ExerciseFrequency?>(null) }
    var reminderHour by rememberSaveable { mutableIntStateOf(18) }
    var reminderMinute by rememberSaveable { mutableIntStateOf(0) }
    var reminderOn by rememberSaveable { mutableStateOf(false) }

    fun go(next: Step) { step = next }
    fun back() { if (step.ordinal > 0) step = Step.entries[step.ordinal - 1] }
    fun finish(startWorkout: Boolean) {
        val reminder = if (reminderOn) LocalTime.of(reminderHour, reminderMinute) else null
        if (reminder != null) Reminders.schedule(context, reminder) else Reminders.cancel(context)
        AppGraph.profile.save(
            Profile(
                onboardingDone = true,
                name = name.trim(),
                goals = goals.toSet(),
                phoneHours = phoneHours,
                targetHours = targetHours,
                exerciseFrequency = frequency,
                dailyGoal = (frequency ?: ExerciseFrequency.ONE_TO_THREE).dailyGoal,
                reminder = reminder,
            ),
        )
        onFinished(startWorkout)
    }

    BackHandler(enabled = step != Step.WELCOME) { back() }

    val displayName = name.trim()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(Brush.verticalGradient(0f to Orange.copy(alpha = 0.28f), 0.45f to Color.Transparent)),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding",
        ) { current ->
            when (current) {
                Step.WELCOME -> WelcomeStep(onStart = { go(Step.NAME) })

                Step.NAME -> StepLayout(
                    step = current, onBack = ::back,
                    kicker = stringResource(R.string.ob_name_kicker),
                    title = stringResource(R.string.ob_name_title),
                    buttonEnabled = displayName.isNotEmpty(),
                    onContinue = { go(Step.GOALS) },
                ) {
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        placeholder = { Text(stringResource(R.string.ob_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { if (displayName.isNotEmpty()) go(Step.GOALS) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Step.GOALS -> StepLayout(
                    step = current, onBack = ::back,
                    kicker = stringResource(R.string.ob_goals_kicker, displayName),
                    title = stringResource(R.string.ob_goals_title),
                    hint = stringResource(R.string.ob_choose_up_to_3),
                    buttonEnabled = goals.isNotEmpty(),
                    onContinue = { go(Step.USAGE) },
                ) {
                    GOAL_OPTIONS.forEach { (goal, label, icon) ->
                        val selected = goal in goals
                        OptionCard(icon, stringResource(label), selected) {
                            goals = when {
                                selected -> goals - goal
                                goals.size < MAX_GOALS -> goals + goal
                                else -> goals
                            }
                        }
                    }
                }

                Step.USAGE -> StepLayout(
                    step = current, onBack = ::back,
                    kicker = stringResource(R.string.ob_usage_kicker),
                    title = stringResource(R.string.ob_usage_title),
                    onContinue = {
                        if (targetHours >= phoneHours) targetHours = Projection.suggestedTarget(phoneHours)
                        go(Step.TARGET)
                    },
                ) {
                    HoursSlider(phoneHours, 0.5f..12f, Orange) { phoneHours = it }
                    Text(
                        stringResource(R.string.ob_usage_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Step.TARGET -> StepLayout(
                    step = current, onBack = ::back,
                    kicker = stringResource(R.string.ob_target_kicker, displayName),
                    title = stringResource(R.string.ob_target_title),
                    onContinue = { go(Step.REALITY) },
                ) {
                    HoursSlider(targetHours, 0.5f..phoneHours.coerceAtLeast(1f), Yellow) { targetHours = it }
                }

                Step.REALITY -> StepLayout(step = current, onBack = ::back, onContinue = { go(Step.HOW) }) {
                    RealityStep(phoneHours, targetHours)
                }

                Step.HOW -> StepLayout(
                    step = current, onBack = ::back,
                    title = stringResource(R.string.ob_how_title),
                    onContinue = { go(Step.FREQUENCY) },
                ) {
                    HowCard(Icons.Rounded.FitnessCenter, Orange, R.string.ob_how_push_title, R.string.ob_how_push_body, "+1 min")
                    HowCard(Icons.Rounded.HourglassBottom, Yellow, R.string.ob_how_scroll_title, R.string.ob_how_scroll_body, "−1 min")
                    HowCard(Icons.Rounded.LockClock, MaterialTheme.colorScheme.primary, R.string.ob_how_free_title, R.string.ob_how_free_body, null)
                }

                Step.FREQUENCY -> StepLayout(
                    step = current, onBack = ::back,
                    kicker = stringResource(R.string.ob_freq_kicker),
                    title = stringResource(R.string.ob_freq_title),
                    buttonEnabled = frequency != null,
                    onContinue = { go(Step.REMINDER) },
                ) {
                    FREQUENCY_OPTIONS.forEach { (option, label, icon) ->
                        OptionCard(icon, stringResource(label), frequency == option) { frequency = option }
                    }
                    frequency?.let {
                        Text(
                            stringResource(R.string.ob_freq_goal, it.dailyGoal),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Step.REMINDER -> {
                    val pickerState = rememberTimePickerState(reminderHour, reminderMinute, DateFormat.is24HourFormat(context))
                    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        go(Step.FIRST)
                    }
                    StepLayout(
                        step = current, onBack = ::back,
                        kicker = stringResource(R.string.ob_reminder_kicker),
                        title = stringResource(R.string.ob_reminder_title),
                        buttonText = stringResource(R.string.ob_reminder_set),
                        onContinue = {
                            reminderHour = pickerState.hour
                            reminderMinute = pickerState.minute
                            reminderOn = true
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (needsPermission) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else go(Step.FIRST)
                        },
                        secondaryText = stringResource(R.string.ob_skip),
                        onSecondary = {
                            reminderOn = false
                            go(Step.FIRST)
                        },
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = pickerState) }
                    }
                }

                Step.FIRST -> StepLayout(
                    step = current, onBack = ::back,
                    title = stringResource(R.string.ob_first_title, displayName),
                    kicker = null,
                    hint = stringResource(R.string.ob_first_body),
                    buttonText = stringResource(R.string.tab_train),
                    onContinue = { finish(startWorkout = true) },
                    secondaryText = stringResource(R.string.ob_first_later),
                    onSecondary = { finish(startWorkout = false) },
                ) {
                    Spacer(Modifier.height(16.dp))
                    OptionCard(Icons.Rounded.FitnessCenter, stringResource(R.string.train_title), selected = true) { finish(startWorkout = true) }
                    Text(
                        stringResource(R.string.ob_first_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.train_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(88.dp).background(Brush.linearGradient(listOf(Orange, Yellow)), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = Ink, modifier = Modifier.size(48.dp))
        }
        Text(
            stringResource(R.string.ob_welcome_title).uppercase(),
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp, lineHeight = 46.sp),
        )
        Text(
            stringResource(R.string.ob_welcome_body),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.ob_start), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Moldura comum: voltar + barra de progresso, títulos, conteúdo rolável e botões no rodapé. */
@Composable
private fun StepLayout(
    step: Step,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    kicker: String? = null,
    title: String? = null,
    hint: String? = null,
    buttonEnabled: Boolean = true,
    buttonText: String = stringResource(R.string.ob_continue),
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ob_back))
            }
            LinearProgressIndicator(
                progress = { step.ordinal.toFloat() / (Step.entries.size - 1) },
                modifier = Modifier.weight(1f).height(10.dp).padding(end = 8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            kicker?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            title?.let { Text(it, style = MaterialTheme.typography.headlineLarge) }
            hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onContinue, enabled = buttonEnabled, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(buttonText, style = MaterialTheme.typography.titleLarge)
            }
            secondaryText?.let {
                TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(it) }
            }
        }
    }
}

@Composable
private fun OptionCard(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (selected) colors.primary else colors.outline.copy(alpha = 0.5f), RoundedCornerShape(50))
            .background(if (selected) colors.primaryContainer else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(Brush.linearGradient(listOf(Orange, Yellow)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary)
    }
}

@Composable
private fun HoursSlider(value: Float, range: ClosedFloatingPointRange<Float>, color: Color, onChange: (Float) -> Unit) {
    val steps = ((range.endInclusive - range.start) / 0.5f).toInt() - 1
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.hours_value, formatHours(value)),
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            color = color,
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onChange((Math.round(it * 2) / 2f).coerceIn(range)) },
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatHours(hours: Float): String =
    NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(hours)

@Composable
private fun RealityStep(phoneHours: Float, targetHours: Float) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.ob_reality_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        BigNumber(stringResource(R.string.ob_days, Projection.daysPerYear(phoneHours)), Orange)
        Text(
            stringResource(R.string.ob_reality_body),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.ob_recover_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        BigNumber(stringResource(R.string.ob_days, Projection.daysRecovered(phoneHours, targetHours)), Yellow)
        Text(
            stringResource(R.string.ob_recover_body),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.ob_reality_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BigNumber(text: String, color: Color) {
    Text(text, fontSize = 64.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = color, textAlign = TextAlign.Center)
}

@Composable
private fun HowCard(icon: ImageVector, color: Color, @StringRes title: Int, @StringRes body: Int, badge: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(48.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(title).uppercase(), style = MaterialTheme.typography.headlineSmall)
                badge?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
