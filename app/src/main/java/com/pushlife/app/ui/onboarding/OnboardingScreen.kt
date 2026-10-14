package com.pushlife.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.blocking.AppEntry
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.blocking.InstalledApps
import com.pushlife.app.blocking.Permissions
import com.pushlife.app.core.DistractionApp
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.ExerciseFrequency
import com.pushlife.app.core.Goal
import com.pushlife.app.core.Profile
import com.pushlife.app.core.Projection
import com.pushlife.app.core.Quiz
import com.pushlife.app.core.QuitReason
import com.pushlife.app.core.TriedMethod
import com.pushlife.app.core.Username
import com.pushlife.app.data.Accounts
import com.pushlife.app.data.Backup
import com.pushlife.app.reminder.Reminders
import com.pushlife.app.ui.AccountError
import com.pushlife.app.ui.CODE_LENGTH
import com.pushlife.app.ui.CodeEntry
import com.pushlife.app.ui.EmailPasswordFields
import com.pushlife.app.ui.HandleStatus
import com.pushlife.app.ui.LoginScreen
import com.pushlife.app.ui.UsernameField
import com.pushlife.app.ui.accountErrorText
import com.pushlife.app.ui.emailPasswordValid
import com.pushlife.app.ui.formatDuration
import com.pushlife.app.ui.rememberHandleStatus
import com.pushlife.app.ui.reloadAfterDataChange
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** As telas das boas-vindas, na ordem em que aparecem. */
private enum class Step {
    WELCOME, NAME, ACCOUNT, GOALS, USAGE, TARGET, APPS, REASONS, STATE, RESULT, PROJECTION, GOOD_NEWS, TRIED,
    REPLACE, HOW_PUSH, HOW_SCROLL, REMINDER, FREQUENCY, SETUP, USAGE_ACCESS, OVERLAY, PERMISSIONS, SELECT_APPS, PLAN, FIRST_WORKOUT,
}

private const val MAX_PICKS = 3

/** Uma opção de resposta: o valor, o texto e o ícone. */
private data class Choice<T>(val value: T, @StringRes val label: Int, val icon: ImageVector)

private val GOAL_OPTIONS = listOf(
    Choice(Goal.LESS_PHONE, R.string.goal_less_phone, Icons.Rounded.Smartphone),
    Choice(Goal.NIGHT_SCROLL, R.string.goal_night, Icons.Rounded.Bedtime),
    Choice(Goal.DISCIPLINE, R.string.goal_discipline, Icons.Rounded.LocalFireDepartment),
    Choice(Goal.STUDY, R.string.goal_study, Icons.Rounded.School),
    Choice(Goal.WORK, R.string.goal_work, Icons.Rounded.Work),
    Choice(Goal.FITNESS, R.string.goal_fit, Icons.AutoMirrored.Rounded.DirectionsRun),
)

private val APP_OPTIONS = listOf(
    Choice(DistractionApp.TIKTOK, R.string.app_tiktok, Icons.Rounded.MusicNote),
    Choice(DistractionApp.YOUTUBE, R.string.app_youtube, Icons.Rounded.SmartDisplay),
    Choice(DistractionApp.INSTAGRAM, R.string.app_instagram, Icons.Rounded.PhotoCamera),
    Choice(DistractionApp.GAMES, R.string.app_games, Icons.Rounded.SportsEsports),
    Choice(DistractionApp.X, R.string.app_x, Icons.Rounded.Tag),
    Choice(DistractionApp.REDDIT, R.string.app_reddit, Icons.Rounded.Forum),
    Choice(DistractionApp.DISCORD, R.string.app_discord, Icons.Rounded.Headset),
    Choice(DistractionApp.SHOPPING, R.string.app_shopping, Icons.Rounded.ShoppingBag),
    Choice(DistractionApp.TWITCH, R.string.app_twitch, Icons.Rounded.LiveTv),
    Choice(DistractionApp.STREAMING, R.string.app_streaming, Icons.Rounded.Movie),
    Choice(DistractionApp.SNAPCHAT, R.string.app_snapchat, Icons.AutoMirrored.Rounded.Chat),
    Choice(DistractionApp.KWAI, R.string.app_kwai, Icons.Rounded.VideoLibrary),
)

