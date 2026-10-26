package com.pushlife.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.Recommendations
import com.pushlife.app.core.packs.PackCatalog
import com.pushlife.app.core.packs.PackId
import com.pushlife.app.core.packs.PackPlanner
import java.time.LocalDate

// 2 exercícios mais feitos lado a lado; toque abre o treino
@Composable
fun RecommendedExercises(onPick: (Exercise) -> Unit, modifier: Modifier = Modifier, @StringRes title: Int = R.string.recommended_title) {
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    val picks = remember(bank.countsByDay) { Recommendations.top(bank.countsByDay) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            picks.forEach { exercise ->
                val ui = exercise.ui
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onPick(exercise) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(ui.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column {
                        Text(stringResource(ui.name), style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(stringResource(ui.value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// para onde um recomendado leva
enum class HomeTarget { BLOCK, PROFILE, PROGRESS }

// "Recomendado para você": treino de hoje, programas que combinam com o perfil e recursos que faltam ligar
@Composable
fun RecommendedFeatures(onOpenPack: (PackId) -> Unit, onTrainPack: (PackId) -> Unit, onGo: (HomeTarget) -> Unit, modifier: Modifier = Modifier) {
    val packs by AppGraph.packs.state.collectAsStateWithLifecycle()
    val profile by AppGraph.profile.state.collectAsStateWithLifecycle()
    val blocking by AppGraph.blocking.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val due = remember(packs, today) { PackPlanner.dueToday(packs, today) }
    val testDue = remember(packs, today) { packs.entries.firstOrNull { it.value.testDue(today) && it.value.sessions.isNotEmpty() }?.key }
    val suggested = remember(profile.exerciseFrequency, profile.goals, packs) {
        PackPlanner.suggest(profile.exerciseFrequency, profile.goals).filter { packs[it]?.started != true }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        due.forEach { id -> TodayCard(id, onTrain = { onTrainPack(id) }, onOpen = { onOpenPack(id) }) }
        Text(stringResource(R.string.home_recommended), style = MaterialTheme.typography.titleMedium)
        testDue?.let { id ->
            FeatureRow(Icons.Rounded.Insights, stringResource(R.string.rec_test_title), stringResource(R.string.rec_test_body, stringResource(id.nameRes))) { onOpenPack(id) }
        }
        suggested.forEach { id ->
            val pack = PackCatalog.get(id)
            FeatureRow(pack.category.icon, stringResource(id.nameRes), stringResource(R.string.rec_pack_body, packMeta(pack, 1f))) { onOpenPack(id) }
        }
        if (!blocking.enabled || blocking.blockedPackages.isEmpty()) {
            FeatureRow(Icons.Rounded.Block, stringResource(R.string.rec_block_title), stringResource(R.string.rec_block_body)) { onGo(HomeTarget.BLOCK) }
        }
        if (!blocking.adultFilter) {
            FeatureRow(Icons.Rounded.Shield, stringResource(R.string.rec_filter_title), stringResource(R.string.rec_filter_body)) { onGo(HomeTarget.BLOCK) }
        }
        if (profile.reminder == null) {
            FeatureRow(Icons.Rounded.Alarm, stringResource(R.string.rec_reminder_title), stringResource(R.string.rec_reminder_body)) { onGo(HomeTarget.PROFILE) }
        }
        if (profile.guest) {
            FeatureRow(Icons.Rounded.AccountCircle, stringResource(R.string.rec_account_title), stringResource(R.string.rec_account_body)) { onGo(HomeTarget.PROFILE) }
        }
        FeatureRow(Icons.Rounded.Insights, stringResource(R.string.rec_progress_title), stringResource(R.string.rec_progress_body)) { onGo(HomeTarget.PROGRESS) }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
