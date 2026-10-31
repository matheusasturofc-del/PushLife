package com.pushlife.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pushlife.app.R
import com.pushlife.app.core.progress.Achievement
import com.pushlife.app.core.progress.AchievementGroup
import com.pushlife.app.core.progress.Stats
import com.pushlife.app.ui.theme.Ink
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow
import java.text.NumberFormat

private val AchievementGroup.title: Int
    @StringRes get() = when (this) {
        AchievementGroup.START -> R.string.ach_group_start
        AchievementGroup.STRENGTH -> R.string.ach_group_strength
        AchievementGroup.CONSISTENCY -> R.string.ach_group_consistency
        AchievementGroup.MILESTONES -> R.string.ach_group_milestones
    }

// cartão-pasta no Progresso: quantas tem, barra e as 3 mais perto de sair
@Composable
fun AchievementsFolder(stats: Stats, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val unlocked = remember(stats) { Achievement.unlocked(stats) }
    val next = remember(stats) {
        Achievement.entries.filter { it !in unlocked }.sortedByDescending { it.progress(stats) }.take(3)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Orange, Yellow)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ach_folder_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.ach_count, unlocked.size, Achievement.entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
        LinearProgressIndicator(
            progress = { unlocked.size.toFloat() / Achievement.entries.size },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            strokeCap = StrokeCap.Round,
        )
        if (next.isNotEmpty()) {
            Text(stringResource(R.string.ach_next), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            next.forEach { AchievementRow(it, stats, compact = true) }
        }
    }
}

private enum class AchFilter { ALL, DONE, TODO }

// dentro da pasta: filtro e as conquistas separadas por grupo
@Composable
fun AchievementsScreen(stats: Stats, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    var filter by rememberSaveable { mutableStateOf(AchFilter.ALL) }
    val unlocked = remember(stats) { Achievement.unlocked(stats) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ob_back)) }
                Column {
                    Text(stringResource(R.string.ach_folder_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.ach_count, unlocked.size, Achievement.entries.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AchFilter.ALL to R.string.ach_filter_all, AchFilter.DONE to R.string.ach_filter_done, AchFilter.TODO to R.string.ach_filter_todo)
                    .forEach { (f, label) -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(label)) }) }
            }
        }
        AchievementGroup.entries.forEach { group ->
            val list = Achievement.entries.filter { it.group == group }.filter {
                when (filter) {
                    AchFilter.ALL -> true
                    AchFilter.DONE -> it in unlocked
                    AchFilter.TODO -> it !in unlocked
                }
            }
            if (list.isNotEmpty()) {
                item {
                    val done = Achievement.entries.count { it.group == group && it in unlocked }
                    val total = Achievement.entries.count { it.group == group }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("$done/$total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(list) { AchievementRow(it, stats) }
            }
        }
    }
}

// uma linha: medalha, nome, descrição e barra "37 / 100"
@Composable
private fun AchievementRow(achievement: Achievement, stats: Stats, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val unlocked = achievement.isUnlocked(stats)
    val fmt = remember { NumberFormat.getIntegerInstance() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (unlocked) colors.primaryContainer else if (compact) colors.surface else colors.surfaceContainer)
            .padding(if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(if (compact) 32.dp else 42.dp)
                .background(
                    if (unlocked) Brush.linearGradient(listOf(Orange, Yellow)) else Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceVariant)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (unlocked) Ink else colors.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 16.dp else 22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(achievement.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (!compact) Text(stringResource(achievement.description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (!unlocked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { achievement.progress(stats) },
                        modifier = Modifier.weight(1f).height(6.dp),
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        "${fmt.format(achievement.value(stats).coerceAtMost(achievement.target))} / ${fmt.format(achievement.target)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