private val REASON_OPTIONS = listOf(
    Choice(QuitReason.FOMO, R.string.reason_fomo, Icons.Rounded.NotificationsActive),
    Choice(QuitReason.ADDICTIVE_DESIGN, R.string.reason_design, Icons.Rounded.PhoneAndroid),
    Choice(QuitReason.AUTOMATIC, R.string.reason_automatic, Icons.Rounded.Autorenew),
    Choice(QuitReason.BOREDOM, R.string.reason_boredom, Icons.Rounded.HourglassEmpty),
    Choice(QuitReason.PROCRASTINATION, R.string.reason_procrastination, Icons.Rounded.Schedule),
    Choice(QuitReason.STRESS, R.string.reason_stress, Icons.Rounded.SentimentVeryDissatisfied),
    Choice(QuitReason.COMMUNITY, R.string.reason_community, Icons.Rounded.Groups),
    Choice(QuitReason.COMPARISON, R.string.reason_comparison, Icons.Rounded.SwapHoriz),
    Choice(QuitReason.WORK, R.string.reason_work, Icons.Rounded.Work),
    Choice(QuitReason.TOO_EASY, R.string.reason_too_easy, Icons.Rounded.TouchApp),
)

private val TRIED_OPTIONS = listOf(
    Choice(TriedMethod.NOTHING, R.string.tried_nothing, Icons.Rounded.Block),
    Choice(TriedMethod.LIMITERS, R.string.tried_limiters, Icons.Rounded.Timer),
    Choice(TriedMethod.UNINSTALL, R.string.tried_uninstall, Icons.Rounded.DeleteForever),
    Choice(TriedMethod.BROWSER_ONLY, R.string.tried_browser, Icons.Rounded.Language),
    Choice(TriedMethod.DETOX, R.string.tried_detox, Icons.Rounded.Spa),
    Choice(TriedMethod.GRAYSCALE, R.string.tried_grayscale, Icons.Rounded.Contrast),
    Choice(TriedMethod.MINDSET, R.string.tried_mindset, Icons.Rounded.Psychology),
    Choice(TriedMethod.OUT_OF_REACH, R.string.tried_out_of_reach, Icons.Rounded.PanTool),
    Choice(TriedMethod.DUMB_PHONE, R.string.tried_dumb_phone, Icons.Rounded.PhoneAndroid),
    Choice(TriedMethod.ROUTINE, R.string.tried_routine, Icons.Rounded.WbTwilight),
    Choice(TriedMethod.NFC_TAG, R.string.tried_nfc, Icons.Rounded.Nfc),
    Choice(TriedMethod.MINIMAL_LAUNCHER, R.string.tried_launcher, Icons.Rounded.Apps),
)

private val FREQUENCY_OPTIONS = listOf(
    Choice(ExerciseFrequency.NEVER, R.string.freq_never, Icons.Rounded.Weekend),
    Choice(ExerciseFrequency.ONE_TO_THREE, R.string.freq_1_3, Icons.AutoMirrored.Rounded.DirectionsWalk),
    Choice(ExerciseFrequency.THREE_TO_FIVE, R.string.freq_3_5, Icons.AutoMirrored.Rounded.DirectionsRun),
    Choice(ExerciseFrequency.DAILY, R.string.freq_daily, Icons.Rounded.Bolt),
)

