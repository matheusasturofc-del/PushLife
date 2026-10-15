package com.pushlife.app.ui

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
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.Recommendations

/** Os 2 exercícios mais praticados (ou flexão e abdominal, no começo), lado a lado; tocar abre o treino. */
@Composable
fun RecommendedExercises(onPick: (Exercise) -> Unit, modifier: Modifier = Modifier) {
    val bank by AppGraph.bank.state.collectAsStateWithLifecycle()
    val picks = remember(bank.countsByDay) { Recommendations.top(bank.countsByDay) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recommended_title), style = MaterialTheme.typography.titleMedium)
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