/**
 * Boas-vindas da primeira abertura. Ao terminar, salva o perfil, liga o bloqueio (se a pessoa
 * escolheu) e chama [onFinished] com o exercício para testar agora, ou null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: (startExercise: Exercise?) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var usernameEdited by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<Int?>(null) }
    val account by Accounts.current.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var goals by rememberSaveable { mutableStateOf(emptyList<Goal>()) }
    var phoneHours by rememberSaveable { mutableFloatStateOf(5f) }
    var targetHours by rememberSaveable { mutableFloatStateOf(Projection.suggestedTarget(5f)) }
    var apps by rememberSaveable { mutableStateOf(emptyList<DistractionApp>()) }
    var reasons by rememberSaveable { mutableStateOf(emptyList<QuitReason>()) }
    var tried by rememberSaveable { mutableStateOf(emptyList<TriedMethod>()) }
    var frequency by rememberSaveable { mutableStateOf<ExerciseFrequency?>(null) }
    var reminderHour by rememberSaveable { mutableIntStateOf(18) }
    var reminderMinute by rememberSaveable { mutableIntStateOf(0) }
    var reminderOn by rememberSaveable { mutableStateOf(false) }
    var protectNow by rememberSaveable { mutableStateOf(true) }

    // Sem Supabase configurado, ou já com conta (entrou sem backup), a tela de criar conta é pulada.
    fun skipped(s: Step) = s == Step.ACCOUNT && (!Accounts.available || Accounts.current.value != null)
    fun go(next: Step) { step = next }
    fun next() {
        var i = (step.ordinal + 1).coerceAtMost(Step.entries.lastIndex)
        while (skipped(Step.entries[i]) && i < Step.entries.lastIndex) i++
        step = Step.entries[i]
    }
    fun back() {
        var i = step.ordinal - 1
        while (i > 0 && skipped(Step.entries[i])) i--
        if (i >= 0) step = Step.entries[i]
    }
    fun accountAttempt(action: suspend () -> Unit) {
        if (accountLoading) return
        scope.launch {
            accountLoading = true
            accountError = null
            try {
                action()
            } catch (e: Accounts.AccountException) {
                accountError = accountErrorText(e.error)
                if (e.error == Accounts.Error.EMAIL_IN_USE) codeSent = false
                if (e.error == Accounts.Error.USERNAME_TAKEN) {
                    codeSent = false
                    go(Step.NAME)
                }
            } finally {
                accountLoading = false
            }
        }
    }
    fun sendSignUpCode() = accountAttempt {
        Accounts.requestSignUpCode(email, password, username, name.trim())
        code = ""
        codeSent = true
    }
    // Criar conta em dois passos: manda o código para o e-mail e, com o código certo, cria a conta.
    fun createAccount() {
        if (!codeSent) {
            if (emailPasswordValid(email, password)) sendSignUpCode()
        } else if (code.length == CODE_LENGTH) {
            accountAttempt {
                Accounts.verifySignUp(email, code)
                next()
            }
        }
    }
    fun finish(startExercise: Exercise?) {
        val reminder = if (reminderOn) LocalTime.of(reminderHour, reminderMinute) else null
        if (reminder != null) Reminders.schedule(context, reminder) else Reminders.cancel(context)
        AppGraph.profile.save(
            Profile(
                onboardingDone = true,
                name = name.trim(),
                username = if (Accounts.current.value != null) username else "",
                goals = goals.toSet(),
                phoneHours = phoneHours,
                targetHours = targetHours,
                exerciseFrequency = frequency,
                dailyGoal = (frequency ?: ExerciseFrequency.ONE_TO_THREE).dailyGoal,
                reminder = reminder,
                distractions = apps.toSet(),
                quitReasons = reasons.toSet(),
                triedMethods = tried.toSet(),
            ),
        )
        if (protectNow && canProtect(context)) {
            AppGraph.blocking.setEnabled(true)
            BlockerService.syncWithSettings(context)
        }
        Accounts.backup(context, force = true)
        onFinished(startExercise)
    }

    BackHandler(enabled = step != Step.WELCOME) { if (step == Step.ACCOUNT && codeSent) codeSent = false else back() }

    val displayName = name.trim()
    val handleStatus = rememberHandleStatus(if (Accounts.available && account == null) username else "")
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(Brush.verticalGradient(0f to Orange.copy(alpha = 0.28f), 0.45f to Color.Transparent)),
    ) {
        AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { current ->
            when (current) {
                Step.WELCOME -> WelcomeStep(onStart = ::next, onLogin = if (Accounts.available) ({ showLogin = true }) else null)

                Step.NAME -> StepLayout(
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_name_kicker),
                    title = stringResource(R.string.ob_name_title),
                    buttonEnabled = displayName.isNotEmpty() && (!Accounts.available || account != null || handleStatus == HandleStatus.FREE),
                ) {
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(30)
                            if (!usernameEdited) username = Username.suggest(name)
                        },
                        placeholder = { Text(stringResource(R.string.ob_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (Accounts.available && account == null) {
                        // O @ não pode repetir: o campo confere no servidor enquanto a pessoa digita.
                        UsernameField(username, { username = it; usernameEdited = true }, handleStatus)
                    } else if (account != null && username.isNotEmpty()) {
                        Text("@$username", style = MaterialTheme.typography.titleMedium, color = Orange)
                    }
                }

                Step.ACCOUNT -> StepLayout(
                    current, { if (codeSent) codeSent = false else back() }, ::createAccount,
                    kicker = "@$username",
                    title = stringResource(if (codeSent) R.string.code_title else R.string.ob_account_title),
                    hint = stringResource(if (codeSent) R.string.code_body_signup else R.string.ob_account_hint),
                    buttonEnabled = !accountLoading && if (codeSent) code.length == CODE_LENGTH else emailPasswordValid(email, password),
                    buttonText = stringResource(
                        when {
                            accountLoading -> R.string.account_creating
                            codeSent -> R.string.account_create
                            else -> R.string.code_send
                        },
                    ),
                ) {
                    Spacer(Modifier.height(8.dp))
                    if (codeSent) {
                        CodeEntry(email, code, { code = it }, onComplete = ::createAccount, onResend = { accountAttempt { Accounts.resendSignUpCode(email) } }, onChangeEmail = { codeSent = false })
                    } else {
                        EmailPasswordFields(email, { email = it }, password, { password = it }, onDone = ::createAccount)
                    }
                    AccountError(accountError)
                }

                Step.GOALS -> StepLayout(
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_goals_kicker, displayName),
                    title = stringResource(R.string.ob_goals_title),
                    hint = stringResource(R.string.ob_choose_up_to_3),
                    buttonEnabled = goals.isNotEmpty(),
                ) { MultiChoice(GOAL_OPTIONS, goals, MAX_PICKS) { goals = it } }

                Step.USAGE -> StepLayout(
                    current, ::back,
                    onContinue = {
                        if (targetHours >= phoneHours) targetHours = Projection.suggestedTarget(phoneHours)
                        next()
                    },
                    kicker = stringResource(R.string.ob_usage_kicker),
                    title = stringResource(R.string.ob_usage_title),
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
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_target_kicker, displayName),
                    title = stringResource(R.string.ob_target_title),
                ) { HoursSlider(targetHours, 0.5f..phoneHours.coerceAtLeast(1f), Yellow) { targetHours = it } }

                Step.APPS -> StepLayout(
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_apps_kicker),
                    title = stringResource(R.string.ob_apps_title),
                    hint = stringResource(R.string.ob_choose_up_to_3),
                    buttonEnabled = apps.isNotEmpty(),
                ) { MultiChoice(APP_OPTIONS, apps, MAX_PICKS) { apps = it } }

                Step.REASONS -> StepLayout(
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_reasons_kicker),
                    title = stringResource(R.string.ob_reasons_title),
                    hint = stringResource(R.string.ob_choose_up_to_3),
                    buttonEnabled = reasons.isNotEmpty(),
                ) { MultiChoice(REASON_OPTIONS, reasons, MAX_PICKS) { reasons = it } }

                Step.STATE -> StepLayout(current, ::back, ::next) { StateStep(apps) }

                Step.RESULT -> StepLayout(current, ::back, ::next) {
                    ResultStep(
                        yours = Quiz.dependenceScore(phoneHours, apps.size, reasons.size),
                        goal = Quiz.goalScore(targetHours),
                    )
                }

                Step.PROJECTION -> StepLayout(current, ::back, ::next) { ProjectionStep(phoneHours) }

                Step.GOOD_NEWS -> StepLayout(current, ::back, ::next) { GoodNewsStep(phoneHours, targetHours) }

                Step.TRIED -> StepLayout(
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_tried_kicker),
                    title = stringResource(R.string.ob_tried_title),
                    buttonEnabled = tried.isNotEmpty(),
                ) {
                    MultiChoice(TRIED_OPTIONS, tried, limit = TRIED_OPTIONS.size) { picked ->
                        // "Nada ainda" não combina com as outras opções.
                        val newest = picked - tried.toSet()
                        tried = when {
                            TriedMethod.NOTHING in newest -> listOf(TriedMethod.NOTHING)
                            newest.isNotEmpty() -> picked - TriedMethod.NOTHING
                            else -> picked
                        }
                    }
                }

                Step.REPLACE -> StepLayout(current, ::back, ::next) { ReplaceStep() }

                Step.HOW_PUSH -> StepLayout(current, ::back, ::next) {
                    HowStep(Icons.Rounded.FitnessCenter, Orange, "+1 min", R.string.ob_how_push_title, R.string.ob_how_push_body)
                }

                Step.HOW_SCROLL -> StepLayout(current, ::back, ::next) {
                    HowStep(Icons.Rounded.Smartphone, Yellow, "−1 min", R.string.ob_how_scroll_title, R.string.ob_how_scroll_body)
                    HowCard(Icons.Rounded.LockClock, R.string.ob_how_free_title, R.string.ob_how_free_body)
                }

                Step.REMINDER -> {
                    val pickerState = rememberTimePickerState(reminderHour, reminderMinute, DateFormat.is24HourFormat(context))
                    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
                    StepLayout(
                        current, ::back,
                        onContinue = {
                            reminderHour = pickerState.hour
                            reminderMinute = pickerState.minute
                            reminderOn = true
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (needsPermission) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else next()
                        },
                        kicker = stringResource(R.string.ob_reminder_kicker),
                        title = stringResource(R.string.ob_reminder_title),
                        buttonText = stringResource(R.string.ob_reminder_set),
                        secondaryText = stringResource(R.string.ob_skip),
                        onSecondary = {
                            reminderOn = false
                            next()
                        },
                    ) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = pickerState) } }
                }

                Step.FREQUENCY -> StepLayout(
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_freq_kicker),
                    title = stringResource(R.string.ob_freq_title),
                    buttonEnabled = frequency != null,
                ) {
                    FREQUENCY_OPTIONS.forEach { option ->
                        OptionCard(option.icon, stringResource(option.label), frequency == option.value) { frequency = option.value }
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

                Step.SETUP -> StepLayout(current, ::back, ::next, buttonText = stringResource(R.string.ob_setup_button)) {
                    Spacer(Modifier.height(80.dp))
                    Text(
                        stringResource(R.string.ob_setup_title, displayName),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Step.USAGE_ACCESS -> PermissionStep(
                    current, ::back, ::next,
                    title = R.string.ob_usage_access_title,
                    body = R.string.ob_usage_access_body,
                    isGranted = { Permissions.hasUsageAccess(it) },
                    openSettings = { it.startSafely(Permissions.usageAccessIntent()) },
                )

                Step.OVERLAY -> PermissionStep(
                    current, ::back, ::next,
                    title = R.string.ob_overlay_title,
                    body = R.string.ob_overlay_body,
                    isGranted = { Permissions.canDrawOverlays(it) },
                    openSettings = { it.startSafely(Permissions.overlayIntent(it)) },
                )

                Step.PERMISSIONS -> AllPermissionsStep(current, ::back, ::next)

                Step.SELECT_APPS -> SelectAppsStep(current, ::back, ::next)

                Step.PLAN -> StepLayout(
                    current, ::back, ::next,
                    kicker = stringResource(R.string.ob_plan_kicker),
                    title = stringResource(R.string.ob_plan_title, displayName),
                ) {
                    PlanStep(
                        dailyGoal = (frequency ?: ExerciseFrequency.ONE_TO_THREE).dailyGoal,
                        targetHours = targetHours,
                        reminder = if (reminderOn) LocalTime.of(reminderHour, reminderMinute) else null,
                        protectNow = protectNow,
                        onProtectNow = { protectNow = it },
                    )
                }

                Step.FIRST_WORKOUT -> StepLayout(
                    current, ::back,
                    onContinue = { finish(startExercise = null) },
                    title = stringResource(R.string.ob_first_title, displayName),
                    hint = stringResource(R.string.ob_first_body),
                    buttonText = stringResource(R.string.ob_first_later),
                    primaryIsOutlined = true,
                ) {
                    Spacer(Modifier.height(16.dp))
                    ExerciseCard(Icons.Rounded.FitnessCenter, R.string.ex_pushup) { finish(Exercise.PUSHUP) }
                    ExerciseCard(Icons.Rounded.AccessibilityNew, R.string.ex_squat) { finish(Exercise.SQUAT) }
                    Text(stringResource(R.string.ob_first_more), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.train_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (showLogin) {
            LoginScreen(
                onBack = { showLogin = false },
                onLoggedIn = { result ->
                    val backup = result.backup
                    if (backup != null && Backup.import(context, backup)) {
                        // Conta com progresso salvo: traz tudo e vai direto para o app.
                        AppGraph.profile.save(AppGraph.profile.state.value.copy(username = result.username))
                        reloadAfterDataChange(context)
                    } else {
                        // Conta sem backup ainda: continua as boas-vindas de onde faz sentido.
                        if (result.name.isNotBlank()) name = result.name
                        username = result.username
                        usernameEdited = true
                        showLogin = false
                        go(Step.GOALS)
                    }
                },
            )
        }
    }
}

/** O bloqueio só pode ser ligado com as permissões e pelo menos um app escolhido. */
private fun canProtect(context: Context) =
    Permissions.allRequired(context) && AppGraph.blocking.state.value.blockedPackages.isNotEmpty()

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent) }
}

// ---------- Telas ----------

@Composable
private fun WelcomeStep(onStart: () -> Unit, onLogin: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(96.dp))
        Text(stringResource(R.string.ob_welcome_title).uppercase(), style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp, lineHeight = 46.sp))
        Text(
            stringResource(R.string.ob_welcome_body),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.ob_start), style = MaterialTheme.typography.titleLarge)
        }
        if (onLogin != null) {
            TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ob_have_account)) }
        }
    }
}

/** "Como você está" × "Com o PushLife", com a pesquisa que associa uso intenso de redes a irritabilidade. */
@Composable
private fun StateStep(apps: List<DistractionApp>) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.ob_state_now), style = MaterialTheme.typography.headlineLarge, color = Orange)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            apps.ifEmpty { listOf(DistractionApp.TIKTOK, DistractionApp.YOUTUBE, DistractionApp.INSTAGRAM) }.take(3).forEach { app ->
                Icon(APP_OPTIONS.first { it.value == app }.icon, contentDescription = null, tint = Orange, modifier = Modifier.size(44.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoodChip(Icons.Rounded.SentimentDissatisfied, R.string.ob_state_irritable, Orange)
            MoodChip(Icons.Rounded.QueryStats, R.string.ob_state_unfocused, Orange)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
        Text(stringResource(R.string.ob_state_with), style = MaterialTheme.typography.headlineLarge, color = Yellow)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoodChip(Icons.Rounded.SentimentSatisfied, R.string.ob_state_calm, Yellow)
            MoodChip(Icons.Rounded.SelfImprovement, R.string.ob_state_present, Yellow)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ob_research_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.ob_research_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MoodChip(icon: ImageVector, @StringRes label: Int, color: Color) {
    Row(
        Modifier.border(2.dp, color, RoundedCornerShape(50)).background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
    }
}

/** Resultado com barras que crescem: seu índice × o índice da sua meta. */
@Composable
private fun ResultStep(yours: Int, goal: Int) {
    val yourBar = remember { Animatable(0f) }
    val goalBar = remember { Animatable(0f) }
    var showNumbers by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        yourBar.animateTo(yours / 100f, tween(900))
        goalBar.animateTo(goal / 100f, tween(600))
        showNumbers = true
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.ob_result_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(stringResource(R.string.ob_result_body), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Row(Modifier.height(300.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.Bottom) {
            Bar(yourBar.value, if (showNumbers) "$yours%" else "", Brush.verticalGradient(listOf(Orange, Yellow.copy(alpha = 0.6f))), R.string.ob_result_yours)
            Bar(goalBar.value, if (showNumbers) "$goal%" else "", Brush.verticalGradient(listOf(Yellow, Yellow.copy(alpha = 0.5f))), R.string.ob_result_goal)
        }
        if (showNumbers) {
            Text(
                stringResource(R.string.ob_result_diff, (yours - goal).coerceAtLeast(0)),
                style = MaterialTheme.typography.titleLarge,
                color = Orange,
                textAlign = TextAlign.Center,
            )
        }
        Text(stringResource(R.string.ob_result_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Bar(fraction: Float, label: String, brush: Brush, @StringRes caption: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // O número fica em cima da barra: dentro dela, barras baixas (ex.: 10%) cortavam o texto.
        Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Box(
            Modifier
                .width(84.dp)
                .height((200 * fraction.coerceIn(0.04f, 1f)).dp)
                .background(brush, RoundedCornerShape(14.dp)),
        )
        Text(stringResource(caption), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProjectionStep(phoneHours: Float) {
    CenterColumn {
        Text(stringResource(R.string.ob_reality_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        BigNumber(stringResource(R.string.ob_days, Projection.daysPerYear(phoneHours)), Orange)
        Text(stringResource(R.string.ob_projection_year), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ob_projection_life_intro), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        BigNumber(stringResource(R.string.ob_years, Quiz.lifeYears(phoneHours)), Orange, size = 72)
        Text(stringResource(R.string.ob_projection_life), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ob_projection_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GoodNewsStep(phoneHours: Float, targetHours: Float) {
    CenterColumn {
        Text(stringResource(R.string.ob_good_news), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        BigNumber(stringResource(R.string.ob_years_plus, Quiz.yearsRecovered(phoneHours, targetHours)), Yellow, size = 72)
        Text(stringResource(R.string.ob_good_news_body), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ob_good_news_note, formatHours(targetHours)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReplaceStep() {
    CenterColumn {
        GradientIcon(Icons.Rounded.Layers, 72.dp)
        Text(stringResource(R.string.ob_replace_1), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.ob_replace_1b), style = MaterialTheme.typography.headlineLarge, color = Orange, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ob_replace_2), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.ob_replace_2b), style = MaterialTheme.typography.headlineLarge, color = Yellow, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ob_replace_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HowStep(icon: ImageVector, color: Color, badge: String, @StringRes title: Int, @StringRes body: Int) {
    CenterColumn {
        Box(
            Modifier.size(180.dp).border(4.dp, color, CircleShape).background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(96.dp))
        }
        Text(badge, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(stringResource(title).uppercase(), style = MaterialTheme.typography.displayMedium, color = color)
        Text(stringResource(body), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HowCard(icon: ImageVector, @StringRes title: Int, @StringRes body: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIcon(icon, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Explica uma permissão especial, abre a tela do Android e confere ao voltar. */
@Composable
private fun PermissionStep(
    step: Step,
    onBack: () -> Unit,
    onNext: () -> Unit,
    @StringRes title: Int,
    @StringRes body: Int,
    isGranted: (Context) -> Boolean,
    openSettings: (Context) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = isGranted(context)
        onPauseOrDispose { }
    }
    StepLayout(
        step, onBack,
        onContinue = { if (granted) onNext() else openSettings(context) },
        title = stringResource(title),
        hint = stringResource(body),
        buttonText = stringResource(if (granted) R.string.ob_continue else R.string.ob_grant),
        secondaryText = if (granted) null else stringResource(R.string.ob_skip),
        onSecondary = onNext,
    ) {
        Spacer(Modifier.height(40.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ob_allow), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = granted, onCheckedChange = { if (!granted) openSettings(context) })
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ob_granted), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Uma linha da lista de permissões. */
private data class PermissionItem(@StringRes val title: Int, @StringRes val body: Int, val granted: Boolean, val ask: () -> Unit)

/** Todas as permissões de uma vez: bloqueio, avisos, bateria, câmera e passos. */
@Composable
private fun AllPermissionsStep(step: Step, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val askOne = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val items = remember(refresh) {
        buildList {
            add(PermissionItem(R.string.perm_usage_title, R.string.perm_usage_body, Permissions.hasUsageAccess(context)) { context.startSafely(Permissions.usageAccessIntent()) })
            add(PermissionItem(R.string.perm_overlay_title, R.string.perm_overlay_body, Permissions.canDrawOverlays(context)) { context.startSafely(Permissions.overlayIntent(context)) })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionItem(R.string.perm_notif_title, R.string.perm_notif_body, Permissions.hasNotifications(context)) { askOne.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
            add(PermissionItem(R.string.perm_battery_title, R.string.perm_battery_body, Permissions.ignoresBatteryOptimizations(context)) { Permissions.requestBatteryExemption(context) })
            add(PermissionItem(R.string.perm_camera_title, R.string.perm_camera_body, Permissions.hasCamera(context)) { askOne.launch(Manifest.permission.CAMERA) })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(PermissionItem(R.string.perm_activity_title, R.string.perm_activity_body, Permissions.hasActivityRecognition(context)) { askOne.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
            }
        }
    }
    val granted = items.count { it.granted }

    StepLayout(
        step, onBack, onNext,
        title = stringResource(R.string.ob_perms_title),
        hint = stringResource(R.string.ob_perms_hint),
        buttonText = stringResource(if (granted == items.size) R.string.ob_continue else R.string.ob_perms_later),
    ) {
        Text(
            stringResource(R.string.ob_perms_count, granted, items.size),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(item.body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.granted) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.perm_ok), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = item.ask) { Text(stringResource(R.string.perm_allow)) }
                }
            }
        }
    }
}

/** Escolha dos apps que distraem, com a média de uso de cada um (vira a lista do bloqueio). */
@Composable
private fun SelectAppsStep(step: Step, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val settings by AppGraph.blocking.state.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { InstalledApps.load(context) } }
    val count = settings.blockedPackages.size

    StepLayout(
        step, onBack, onNext,
        kicker = stringResource(R.string.ob_select_kicker),
        title = stringResource(R.string.ob_select_title),
        hint = stringResource(R.string.ob_select_hint),
        buttonText = stringResource(R.string.ob_select_button, count),
        buttonEnabled = count > 0,
        secondaryText = if (count == 0) stringResource(R.string.ob_skip) else null,
        onSecondary = onNext,
    ) {
        val list = apps
        if (list == null) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        } else {
            (if (showAll) list else list.take(8)).forEach { app ->
                val blocked = app.packageName in settings.blockedPackages
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                        .clickable { AppGraph.blocking.toggle(app.packageName) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    app.icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp)) }
                        ?: Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (app.dailyAverageMs >= 60_000) stringResource(R.string.app_average, formatDuration(app.dailyAverageMs / 1000)) else stringResource(R.string.app_little_use),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = blocked, onCheckedChange = { AppGraph.blocking.toggle(app.packageName) })
                }
            }
            if (!showAll && list.size > 8) {
                OutlinedButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ob_select_all)) }
            }
        }
    }
}

/** Resumo do plano antes de terminar (no lugar da tela de criar conta). */
@Composable
private fun PlanStep(dailyGoal: Int, targetHours: Float, reminder: LocalTime?, protectNow: Boolean, onProtectNow: (Boolean) -> Unit) {
    val context = LocalContext.current
    val settings by AppGraph.blocking.state.collectAsStateWithLifecycle()
    val canProtect = remember(settings) { canProtect(context) }
    PlanRow(Icons.Rounded.FitnessCenter, stringResource(R.string.ob_plan_goal, dailyGoal))
    PlanRow(Icons.Rounded.HourglassBottom, stringResource(R.string.ob_plan_target, formatHours(targetHours)))
    PlanRow(Icons.Rounded.LockClock, stringResource(R.string.ob_plan_apps, settings.blockedPackages.size))
    PlanRow(
        Icons.Rounded.NotificationsActive,
        reminder?.let { stringResource(R.string.ob_plan_reminder, it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) }
            ?: stringResource(R.string.ob_plan_no_reminder),
    )
    PlanRow(Icons.Rounded.Timer, stringResource(R.string.ob_plan_free))
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ob_plan_protect), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (canProtect) R.string.ob_plan_protect_hint else R.string.ob_plan_protect_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = protectNow && canProtect, onCheckedChange = onProtectNow, enabled = canProtect)
    }
}

@Composable
private fun PlanRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GradientIcon(icon, 40.dp)
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ExerciseCard(icon: ImageVector, @StringRes name: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientIcon(icon, 52.dp)
        Text(stringResource(name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ---------- Peças comuns ----------

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
    primaryIsOutlined: Boolean = false,
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
                strokeCap = StrokeCap.Round,
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
            if (primaryIsOutlined) {
                OutlinedButton(onClick = onContinue, enabled = buttonEnabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(buttonText) }
            } else {
                Button(onClick = onContinue, enabled = buttonEnabled, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text(buttonText, style = MaterialTheme.typography.titleLarge)
                }
            }
            secondaryText?.let { TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(it) } }
        }
    }
}

/** Lista de opções em que se pode marcar até [limit]. */
@Composable
private fun <T> MultiChoice(options: List<Choice<T>>, selected: List<T>, limit: Int, onChange: (List<T>) -> Unit) {
    options.forEach { option ->
        val isSelected = option.value in selected
        OptionCard(option.icon, stringResource(option.label), isSelected) {
            onChange(
                when {
                    isSelected -> selected - option.value
                    selected.size < limit -> selected + option.value
                    else -> selected
                },
            )
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
        GradientIcon(icon, 40.dp)
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary)
    }
}

@Composable
private fun GradientIcon(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(Brush.linearGradient(listOf(Orange, Yellow)), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun CenterColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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

private fun formatHours(hours: Float): String = NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(hours)

@Composable
private fun BigNumber(text: String, color: Color, size: Int = 64) {
    Text(text, fontSize = size.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = color, textAlign = TextAlign.Center)
}
